package ru.vsm.backend.gamification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.CompetencyScore;
import ru.vsm.backend.gamification.domain.PlayerAchievement;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.AccrualLogRepository;
import ru.vsm.backend.gamification.repository.CompetencyScoreRepository;
import ru.vsm.backend.gamification.repository.PlayerAchievementRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Публикует {@link ScenarioCompletedEvent} вручную через {@link ApplicationEventPublisher}
 * (как это делается при завершении прохождения) и проверяет, что
 * {@code ScenarioCompletedEventListener} -&gt; {@code GamificationAccrualService} начисляют
 * очки/ачивку, а повторная доставка того же события (тот же {@code userProgressId}) не
 * начисляет очки повторно (идемпотентность через {@code gamification_accrual_log}).
 *
 * <p>Тест намеренно НЕ {@code @Transactional}: слушатель — {@code @TransactionalEventListener
 * (AFTER_COMMIT, fallbackExecution = true)}; без активной транзакции вокруг
 * {@code publishEvent} он срабатывает сразу (fallback), что и нужно для синхронной проверки
 * в тесте. Обёртка теста в {@code @Transactional} откатывала бы всё в конце и AFTER_COMMIT
 * вообще не сработал бы.
 */
@SpringBootTest
@Testcontainers
class GamificationAccrualIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private CompetencyScoreRepository competencyScoreRepository;

    @Autowired
    private PlayerAchievementRepository playerAchievementRepository;

    @Autowired
    private AccrualLogRepository accrualLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Регрессионный тест на баг в обработке транзакций:
     * {@code GamificationAccrualService.processEvent} с обычным {@code @Transactional} (REQUIRED)
     * присоединялся к уже закоммиченной транзакции (AFTER_COMMIT-синхронизации выполняются
     * до физической отвязки {@code EntityManagerHolder}) — SELECT'ы отрабатывали, лог писал "Начислено
     * N очков", но ни один {@code save()} физически не коммитился. Этот тест воспроизводит именно тот
     * сценарий: событие публикуется ИЗНУТРИ реальной транзакции (как это делает
     * {@code ScenarioPlayService}), а не "голым" {@code publishEvent} без транзакции — второй случай
     * не ловит баг, т.к. срабатывает {@code fallbackExecution} листенера (см. тест выше), а не
     * AFTER_COMMIT-путь. После фикса ({@code propagation = REQUIRES_NEW} на {@code processEvent})
     * строки должны появиться после коммита внешней транзакции.
     */
    @Test
    void publishingEventInsideRealTransactionStillCommitsGamificationRows() {
        UUID playerId = UUID.randomUUID();
        UUID userProgressId = UUID.randomUUID();
        ScenarioCompletedEvent event = new ScenarioCompletedEvent(
                userProgressId,
                playerId,
                UUID.randomUUID(),
                "boarding-no-ticket",
                "boarding",
                ScenarioOutcome.SUCCESS,
                10,
                10,
                3,
                false,
                true,
                Instant.now().minusSeconds(60),
                Instant.now());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        // base(SUCCESS)=100 + loyaltyGain=10 + safetyGain=10 - timeoutPenalty=0 = 120
        Optional<PlayerProfile> profile = playerProfileRepository.findById(playerId);
        assertThat(profile).isPresent();
        assertThat(profile.get().getTotalScore()).isEqualTo(120);
        assertThat(profile.get().getScenariosCompleted()).isEqualTo(1);
        assertThat(accrualLogRepository.existsByUserProgressId(userProgressId)).isTrue();
    }

    @Test
    void publishingScenarioCompletedEventAwardsPointsAndAchievementsOnce() {
        UUID playerId = UUID.randomUUID();
        UUID userProgressId = UUID.randomUUID();
        ScenarioCompletedEvent event = new ScenarioCompletedEvent(
                userProgressId,
                playerId,
                UUID.randomUUID(),
                "boarding-no-ticket",
                "boarding",
                ScenarioOutcome.SUCCESS,
                30,
                30,
                4,
                false,
                true,
                Instant.now().minusSeconds(120),
                Instant.now());

        eventPublisher.publishEvent(event);

        // base(SUCCESS)=100 + loyaltyGain=30 + safetyGain=30 - timeoutPenalty=0 = 160
        Optional<PlayerProfile> profile = playerProfileRepository.findById(playerId);
        assertThat(profile).isPresent();
        assertThat(profile.get().getTotalScore()).isEqualTo(160);
        assertThat(profile.get().getScenariosCompleted()).isEqualTo(1);

        Optional<CompetencyScore> competency =
                competencyScoreRepository.findByPlayerIdAndBlock(playerId, "boarding");
        assertThat(competency).isPresent();
        assertThat(competency.get().getLoyaltyPoints()).isEqualTo(30);
        assertThat(competency.get().getSafetyPoints()).isEqualTo(30);
        assertThat(competency.get().getScenariosCompleted()).isEqualTo(1);

        List<PlayerAchievement> achievements = playerAchievementRepository.findByPlayerId(playerId);
        assertThat(achievements)
                .extracting(PlayerAchievement::getAchievementCode)
                .containsExactlyInAnyOrder(
                        AchievementCode.FIRST_SCENARIO,
                        AchievementCode.FLAWLESS_SAFETY,
                        AchievementCode.PASSENGER_FAVORITE);

        assertThat(accrualLogRepository.existsByUserProgressId(userProgressId)).isTrue();

        // Повторная доставка того же прохождения (например, ретрай) — не должна ничего удвоить.
        eventPublisher.publishEvent(event);

        Optional<PlayerProfile> profileAfterReplay = playerProfileRepository.findById(playerId);
        assertThat(profileAfterReplay).isPresent();
        assertThat(profileAfterReplay.get().getTotalScore()).isEqualTo(160);
        assertThat(profileAfterReplay.get().getScenariosCompleted()).isEqualTo(1);
        assertThat(playerAchievementRepository.findByPlayerId(playerId)).hasSize(3);
    }
}
