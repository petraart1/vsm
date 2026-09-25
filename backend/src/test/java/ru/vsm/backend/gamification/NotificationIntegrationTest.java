package ru.vsm.backend.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.repository.NotificationRepository;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Уведомления, создаваемые {@code GamificationAccrualService} в том же обработчике
 * {@link ScenarioCompletedEvent}, что и начисление очков: новая ачивка, личный рекорд по
 * сценарию, рост в лидерборде. Проверяет также идемпотентность (повтор события не дублирует
 * уведомления) и отметку прочитанным через {@link NotificationService}.
 */
@SpringBootTest
@Testcontainers
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    private static ScenarioCompletedEvent completedEvent(UUID playerId, String scenarioCode,
            String block, int loyalty, int safety) {
        return new ScenarioCompletedEvent(
                UUID.randomUUID(),
                playerId,
                UUID.randomUUID(),
                scenarioCode,
                block,
                ScenarioOutcome.SUCCESS,
                loyalty,
                safety,
                4,
                false,
                true,
                Instant.now().minusSeconds(90),
                Instant.now());
    }

    @Test
    void firstCompletionCreatesAchievementNotificationButNoPersonalBestOrRankUp() {
        UUID playerId = UUID.randomUUID();
        ScenarioCompletedEvent event = completedEvent(playerId, "boarding-no-ticket", "boarding", 30, 30);

        eventPublisher.publishEvent(event);

        List<NotificationDto> notifications = notificationService.list(playerId, false);
        assertThat(notifications)
                .extracting(NotificationDto::type)
                .contains(NotificationType.ACHIEVEMENT_UNLOCKED.name())
                .doesNotContain(NotificationType.NEW_PERSONAL_BEST.name(), NotificationType.LEADERBOARD_RANK_UP.name());
        assertThat(notifications).allMatch(NotificationDto::unread);
    }

    @Test
    void replayingSameEventDoesNotDuplicateNotifications() {
        UUID playerId = UUID.randomUUID();
        ScenarioCompletedEvent event = completedEvent(playerId, "boarding-no-ticket", "boarding", 30, 30);

        eventPublisher.publishEvent(event);
        int countAfterFirst = notificationService.list(playerId, false).size();

        eventPublisher.publishEvent(event);
        int countAfterReplay = notificationService.list(playerId, false).size();

        assertThat(countAfterReplay).isEqualTo(countAfterFirst);
    }

    @Test
    void secondBetterCompletionOfSameScenarioCreatesPersonalBestNotification() {
        UUID playerId = UUID.randomUUID();
        eventPublisher.publishEvent(completedEvent(playerId, "medical-passenger-unwell", "medical", 5, 5));
        eventPublisher.publishEvent(completedEvent(playerId, "medical-passenger-unwell", "medical", 20, 20));

        List<NotificationDto> notifications = notificationService.list(playerId, false);
        assertThat(notifications)
                .extracting(NotificationDto::type)
                .contains(NotificationType.NEW_PERSONAL_BEST.name());
    }

    @Test
    void overtakingAnotherPlayerCreatesRankUpNotification() {
        UUID leaderId = UUID.randomUUID();
        UUID challengerId = UUID.randomUUID();

        eventPublisher.publishEvent(completedEvent(leaderId, "baggage-oversized", "baggage", 20, 20));
        // challenger's first completion: no "before" rank to compare against, no rank-up expected.
        eventPublisher.publishEvent(completedEvent(challengerId, "catering-dish-unavailable", "catering", 0, 0));
        assertThat(notificationService.list(challengerId, false))
                .extracting(NotificationDto::type)
                .doesNotContain(NotificationType.LEADERBOARD_RANK_UP.name());

        // challenger overtakes leader with a strong second completion of a different scenario.
        eventPublisher.publishEvent(completedEvent(challengerId, "safety-smoking", "safety", 30, 30));

        assertThat(notificationService.list(challengerId, false))
                .extracting(NotificationDto::type)
                .contains(NotificationType.LEADERBOARD_RANK_UP.name());
    }

    @Test
    void markingNotificationReadIsReflectedInUnreadOnlyFilter() {
        UUID playerId = UUID.randomUUID();
        eventPublisher.publishEvent(completedEvent(playerId, "boarding-no-ticket", "boarding", 30, 30));

        List<NotificationDto> unreadBefore = notificationService.list(playerId, true);
        assertThat(unreadBefore).isNotEmpty();

        NotificationDto marked = notificationService.markRead(unreadBefore.get(0).id());
        assertThat(marked.readAt()).isNotNull();

        List<NotificationDto> unreadAfter = notificationService.list(playerId, true);
        assertThat(unreadAfter).extracting(NotificationDto::id).doesNotContain(marked.id());

        int markedCount = notificationService.markAllRead(playerId);
        assertThat(markedCount).isEqualTo(unreadAfter.size());
        assertThat(notificationService.list(playerId, true)).isEmpty();
        assertThat(notificationRepository.findByPlayerIdOrderByCreatedAtDesc(playerId))
                .allMatch(n -> n.getReadAt() != null);
    }
}
