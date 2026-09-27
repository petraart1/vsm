package ru.vsm.backend.gamification.challenge.domain;

/** Тип цели челленджа месяца. Условие вычисляется в {@code GamificationAccrualService} по каждому */
public enum ChallengeGoalType {

    /**
     * N завершённых сценариев блока {@code targetBlock} без исхода FAILURE. Счётчик
     * накопительный (не обязательно подряд): прохождения с исходом FAILURE просто не
     * засчитываются и не сбрасывают счётчик.
     */
    BLOCK_SCENARIOS_NO_FAILURE,

    /** N сценариев ПОДРЯД с итоговым рейтингом безопасности не ниже {@code safetyThreshold}. */
    SAFETY_STREAK,

    /** N завершённых сценариев, в каждом из которых по совокупности всех сделанных выборов */
    ROLE_MODEL_ALL_STEPS
}
