package ru.vsm.backend.gamification.challenge.web.dto;

import java.time.Instant;

/**
 * Активный челлендж месяца с прогрессом конкретного игрока (или нулевым прогрессом, если игрок
 * ещё не сделал ни одного зачитываемого шага — так же, как {@code ProfileResponse} без
 * прохождений).
 *
 * @param goalType имя {@code ChallengeGoalType} (BLOCK_SCENARIOS_NO_FAILURE/SAFETY_STREAK/ROLE_MODEL_ALL_STEPS)
 * @param targetBlock блок-фильтр цели; null — любой блок
 * @param safetyThreshold порог шкалы безопасности; null для типов, где он не используется
 * @param current текущее значение счётчика прогресса игрока
 * @param completed выполнен ли челлендж этим игроком
 * @param completedAt момент выполнения; null, пока не выполнен
 */
public record ChallengeDto(
        String code,
        String title,
        String description,
        String goalType,
        String targetBlock,
        int targetCount,
        Integer safetyThreshold,
        int rewardPoints,
        Instant startsAt,
        Instant endsAt,
        int current,
        boolean completed,
        Instant completedAt) {
}
