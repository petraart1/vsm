package ru.vsm.backend.gamification.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.gamification.domain.AccrualLogEntry;
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
 * Начисление очков компетенций и ачивок по {@link ScenarioCompletedEvent}.
 *
 * <p><b>Формула начисления</b> (простая и объяснимая):
 * <pre>
 * base(outcome)      = SUCCESS: 100, PARTIAL: 50, FAILURE: 20 (очки за участие)
 * loyaltyGain        = max(0, event.loyaltyScore())   -- в очки компетенций блока не уходит "в минус"
 * safetyGain         = max(0, event.safetyScore())
 * timeoutPenalty     = event.hadTimeout() ? 5 : 0
 * totalPoints        = max(0, base(outcome) + loyaltyGain + safetyGain - timeoutPenalty)
 *
 * profile.totalScore        += totalPoints
 * profile.scenariosCompleted += 1
 * competency(block).loyaltyPoints += loyaltyGain
 * competency(block).safetyPoints  += safetyGain
 * </pre>
 *
 * <p><b>Идемпотентность</b>: перед начислением проверяется {@link AccrualLogRepository
 * #existsByUserProgressId}. Запись в журнал и все обновления профиля/компетенций/ачивок
 * происходят в одной транзакции — повторная доставка события с тем же {@code userProgressId}
 * (например, ретрай слушателя) не начисляет очки дважды.
 *
 * <p><b>{@code propagation = REQUIRES_NEW} на {@link #processEvent}, а не дефолтный
 * {@code REQUIRED}</b>: метод вызывается из {@code ScenarioCompletedEventListener}
 * (`@TransactionalEventListener(AFTER_COMMIT)`). В {@code AbstractPlatformTransactionManager}
 * {@code triggerAfterCommit()} выполняется ДО {@code cleanupAfterCompletion()}, которая
 * физически отвязывает {@code EntityManagerHolder} уже закоммиченной транзакции от потока —
 * то есть в момент вызова AFTER_COMMIT-синхронизации Spring ещё "видит" старую (фактически уже
 * завершённую) транзакцию как текущую. С {@code REQUIRED} метод присоединился бы к этому
 * умирающему контексту: SELECT'ы через ещё живую Hibernate-сессию отработали бы (это и вводило
 * в заблуждение — в логе была строка "Начислено N очков"), но новые {@code save()} никогда не
 * коммитятся — их подчищает {@code cleanupAfterCompletion()} без физического commit. Баг был
 * обнаружен на реальном REST-прохождении (frontend e2e): таблицы {@code gamification_*}
 * оставались пустыми, хотя метод отрабатывал без исключений. {@code REQUIRES_NEW} гарантирует
 * отдельную физическую транзакцию/подключение, не зависящую от уже закоммиченных ресурсов
 * исходной.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GamificationAccrualService {

    private static final int BASE_SUCCESS = 100;
    private static final int BASE_PARTIAL = 50;
    private static final int BASE_FAILURE = 20;
    private static final int TIMEOUT_PENALTY = 5;

    private static final int FLAWLESS_SAFETY_THRESHOLD = 25;
    private static final int PASSENGER_FAVORITE_THRESHOLD = 25;
    private static final int VERSATILE_BLOCK_COUNT = 3;
    private static final int VETERAN_SCENARIO_COUNT = 10;

    private final PlayerProfileRepository playerProfileRepository;
    private final CompetencyScoreRepository competencyScoreRepository;
    private final PlayerAchievementRepository playerAchievementRepository;
    private final AccrualLogRepository accrualLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(ScenarioCompletedEvent event) {
        if (accrualLogRepository.existsByUserProgressId(event.userProgressId())) {
            log.info("Скип начисления: userProgressId={} уже обработан", event.userProgressId());
            return;
        }

        int basePoints = basePoints(event.outcome());
        int loyaltyGain = Math.max(0, event.loyaltyScore());
        int safetyGain = Math.max(0, event.safetyScore());
        int timeoutPenalty = event.hadTimeout() ? TIMEOUT_PENALTY : 0;
        int totalPoints = Math.max(0, basePoints + loyaltyGain + safetyGain - timeoutPenalty);

        PlayerProfile profile = playerProfileRepository.findById(event.userId())
                .orElseGet(() -> PlayerProfile.builder().id(event.userId()).build());
        profile.setTotalScore(profile.getTotalScore() + totalPoints);
        profile.setScenariosCompleted(profile.getScenariosCompleted() + 1);
        profile.setUpdatedAt(Instant.now());
        playerProfileRepository.save(profile);

        CompetencyScore competency = competencyScoreRepository
                .findByPlayerIdAndBlock(event.userId(), event.scenarioBlock())
                .orElseGet(() -> CompetencyScore.builder()
                        .playerId(event.userId())
                        .block(event.scenarioBlock())
                        .build());
        competency.setLoyaltyPoints(competency.getLoyaltyPoints() + loyaltyGain);
        competency.setSafetyPoints(competency.getSafetyPoints() + safetyGain);
        competency.setScenariosCompleted(competency.getScenariosCompleted() + 1);
        competency.setUpdatedAt(Instant.now());
        competencyScoreRepository.save(competency);

        accrualLogRepository.save(AccrualLogEntry.builder()
                .userProgressId(event.userProgressId())
                .playerId(event.userId())
                .scenarioId(event.scenarioId())
                .scenarioCode(event.scenarioCode())
                .block(event.scenarioBlock())
                .outcome(event.outcome())
                .loyaltyPointsAwarded(loyaltyGain)
                .safetyPointsAwarded(safetyGain)
                .totalPointsAwarded(totalPoints)
                .hadTimeout(event.hadTimeout())
                .build());

        evaluateAchievements(event, profile);

        log.info("Начислено {} очков игроку {} за прохождение {} (userProgressId={})",
                totalPoints, event.userId(), event.scenarioCode(), event.userProgressId());
    }

    private int basePoints(ScenarioOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> BASE_SUCCESS;
            case PARTIAL -> BASE_PARTIAL;
            case FAILURE -> BASE_FAILURE;
        };
    }

    /** Условия — см. {@link AchievementCode}. Оценка идёт по уже обновлённому состоянию профиля. */
    private void evaluateAchievements(ScenarioCompletedEvent event, PlayerProfile profile) {
        UUID playerId = event.userId();
        Set<AchievementCode> toAward = EnumSet.noneOf(AchievementCode.class);

        if (profile.getScenariosCompleted() == 1) {
            toAward.add(AchievementCode.FIRST_SCENARIO);
        }
        if (event.outcome() == ScenarioOutcome.SUCCESS && event.safetyScore() >= FLAWLESS_SAFETY_THRESHOLD) {
            toAward.add(AchievementCode.FLAWLESS_SAFETY);
        }
        if (event.outcome() == ScenarioOutcome.SUCCESS && event.loyaltyScore() >= PASSENGER_FAVORITE_THRESHOLD) {
            toAward.add(AchievementCode.PASSENGER_FAVORITE);
        }
        if (competencyScoreRepository.countByPlayerId(playerId) >= VERSATILE_BLOCK_COUNT) {
            toAward.add(AchievementCode.VERSATILE);
        }
        if (profile.getScenariosCompleted() >= VETERAN_SCENARIO_COUNT) {
            toAward.add(AchievementCode.VETERAN);
        }

        for (AchievementCode code : toAward) {
            if (!playerAchievementRepository.existsByPlayerIdAndAchievementCode(playerId, code)) {
                playerAchievementRepository.save(PlayerAchievement.builder()
                        .playerId(playerId)
                        .achievementCode(code)
                        .build());
                log.info("Ачивка {} выдана игроку {}", code, playerId);
            }
        }
    }
}
