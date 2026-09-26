package ru.vsm.backend.gamification.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.vsm.backend.auth.service.PlayerVerificationService;
import ru.vsm.backend.config.GamificationLimitsProperties;
import ru.vsm.backend.gamification.challenge.domain.Challenge;
import ru.vsm.backend.gamification.challenge.domain.ChallengeProgress;
import ru.vsm.backend.gamification.challenge.repository.ChallengeProgressRepository;
import ru.vsm.backend.gamification.challenge.repository.ChallengeRepository;
import ru.vsm.backend.gamification.domain.AccrualLogEntry;
import ru.vsm.backend.gamification.domain.AchievementCode;
import ru.vsm.backend.gamification.domain.CompetencyScore;
import ru.vsm.backend.gamification.domain.NotificationType;
import ru.vsm.backend.gamification.domain.PlayerAchievement;
import ru.vsm.backend.gamification.domain.PlayerProfile;
import ru.vsm.backend.gamification.repository.AccrualLogRepository;
import ru.vsm.backend.gamification.repository.CompetencyScoreRepository;
import ru.vsm.backend.gamification.repository.PlayerAchievementRepository;
import ru.vsm.backend.gamification.repository.PlayerProfileRepository;
import ru.vsm.backend.gamification.team.service.TeamService;
import ru.vsm.backend.scenario.domain.ScenarioOutcome;
import ru.vsm.backend.scenario.event.ScenarioCompletedEvent;

