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
import ru.vsm.backend.gamification.domain.PlayerLevel;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;
import ru.vsm.backend.gamification.web.dto.ProfileResponse;
import ru.vsm.backend.gamification.service.GamificationQueryService;
import ru.vsm.backend.scenario.domain.ExamGrade;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ExamCompletedEvent;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * {@link NotificationType#LEVEL_UP} создаётся ровно один раз в момент, когда {@code
 * PlayerProfile.totalScore} пересекает порог следующего {@link PlayerLevel}, — как за начисление
 * очков сценария ({@code GamificationAccrualService}), так и за бонус экзамена
 * ({@code ExamAccrualService}). Дальнейшие начисления в границах того же уровня новых
 * уведомлений не создают.
 */
@SpringBootTest
@Testcontainers
class LevelUpIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private GamificationQueryService gamificationQueryService;

    /**
     * Нулевые шкалы и {@code allRoleStepsFollowed=false} намеренно — иначе побочные челленджи
     * месяца ({@code ChallengeSeeder}: серия безопасности/полный протокол) добавили бы очки поверх
     * обычного начисления и сбили бы точный подсчёт порога в этом тесте.
     */
    private static ScenarioCompletedEvent completedEvent(UUID playerId, String scenarioCode, String block) {
        return new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), scenarioCode, block,
                ScenarioOutcome.SUCCESS, 0, 0, 4, false, false,
                Instant.now().minusSeconds(90), Instant.now(), false, true);
    }

    /**
     * Неподтверждённый игрок: base(SUCCESS)=100 raw (шкалы нулевые), множитель 0.5 -> 50 очков за
     * прохождение (см. {@code GamificationAntifraudIntegrationTest}). Порог второго уровня
     * ({@link PlayerLevel#JUNIOR_CONDUCTOR}) — 300 очков, пересекается ровно на 6-м прохождении.
     * Блоки — все разные, чтобы не задеть блок-специфичные челленджи месяца (посадка/медицина).
     */
    @Test
    void levelUpNotificationCreatedExactlyOnceWhenThresholdCrossed() {
        UUID playerId = UUID.randomUUID();
        String[] blocks = {"boarding", "baggage", "catering", "misc", "comfort", "seating", "conflict"};
        String[] codes = {"boarding-no-ticket", "baggage-oversized", "catering-dish-unavailable",
                "misc-lost-item", "comfort-temperature", "seating-dispute", "conflict-noise"};

        for (int i = 0; i < 5; i++) {
            eventPublisher.publishEvent(completedEvent(playerId, codes[i], blocks[i]));
        }
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(250);
        assertThat(levelUpCount(playerId)).isZero();

        // 6th completion: 250 + 50 = 300, crosses the threshold (inclusive, forScore(300) = JUNIOR_CONDUCTOR).
        eventPublisher.publishEvent(completedEvent(playerId, codes[5], blocks[5]));
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(300);
        assertThat(levelUpCount(playerId)).isEqualTo(1);

        ProfileResponse profile = gamificationQueryService.getProfile(playerId);
        assertThat(profile.level()).isEqualTo(PlayerLevel.JUNIOR_CONDUCTOR.level());
        assertThat(profile.levelTitle()).isEqualTo(PlayerLevel.JUNIOR_CONDUCTOR.title());
        assertThat(profile.pointsToNextLevel()).isEqualTo(PlayerLevel.CONDUCTOR.minTotalScore() - 300);

        // 7th completion: still within the same level (700 not yet reached) -> no extra notification.
        eventPublisher.publishEvent(completedEvent(playerId, codes[6], blocks[6]));
        assertThat(levelUpCount(playerId)).isEqualTo(1);
    }

    /** Экзаменационный бонус "отлично" (300 очков) поднимает игрока с 0 сразу до уровня 2. */
    @Test
    void examBonusCrossingThresholdCreatesLevelUpNotificationOnce() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        ExamCompletedEvent event = new ExamCompletedEvent(
                UUID.randomUUID(), playerId, 80.0, 90.0, 0.8, ExamGrade.EXCELLENT, List.of(),
                now.minusSeconds(600), now);

        eventPublisher.publishEvent(event);

        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(300);
        assertThat(levelUpCount(playerId)).isEqualTo(1);
    }

    @Test
    void playerBelowFirstThresholdGetsNoLevelUpNotification() {
        UUID playerId = UUID.randomUUID();
        eventPublisher.publishEvent(completedEvent(playerId, "boarding-no-ticket", "boarding"));

        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(50);
        assertThat(levelUpCount(playerId)).isZero();

        ProfileResponse profile = gamificationQueryService.getProfile(playerId);
        assertThat(profile.level()).isEqualTo(PlayerLevel.TRAINEE.level());
        assertThat(profile.levelTitle()).isEqualTo(PlayerLevel.TRAINEE.title());
    }

    private long levelUpCount(UUID playerId) {
        List<NotificationDto> notifications = notificationService.list(playerId, false);
        return notifications.stream()
                .filter(n -> n.type().equals(NotificationType.LEVEL_UP.name()))
                .count();
    }
}
