package ru.vsm.backend.gamification.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import ru.vsm.backend.gamification.challenge.domain.Challenge;
import ru.vsm.backend.gamification.challenge.domain.ChallengeGoalType;
import ru.vsm.backend.gamification.challenge.domain.ChallengeProgress;
import ru.vsm.backend.gamification.challenge.repository.ChallengeProgressRepository;
import ru.vsm.backend.gamification.challenge.repository.ChallengeRepository;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.repository.PlayerAchievementRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.service.NotificationService;
import ru.vsm.backend.gamification.web.dto.NotificationDto;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Прогресс по челленджам месяца обновляется в том же обработчике {@link ScenarioCompletedEvent},
 * что и начисление очков ({@code GamificationAccrualService}) — см. также
 * {@code GamificationAccrualIntegrationTest}/{@code NotificationIntegrationTest} для основного
 * начисления. Здесь — рост прогресса, награда (очки + ачивка + уведомление) при выполнении,
 * идемпотентность повторной доставки события и игнорирование истёкших челленджей.
 */
@SpringBootTest
@Testcontainers
class ChallengeAccrualIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private ChallengeProgressRepository challengeProgressRepository;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private PlayerAchievementRepository playerAchievementRepository;

    @Autowired
    private NotificationService notificationService;

    private Challenge saveChallenge(ChallengeGoalType goalType, String targetBlock, int targetCount,
            Integer safetyThreshold, int rewardPoints, String rewardAchievementCode,
            Instant startsAt, Instant endsAt) {
        return challengeRepository.save(Challenge.builder()
                .code("test-" + UUID.randomUUID())
                .title("Тестовый челлендж")
                .description("Для интеграционного теста")
                .goalType(goalType)
                .targetBlock(targetBlock)
                .targetCount(targetCount)
                .safetyThreshold(safetyThreshold)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .rewardPoints(rewardPoints)
                .rewardAchievementCode(rewardAchievementCode)
                .build());
    }

    private static ScenarioCompletedEvent event(UUID playerId, String block, ScenarioOutcome outcome,
            int safetyScore, boolean allRoleStepsFollowed, Instant completedAt) {
        return new ScenarioCompletedEvent(
                UUID.randomUUID(),
                playerId,
                UUID.randomUUID(),
                "scenario-x",
                block,
                outcome,
                10,
                safetyScore,
                3,
                false,
                allRoleStepsFollowed,
                completedAt.minusSeconds(60),
                completedAt,
                false,
                true);
    }

    @Test
    void progressGrowsAndCompletionAwardsPointsAchievementAndNotification() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        Challenge challenge = saveChallenge(ChallengeGoalType.BLOCK_SCENARIOS_NO_FAILURE, "boarding", 3,
                null, 120, AchievementCode.CHALLENGE_CHAMPION.name(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));

        eventPublisher.publishEvent(event(playerId, "boarding", ScenarioOutcome.SUCCESS, 10, true, now));
        ChallengeProgress afterFirst = challengeProgressRepository
                .findByChallengeIdAndPlayerId(challenge.getId(), playerId).orElseThrow();
        assertThat(afterFirst.getCurrentValue()).isEqualTo(1);
        assertThat(afterFirst.isCompleted()).isFalse();

        // PARTIAL (не FAILURE) тоже засчитывается для BLOCK_SCENARIOS_NO_FAILURE.
        eventPublisher.publishEvent(event(playerId, "boarding", ScenarioOutcome.PARTIAL, 5, true, now));
        assertThat(challengeProgressRepository.findByChallengeIdAndPlayerId(challenge.getId(), playerId)
                .orElseThrow().getCurrentValue()).isEqualTo(2);

        eventPublisher.publishEvent(event(playerId, "boarding", ScenarioOutcome.SUCCESS, 10, true, now));
        ChallengeProgress completed = challengeProgressRepository
                .findByChallengeIdAndPlayerId(challenge.getId(), playerId).orElseThrow();
        assertThat(completed.getCurrentValue()).isEqualTo(3);
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getCompletedAt()).isNotNull();

        // base(SUCCESS)=100+10+10=120, base(PARTIAL)=50+10+5=65, base(SUCCESS)=120 => 305 raw;
        // playerId без AppUser-записи -> неподтверждён -> множитель 0.5 на каждое начисление:
        // round(120*0.5)=60, round(65*0.5)=33, round(120*0.5)=60 => 153 + reward 120 (не урезается) = 273
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(273);
        assertThat(playerAchievementRepository.existsByPlayerIdAndAchievementCode(
                playerId, AchievementCode.CHALLENGE_CHAMPION)).isTrue();

        List<NotificationDto> notifications = notificationService.list(playerId, false);
        assertThat(notifications).extracting(NotificationDto::type)
                .contains(NotificationType.CHALLENGE_COMPLETED.name(), NotificationType.ACHIEVEMENT_UNLOCKED.name());
    }

    @Test
    void replayingSameEventDoesNotDuplicateChallengeProgress() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        Challenge challenge = saveChallenge(ChallengeGoalType.BLOCK_SCENARIOS_NO_FAILURE, "boarding", 5,
                null, 50, null, now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));
        ScenarioCompletedEvent sameEvent = event(playerId, "boarding", ScenarioOutcome.SUCCESS, 10, true, now);

        eventPublisher.publishEvent(sameEvent);
        eventPublisher.publishEvent(sameEvent);

        ChallengeProgress progress = challengeProgressRepository
                .findByChallengeIdAndPlayerId(challenge.getId(), playerId).orElseThrow();
        assertThat(progress.getCurrentValue()).isEqualTo(1);
    }

    @Test
    void expiredChallengeIsNotCounted() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        Challenge challenge = saveChallenge(ChallengeGoalType.BLOCK_SCENARIOS_NO_FAILURE, "boarding", 2,
                null, 50, null, now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));

        eventPublisher.publishEvent(event(playerId, "boarding", ScenarioOutcome.SUCCESS, 10, true, now));

        assertThat(challengeProgressRepository.findByChallengeIdAndPlayerId(challenge.getId(), playerId))
                .isEmpty();
    }

    @Test
    void safetyStreakResetsBelowThresholdAndCompletesOnStreak() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        Challenge challenge = saveChallenge(ChallengeGoalType.SAFETY_STREAK, null, 2, 15, 80, null,
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS));

        eventPublisher.publishEvent(event(playerId, "boarding", ScenarioOutcome.SUCCESS, 10, true, now));
        assertThat(challengeProgressRepository.findByChallengeIdAndPlayerId(challenge.getId(), playerId)
                .orElseThrow().getCurrentValue()).isEqualTo(0);

        eventPublisher.publishEvent(event(playerId, "medical", ScenarioOutcome.SUCCESS, 20, true, now));
        eventPublisher.publishEvent(event(playerId, "safety", ScenarioOutcome.SUCCESS, 25, true, now));

        ChallengeProgress progress = challengeProgressRepository
                .findByChallengeIdAndPlayerId(challenge.getId(), playerId).orElseThrow();
        assertThat(progress.getCurrentValue()).isEqualTo(2);
        assertThat(progress.isCompleted()).isTrue();
    }
}
