package ru.vsm.mobile.domain.model

/**
 * Агрегат по одному блоку ситуаций (boarding/medical/safety/...) за все завершённые прохождения
 * игрока в этом блоке.
 *
 * @param successRate доля прохождений с исходом SUCCESS (0..1)
 * @param failureRate доля прохождений с исходом FAILURE (0..1)
 * @param weak true, если блок попал в [CompetencyAnalytics.weakCompetencies]
 */
data class BlockCompetencyStats(
    val block: String,
    val playthroughs: Int,
    val avgLoyaltyScore: Double,
    val avgSafetyScore: Double,
    val successRate: Double,
    val failureRate: Double,
    val weak: Boolean,
)

/** Как часто игрок соблюдает/пропускает один шаг универсальной ролевой модели ответа. */
data class RoleStepCompliance(
    val step: RoleStep,
    val stepLabel: String,
    val timesFollowed: Int,
    val timesSkipped: Int,
    val complianceRate: Double,
)

/** Частое нарушение норматива: сколько раз игрок выбирал вариант, нарушающий эту норму. */
data class NormViolation(
    val normRef: String,
    val count: Int,
)

/** Один рекомендованный к прохождению сценарий. */
data class ScenarioRecommendation(
    val scenarioId: String,
    val code: String,
    val title: String,
    val block: String,
    val reason: RecommendationReason,
)

/**
 * Аналитика компетенций игрока — агрегат по всем его завершённым прохождениям, в отличие от
 * [Debrief], который разбирает одно прохождение. Игрок без завершённых прохождений получает
 * этот же тип с [totalPlaythroughs] == 0 и пустыми списками, а не ошибку.
 */
data class CompetencyAnalytics(
    val playerId: String,
    val totalPlaythroughs: Int,
    val blockStats: List<BlockCompetencyStats>,
    val roleStepCompliance: List<RoleStepCompliance>,
    val frequentNormViolations: List<NormViolation>,
    val weakCompetencies: List<String>,
    val recommendations: List<ScenarioRecommendation>,
)
