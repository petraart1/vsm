package ru.vsm.backend.gamification.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.vsm.backend.gamification.domain.Notification;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.domain.PlayerLevel;
import ru.vsm.backend.gamification.repository.NotificationRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.web.dto.NotificationDto;

/**
 * Создание и чтение уведомлений игрока. Начисление-связанные уведомления (ачивка/личный
 * рекорд/рост в лидерборде/выполнение челленджа/командный рейтинг/экзамен) создаются из
 * {@link GamificationAccrualService} внутри того же обработчика {@code ScenarioCompletedEvent},
 * что и начисление очков, через package-private {@link #create} — отдельной идемпотентности там
 * не требуется: весь метод {@code GamificationAccrualService#processEvent} пропускается целиком
 * при повторной доставке уже обработанного {@code userProgressId}.
 *
 * <p>{@link #notify} и {@link #notifyAllPlayers} — публичные обёртки для уведомлений, не
 * привязанных к обработке {@code ScenarioCompletedEvent} (новый сценарий, новое событие/челлендж,
 * сгорание баллов — см. вызывающих в {@code gamification.event}/{@code gamification.expiry}/
 * {@code gamification.challenge.web.admin}).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final PlayerProfileRepository playerProfileRepository;

    /** Вызывается только из {@code GamificationAccrualService.processEvent} (та же транзакция). */
    void create(UUID playerId, NotificationType type, String title, String body, UUID sourceUserProgressId) {
        notificationRepository.save(Notification.builder()
                .playerId(playerId)
                .type(type)
                .title(title)
                .body(body)
                .sourceUserProgressId(sourceUserProgressId)
                .build());
    }

    /** Точечное уведомление одному игроку вне обработки {@code ScenarioCompletedEvent}. */
    @Transactional
    public void notify(UUID playerId, NotificationType type, String title, String body) {
        create(playerId, type, title, body, null);
    }

    /**
     * Рассылает одно и то же уведомление всем уже известным профилям игрока
     * ({@code gamification_player_profile}) — используется для широковещательных уведомлений
     * (новый сценарий, новое событие/челлендж), а не для персональных начислений. Игрок, у
     * которого ещё нет строки профиля (ни одного прохождения), уведомление не получает — узнать
     * о нём просто негде.
     *
     * @return число разосланных уведомлений (= число известных профилей)
     */
    @Transactional
    public int notifyAllPlayers(NotificationType type, String title, String body) {
        List<Notification> notifications = playerProfileRepository.findAll().stream()
                .map(PlayerProfile::getId)
                .map(playerId -> Notification.builder()
                        .playerId(playerId)
                        .type(type)
                        .title(title)
                        .body(body)
                        .build())
                .toList();
        notificationRepository.saveAll(notifications);
        return notifications.size();
    }

    /**
     * Уведомление {@link NotificationType#LEVEL_UP}, если {@code scoreAfter} пересёк порог нового
     * уровня (см. {@link PlayerLevel}) по сравнению со {@code scoreBefore}. Вызывается из мест
     * начисления очков ({@code GamificationAccrualService}, {@code ExamAccrualService}) сразу после
     * обновления {@code PlayerProfile.totalScore} — само начисление тем же событием пересчитывать
     * не нужно, уровень выводится из {@code totalScore} без отдельного поля.
     */
    void notifyLevelUpIfChanged(UUID playerId, int scoreBefore, int scoreAfter, UUID sourceUserProgressId) {
        PlayerLevel before = PlayerLevel.forScore(scoreBefore);
        PlayerLevel after = PlayerLevel.forScore(scoreAfter);
        if (after == before) {
            return;
        }
        create(playerId, NotificationType.LEVEL_UP,
                "Новый уровень: " + after.title(),
                "Вы достигли уровня «%s» (уровень %d)".formatted(after.title(), after.level()),
                sourceUserProgressId);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(UUID playerId, boolean unreadOnly) {
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findByPlayerIdAndReadAtIsNullOrderByCreatedAtDesc(playerId)
                : notificationRepository.findByPlayerIdOrderByCreatedAtDesc(playerId);
        return notifications.stream().map(this::toDto).toList();
    }

    @Transactional
    public NotificationDto markRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Уведомление не найдено: " + notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return toDto(notification);
    }

    /**
     * Владелец уведомления — нужен контроллеру, чтобы проверить доступ ({@code PlayerAccessGuard})
     * до вызова {@link #markRead}, т.к. эндпоинт {@code POST .../{id}/read} сам по себе не
     * принимает {@code playerId} (id уведомления не раскрывает, чьё оно, без этого запроса).
     */
    @Transactional(readOnly = true)
    public UUID ownerOf(UUID notificationId) {
        return notificationRepository.findById(notificationId)
                .map(Notification::getPlayerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Уведомление не найдено: " + notificationId));
    }

    @Transactional
    public int markAllRead(UUID playerId) {
        List<Notification> unread = notificationRepository.findByPlayerIdAndReadAtIsNull(playerId);
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                n.getCreatedAt(), n.getReadAt());
    }
}
