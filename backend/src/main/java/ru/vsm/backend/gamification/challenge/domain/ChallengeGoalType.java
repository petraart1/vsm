package ru.vsm.backend.gamification.challenge.domain;

/**
 * Тип цели челленджа месяца. Условие вычисляется в {@code GamificationAccrualService} по каждому
 * {@code ScenarioCompletedEvent} для всех активных на момент завершения прохождения челленджей
 * (см. {@link Challenge#getTargetBlock()}/{@link Challenge#getTargetCount()}/
 * {@link Challenge#getSafetyThreshold()} — какие параметры использует каждый тип).
 */
public enum ChallengeGoalType {

    /**
     * N завершённых сценариев блока {@code targetBlock} без исхода FAILURE. Счётчик
     * накопительный (не обязательно подряд): прохождения с исходом FAILURE просто не
     * засчитываются и не сбрасывают счётчик.
     */
    BLOCK_SCENARIOS_NO_FAILURE,

    /**
     * N сценариев ПОДРЯД с итоговым рейтингом безопасности не ниже {@code safetyThreshold}.
     * Любое прохождение (в области действия челленджа — см. {@code targetBlock}, может быть
     * null = любой блок) с рейтингом ниже порога сбрасывает счётчик в 0.
     */
    SAFETY_STREAK,

    /**
     * N завершённых сценариев, в каждом из которых по совокупности всех сделанных выборов
     * встретились все 4 шага универсальной ролевой модели ответа (признать/обозначить правило/
     * предложить решение/заверить) — источник флага {@code ScenarioCompletedEvent#allRoleStepsFollowed()}.
     * Счётчик накопительный, как и {@link #BLOCK_SCENARIOS_NO_FAILURE}.
     */
    ROLE_MODEL_ALL_STEPS
}
