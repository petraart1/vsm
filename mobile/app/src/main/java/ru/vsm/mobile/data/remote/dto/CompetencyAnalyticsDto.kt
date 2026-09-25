package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** `ru.vsm.backend.feedback.dto.BlockCompetencyStatsDto` */
@Serializable
data class BlockCompetencyStatsDto(
    val block: String,
    val playthroughs: Int,
    val avgLoyaltyScore: Double,
    val avgSafetyScore: Double,
    val successRate: Double,
    val failureRate: Double,
    val weak: Boolean,
)

/** `ru.vsm.backend.feedback.dto.RoleStepComplianceDto` */
@Serializable
data class RoleStepComplianceDto(
    val step: RoleStepDto,
    val stepLabel: String,
    val timesFollowed: Int,
    val timesSkipped: Int,
    val complianceRate: Double,
)

/** `ru.vsm.backend.feedback.dto.NormViolationDto` */
@Serializable
data class NormViolationDto(
    val normRef: String,
    val count: Int,
)

/** `ru.vsm.backend.feedback.dto.ScenarioRecommendationDto` */
@Serializable
data class ScenarioRecommendationDto(
    val scenarioId: String,
    val code: String,
    val title: String,
    val block: String,
    val reason: RecommendationReasonDto,
)

/** `ru.vsm.backend.feedback.dto.CompetencyAnalyticsResponse` */
@Serializable
data class CompetencyAnalyticsResponseDto(
    val playerId: String,
    val totalPlaythroughs: Int,
    val blockStats: List<BlockCompetencyStatsDto> = emptyList(),
    val roleStepCompliance: List<RoleStepComplianceDto> = emptyList(),
    val frequentNormViolations: List<NormViolationDto> = emptyList(),
    val weakCompetencies: List<String> = emptyList(),
    val recommendations: List<ScenarioRecommendationDto> = emptyList(),
)
