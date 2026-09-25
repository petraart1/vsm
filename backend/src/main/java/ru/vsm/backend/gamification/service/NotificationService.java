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
import ru.vsm.backend.gamification.repository.NotificationRepository;
import ru.vsm.backend.gamification.web.dto.NotificationDto;

/**
 * Создание и чтение уведомлений игрока. Создание вызывается из
 * {@link GamificationAccrualService} внутри того же обработчика {@code ScenarioCompletedEvent},
 * что и начисление очков — отдельной идемпотентности здесь не требуется: весь метод
 * {@code GamificationAccrualService#processEvent} пропускается целиком при повторной доставке
 * уже обработанного {@code userProgressId}.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

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
