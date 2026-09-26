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
                Instant.now(),
                false,
                true);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        // base(SUCCESS)=100 + loyaltyGain=10 + safetyGain=10 - timeoutPenalty=0 = 120 raw;
        // playerId без AppUser-записи -> неподтверждён -> множитель 0.5 (см. GamificationLimitsProperties) -> 60.
        Optional<PlayerProfile> profile = playerProfileRepository.findById(playerId);
        assertThat(profile).isPresent();
        assertThat(profile.get().getTotalScore()).isEqualTo(60);
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
                Instant.now(),
                false,
                true);

        eventPublisher.publishEvent(event);

        // base(SUCCESS)=100 + loyaltyGain=30 + safetyGain=30 - timeoutPenalty=0 = 160 raw;
        // неподтверждённый playerId -> множитель 0.5 -> 80 (см. GamificationAntifraudIntegrationTest
        // для отдельных тестов множителя/лимита/подтверждённого профиля).
        Optional<PlayerProfile> profile = playerProfileRepository.findById(playerId);
        assertThat(profile).isPresent();
        assertThat(profile.get().getTotalScore()).isEqualTo(80);
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
        assertThat(profileAfterReplay.get().getTotalScore()).isEqualTo(80);
        assertThat(profileAfterReplay.get().getScenariosCompleted()).isEqualTo(1);
        assertThat(playerAchievementRepository.findByPlayerId(playerId)).hasSize(3);
    }

    /**
     * Регрессионный тест на находку аудита безопасности: replay уже {@code COMPLETED} сценария
     * (новый {@code userProgressId} на каждый заход, поэтому идемпотентность по
     * {@code userProgressId} его не ловит) не должен приносить очки/scenariosCompleted/ачивки —
     * см. Javadoc {@code GamificationAccrualService#processEvent}, поле {@code awardable}.
     * Компетенции по шкалам растут при каждой реальной попытке — это нужно аналитике компетенций.
     */
    @Test
    void replayOfAlreadyCompletedScenarioAwardsNoPointsOrAchievementsButUpdatesCompetencies() {
        UUID playerId = UUID.randomUUID();

        ScenarioCompletedEvent firstPlay = new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "boarding-no-ticket", "boarding",
                ScenarioOutcome.SUCCESS, 10, 10, 3, false, true,
                Instant.now().minusSeconds(120), Instant.now(), false, true);
        eventPublisher.publishEvent(firstPlay);

        // base(SUCCESS)=100+10+10=120 raw, неподтверждённый playerId -> множитель 0.5 -> 60.
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getTotalScore()).isEqualTo(60);
        assertThat(playerProfileRepository.findById(playerId).orElseThrow().getScenariosCompleted()).isEqualTo(1);
        assertThat(playerAchievementRepository.findByPlayerId(playerId))
                .extracting(PlayerAchievement::getAchievementCode)
                .contains(AchievementCode.FIRST_SCENARIO);

        // Повторное прохождение того же сценария игроком: новый userProgressId, firstCompletion=false.
        ScenarioCompletedEvent replay = new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "boarding-no-ticket", "boarding",
                ScenarioOutcome.SUCCESS, 10, 10, 3, false, true,
                Instant.now().minusSeconds(60), Instant.now(), false, false);
        eventPublisher.publishEvent(replay);

        PlayerProfile profileAfterReplay = playerProfileRepository.findById(playerId).orElseThrow();
        assertThat(profileAfterReplay.getTotalScore()).isEqualTo(60);
        assertThat(profileAfterReplay.getScenariosCompleted()).isEqualTo(1);
        assertThat(playerAchievementRepository.findByPlayerId(playerId)).hasSize(1);

        CompetencyScore competency =
                competencyScoreRepository.findByPlayerIdAndBlock(playerId, "boarding").orElseThrow();
        assertThat(competency.getLoyaltyPoints()).isEqualTo(20);
        assertThat(competency.getSafetyPoints()).isEqualTo(20);
        assertThat(competency.getScenariosCompleted()).isEqualTo(2);

        assertThat(accrualLogRepository.existsByUserProgressId(replay.userProgressId())).isTrue();
    }

    /**
     * Пункт экзамена ({@code examMode=true}) не приносит очков/ачивок — экзамен вознаграждается
     * отдельно, итоговым бонусом за всю попытку ({@code ExamAccrualService} по {@code
     * ExamCompletedEvent}), а не за каждый входящий в него сценарий. Компетенции по шкалам растут
     * как обычно.
     */
    @Test
    void examModeScenarioAwardsNoPointsOrAchievementsButUpdatesCompetencies() {
        UUID playerId = UUID.randomUUID();

        ScenarioCompletedEvent examPiece = new ScenarioCompletedEvent(
                UUID.randomUUID(), playerId, UUID.randomUUID(), "medical-passenger-unwell", "medical",
                ScenarioOutcome.SUCCESS, 15, 20, 2, false, true,
                Instant.now().minusSeconds(90), Instant.now(), true, true);
        eventPublisher.publishEvent(examPiece);

        PlayerProfile profile = playerProfileRepository.findById(playerId).orElseThrow();
        assertThat(profile.getTotalScore()).isZero();
        assertThat(profile.getScenariosCompleted()).isZero();
        assertThat(playerAchievementRepository.findByPlayerId(playerId)).isEmpty();

        CompetencyScore competency =
                competencyScoreRepository.findByPlayerIdAndBlock(playerId, "medical").orElseThrow();
        assertThat(competency.getLoyaltyPoints()).isEqualTo(15);
        assertThat(competency.getSafetyPoints()).isEqualTo(20);
        assertThat(competency.getScenariosCompleted()).isEqualTo(1);

        assertThat(accrualLogRepository.existsByUserProgressId(examPiece.userProgressId())).isTrue();
    }
}
