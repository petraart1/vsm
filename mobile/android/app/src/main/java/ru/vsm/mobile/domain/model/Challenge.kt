package ru.vsm.mobile.domain.model

/**
 * Активный челлендж месяца с прогрессом конкретного игрока (или нулевым прогрессом, если запрос
 * анонимный — см. [ru.vsm.mobile.domain.repository.GamificationRepository.getChallenges]).
 *
 * @param goalType имя цели на backend (`BLOCK_SCENARIOS_NO_FAILURE`/`SAFETY_STREAK`/`ROLE_MODEL_ALL_STEPS`) —
 *   строка, а не enum: список целей может расшириться быстрее, чем этот клиент, а с UI-стороны
 *   достаточно текста/описания, конкретный алгоритм подсчёта прогресса всегда считает backend.
 * @param targetBlock блок-фильтр цели; `null` — любой блок.
 * @param safetyThreshold порог шкалы безопасности; `null` для типов, где он не используется.
 * @param current текущее значение счётчика прогресса игрока.
 * @param completed выполнен ли челлендж этим игроком.
 */
data class Challenge(
    val code: String,
    val title: String,
    val description: String,
    val goalType: String,
    val targetBlock: String?,
    val targetCount: Int,
    val safetyThreshold: Int?,
    val rewardPoints: Int,
    val startsAt: String,
    val endsAt: String,
    val current: Int,
    val completed: Boolean,
    val completedAt: String?,
)