/**
 * Начисление очков компетенций и ачивок по {@link ScenarioCompletedEvent}. Тем же обработчиком
 * создаются уведомления игрока (новая ачивка / личный рекорд по сценарию / рост в лидерборде)
 * через {@link NotificationService} — см. {@link #evaluateAchievements},
 * {@link #evaluatePersonalBest}, {@link #evaluateRankUp}.
 *
 * <p><b>Формула начисления</b> (простая и объяснимая):
 * <pre>
 * base(outcome)      = SUCCESS: 100, PARTIAL: 50, FAILURE: 20 (очки за участие)
 * loyaltyGain        = max(0, event.loyaltyScore())   -- в очки компетенций блока не уходит "в минус"
 * safetyGain         = max(0, event.safetyScore())
 * timeoutPenalty     = event.hadTimeout() ? 5 : 0
 * rawPoints          = max(0, base(outcome) + loyaltyGain + safetyGain - timeoutPenalty)
 * totalPoints        = applyAntifraudLimits(rawPoints)  -- множитель неподтверждённости + суточный потолок
 *
 * profile.totalScore        += totalPoints
 * profile.scenariosCompleted += 1
 * competency(block).loyaltyPoints += loyaltyGain
 * competency(block).safetyPoints  += safetyGain
 * </pre>
 *
 * <p><b>Антифрод</b> (см. {@link #applyAntifraudLimits}): {@code loyaltyGain}/{@code safetyGain} —
 * компетенции по конкретной шкале, ограничениям не подвергаются (это оценка навыка, а не "очки"
 * для лидерборда/накрутки); ограничивается только {@code totalPoints} — то, что уходит в
 * {@code profile.totalScore} и в лог начислений (а значит и в личный рекорд/суточную сумму).
 *
 * <p><b>Зачётность прохождения</b> ({@code awardable} в {@link #processEvent}) — второй, более
 * строгий антифрод-гейт поверх суточного потолка: {@code profile.totalScore}/
 * {@code scenariosCompleted}, ачивки ({@link #evaluateAchievements}) и прогресс челленджей
 * ({@link #evaluateChallenges}) начисляются, только если {@link ScenarioCompletedEvent#examMode()}
 * {@code == false} и {@link ScenarioCompletedEvent#firstCompletion()} {@code == true} — иначе
 * повторное прохождение уже завершённого сценария (новый {@code userProgressId} на каждый заход,
 * идемпотентность выше его не ловит) или прохождение пункта экзамена (который вознаграждается
 * отдельно за итоговую оценку, см. {@code ExamAccrualService}) приносили бы очки без ограничения.
 * Компетенции по шкалам ({@code CompetencyScore}) и журнал начислений (см. ниже) от этого гейта
 * не зависят — они нужны аналитике компетенций/разбору решений, которым важны все реальные попытки
 * игрока, а не только зачётные.
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
@EnableConfigurationProperties(GamificationLimitsProperties.class)
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
    private final NotificationService notificationService;
    private final ChallengeRepository challengeRepository;
    private final ChallengeProgressRepository challengeProgressRepository;
    private final TeamService teamService;
    private final PlayerVerificationService playerVerificationService;
    private final GamificationLimitsProperties gamificationLimitsProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(ScenarioCompletedEvent event) {
        if (accrualLogRepository.existsByUserProgressId(event.userProgressId())) {
            log.info("Скип начисления: userProgressId={} уже обработан", event.userProgressId());
            return;
        }

        // Полные очки/ачивки/челленджи — только за первое, не-экзаменационное прохождение этого
        // сценария (см. Javadoc ScenarioCompletedEvent#examMode()/#firstCompletion() и политику в
        // README «Начисление очков и антифрод»): без этого гейта один и тот же лёгкий сценарий
        // можно было фармить неограниченно (новый userProgressId на каждое повторное прохождение
        // обходит идемпотентность по userProgressId выше), а экзамен приносил бы очки дважды — за
        // каждый пункт по отдельности И за итоговую оценку ({@code ExamAccrualService}).
        // Компетенции по шкалам ниже НЕ подчиняются этому гейту — аналитика компетенций должна
        // видеть реальные попытки игрока (в т.ч. экзаменационные и повторные), не только зачётные.
        boolean awardable = !event.examMode() && event.firstCompletion();

        int basePoints = basePoints(event.outcome());
        int loyaltyGain = Math.max(0, event.loyaltyScore());
        int safetyGain = Math.max(0, event.safetyScore());
        int timeoutPenalty = event.hadTimeout() ? TIMEOUT_PENALTY : 0;
        int rawPoints = Math.max(0, basePoints + loyaltyGain + safetyGain - timeoutPenalty);
        int totalPoints = awardable ? applyAntifraudLimits(event.userId(), rawPoints) : 0;

        Optional<PlayerProfile> existingProfile = playerProfileRepository.findById(event.userId());
        // Ранг ДО обновления очков — читаем, пока строка профиля в БД ещё содержит старый
        // totalScore (запрос выполняется раньше любой мутации/save этого профиля в текущей
        // транзакции). Для нового игрока (первое прохождение) "ранга до" не существует —
        // сравнивать не с чем, поэтому LEADERBOARD_RANK_UP для первого прохождения не считается.
        Long rankBefore = existingProfile.isPresent()
                ? playerProfileRepository.findRankByPlayerId(event.userId())
                : null;

        // Лучший результат одного прохождения ЭТОГО сценария игроком ранее — до вставки текущей
        // записи в журнал начислений.
        Optional<Integer> previousBestForScenario = accrualLogRepository
                .findMaxTotalPointsByPlayerIdAndScenarioCode(event.userId(), event.scenarioCode());

        // Позиция команды игрока ДО обновления его очков — та же логика, что у rankBefore выше:
        // читаем, пока totalScore участников команды в БД ещё не отражает текущее событие.
        Optional<UUID> teamId = teamService.findTeamIdByPlayerId(event.userId());
        Integer teamRankBefore = teamId.map(teamService::rankOfTeam).orElse(null);

        PlayerProfile profile = existingProfile
                .orElseGet(() -> PlayerProfile.builder().id(event.userId()).build());
        profile.setTotalScore(profile.getTotalScore() + totalPoints);
        if (awardable) {
            profile.setScenariosCompleted(profile.getScenariosCompleted() + 1);
        }
        profile.setUpdatedAt(Instant.now());
        playerProfileRepository.save(profile);

        // Компетенции по шкалам — как есть, независимо от awardable (см. комментарий выше).
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

        // Журнал начислений пишется всегда, даже когда totalPoints=0 (не awardable) — это и
        // идемпотентность на будущее (проверка existsByUserProgressId выше сработает для повторной
        // доставки того же события), и история для аналитики (loyalty/safetyPointsAwarded отражают
        // реальный результат попытки независимо от awardable).
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

        if (awardable) {
            evaluateAchievements(event, profile);
            evaluatePersonalBest(event, totalPoints, previousBestForScenario);
            evaluateRankUp(event, rankBefore);
            evaluateChallenges(event, profile);
            evaluateTeamRankUp(teamId, teamRankBefore);
        }

        log.info("Начислено {} очков игроку {} за прохождение {} (userProgressId={}, awardable={})",
                totalPoints, event.userId(), event.scenarioCode(), event.userProgressId(), awardable);
    }

    private int basePoints(ScenarioOutcome outcome) {
        return switch (outcome) {
            case SUCCESS -> BASE_SUCCESS;
            case PARTIAL -> BASE_PARTIAL;
            case FAILURE -> BASE_FAILURE;
        };
    }

    /**
     * Антифрод (см. договорённости проекта по Q&amp;A хакатона), в порядке применения:
     * <ol>
     *   <li>{@link GamificationLimitsProperties#getUnverifiedMultiplier()} — профиль без
     *       подтверждённой личности ({@link PlayerVerificationService#isVerified} == {@code false})
     *       получает только долю очков одного прохождения; подтверждённый (демо-вход через
     *       Госуслуги/ЕСИА, {@code auth.esia}) — очки без урезания;</li>
     *   <li>{@link GamificationLimitsProperties#getDailyPointsLimit()} — суточный (UTC, календарные
     *       сутки) потолок суммы {@code AccrualLogEntry.totalPointsAwarded} на игрока: это
     *       прохождение довносит остаток лимита, а не всю сумму после множителя, если игрок уже
     *       близок к потолку. Достаточно щедрый дефолт, чтобы не задевать честную игру — см. javadoc
     *       {@link GamificationLimitsProperties#getDailyPointsLimit()}.
     * </ol>
     */
    private int applyAntifraudLimits(UUID playerId, int rawPoints) {
        boolean verified = playerVerificationService.isVerified(playerId);
        int afterMultiplier = verified
                ? rawPoints
                : (int) Math.round(rawPoints * gamificationLimitsProperties.getUnverifiedMultiplier());

        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        int awardedToday = accrualLogRepository.sumTotalPointsByPlayerIdSince(playerId, startOfToday);
        int remaining = Math.max(0, gamificationLimitsProperties.getDailyPointsLimit() - awardedToday);
        return Math.min(afterMultiplier, remaining);
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
                notificationService.create(playerId, NotificationType.ACHIEVEMENT_UNLOCKED,
                        "Новая ачивка: " + code.title(), code.description(), event.userProgressId());
            }
        }
    }

    /** Личный рекорд — превышение лучшего результата ОДНОГО прохождения этого сценария ранее. */
    private void evaluatePersonalBest(ScenarioCompletedEvent event, int totalPoints,
            Optional<Integer> previousBestForScenario) {
        if (previousBestForScenario.isEmpty() || totalPoints <= previousBestForScenario.get()) {
            return;
        }
        notificationService.create(event.userId(), NotificationType.NEW_PERSONAL_BEST,
                "Новый личный рекорд",
                "Сценарий «%s»: %d очков (было %d)".formatted(
                        event.scenarioCode(), totalPoints, previousBestForScenario.get()),
                event.userProgressId());
    }

    /** Рост в лидерборде — позиция (меньше = выше) улучшилась по сравнению с позицией до этого события. */
    private void evaluateRankUp(ScenarioCompletedEvent event, Long rankBefore) {
        if (rankBefore == null) {
            return;
        }
        long rankAfter = playerProfileRepository.findRankByPlayerId(event.userId());
        if (rankAfter < rankBefore) {
            notificationService.create(event.userId(), NotificationType.LEADERBOARD_RANK_UP,
                    "Рост в лидерборде",
                    "Вы поднялись с %d места на %d".formatted(rankBefore, rankAfter),
                    event.userProgressId());
        }
    }

    /**
     * Уведомляет ВСЕХ участников команды игрока, если после этого события команда впервые
     * заняла 1-е место в командном рейтинге (см. {@link TeamService#getLeaderboard}). Игрок без
     * команды или команда, уже бывшая на 1-м месте до события, уведомлений не порождают.
     */
    private void evaluateTeamRankUp(Optional<UUID> teamId, Integer teamRankBefore) {
        if (teamId.isEmpty() || teamRankBefore == null || teamRankBefore == 1) {
            return;
        }
        int teamRankAfter = teamService.rankOfTeam(teamId.get());
        if (teamRankAfter != 1) {
            return;
        }
        String teamName = teamService.teamName(teamId.get()).orElse("команда");
        for (UUID memberId : teamService.memberIds(teamId.get())) {
            notificationService.create(memberId, NotificationType.TEAM_RANK_UP,
                    "Команда вышла на 1-е место",
                    "«%s» поднялась на 1-е место в командном рейтинге".formatted(teamName), null);
        }
        log.info("Команда {} вышла на 1-е место командного рейтинга", teamId.get());
    }

    /**
     * Обновляет прогресс игрока по всем челленджам, период действия которых
     * ({@code startsAt}-{@code endsAt}) покрывает момент завершения прохождения — истёкшие или
     * ещё не начавшиеся челленджи в выборку не попадают и, соответственно, не засчитываются.
     * Уже выполненные челленджем (completed=true) прогрессом больше не пересчитываются.
     */
    private void evaluateChallenges(ScenarioCompletedEvent event, PlayerProfile profile) {
        Instant at = event.completedAt();
        List<Challenge> activeChallenges =
                challengeRepository.findByStartsAtLessThanEqualAndEndsAtGreaterThanEqual(at, at);

        for (Challenge challenge : activeChallenges) {
            ChallengeProgress progress = challengeProgressRepository
                    .findByChallengeIdAndPlayerId(challenge.getId(), event.userId())
                    .orElseGet(() -> ChallengeProgress.builder()
                            .challengeId(challenge.getId())
                            .playerId(event.userId())
                            .build());
            if (progress.isCompleted()) {
                continue;
            }

            int nextValue = nextChallengeValue(challenge, progress.getCurrentValue(), event);
            progress.setCurrentValue(nextValue);
            progress.setUpdatedAt(Instant.now());

            if (nextValue >= challenge.getTargetCount()) {
                progress.setCompleted(true);
                progress.setCompletedAt(Instant.now());
                challengeProgressRepository.save(progress);
                awardChallengeCompletion(event, profile, challenge);
            } else {
                challengeProgressRepository.save(progress);
            }
        }
    }

    /** Логика счётчика по типу цели — см. {@link ru.vsm.backend.gamification.challenge.domain.ChallengeGoalType}. */
    private int nextChallengeValue(Challenge challenge, int current, ScenarioCompletedEvent event) {
        String targetBlock = challenge.getTargetBlock();
        if (targetBlock != null && !targetBlock.equals(event.scenarioBlock())) {
            // Событие вне области действия челленджа (другой блок) — игнорируется, счётчик
            // не растёт и не сбрасывается (в т.ч. для SAFETY_STREAK).
            return current;
        }
        return switch (challenge.getGoalType()) {
            case BLOCK_SCENARIOS_NO_FAILURE ->
                    event.outcome() == ScenarioOutcome.FAILURE ? current : current + 1;
            case SAFETY_STREAK ->
                    event.safetyScore() >= challenge.getSafetyThreshold() ? current + 1 : 0;
            case ROLE_MODEL_ALL_STEPS ->
                    event.allRoleStepsFollowed() ? current + 1 : current;
        };
    }

    /** Награда за выполнение челленджа: очки в профиль + ачивка (если задана) + уведомление. */
    private void awardChallengeCompletion(ScenarioCompletedEvent event, PlayerProfile profile, Challenge challenge) {
        profile.setTotalScore(profile.getTotalScore() + challenge.getRewardPoints());
        profile.setUpdatedAt(Instant.now());
        playerProfileRepository.save(profile);

        if (challenge.getRewardAchievementCode() != null) {
            AchievementCode code = AchievementCode.valueOf(challenge.getRewardAchievementCode());
            if (!playerAchievementRepository.existsByPlayerIdAndAchievementCode(event.userId(), code)) {
                playerAchievementRepository.save(PlayerAchievement.builder()
                        .playerId(event.userId())
                        .achievementCode(code)
                        .build());
                notificationService.create(event.userId(), NotificationType.ACHIEVEMENT_UNLOCKED,
                        "Новая ачивка: " + code.title(), code.description(), event.userProgressId());
            }
        }

        log.info("Челлендж {} выполнен игроком {} (+{} очков)",
                challenge.getCode(), event.userId(), challenge.getRewardPoints());
        notificationService.create(event.userId(), NotificationType.CHALLENGE_COMPLETED,
                "Челлендж выполнен: " + challenge.getTitle(),
                "Награда: %d очков".formatted(challenge.getRewardPoints()),
                event.userProgressId());
    }
}
