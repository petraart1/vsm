package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** `ru.vsm.backend.scenario.web.dto.ExamScenarioResponse` */
@Serializable
data class ExamScenarioDto(
    val sortOrder: Int,
    val scenarioId: String,
    val scenarioCode: String,
    val block: String,
    val title: String,
    val flagship: Boolean,
    val userProgressId: String? = null,
    val completed: Boolean,
    val outcome: ScenarioOutcomeDto? = null,
    val loyaltyScore: Int? = null,
    val safetyScore: Int? = null,
)

/** `ru.vsm.backend.scenario.web.dto.ExamResultResponse` */
@Serializable
data class ExamResultDto(
    val avgLoyaltyScore: Double,
    val avgSafetyScore: Double,
    val successRate: Double,
    val grade: ExamGradeDto,
    val weakBlocks: List<String>,
)

/** `ru.vsm.backend.scenario.web.dto.ExamResponse` */
@Serializable
data class ExamResponseDto(
    val examId: String,
    val playerId: String,
    val carClass: CarClassDto,
    val status: ExamStatusDto,
    val size: Int,
    val currentIndex: Int,
    val startedAt: String,
    val finishedAt: String? = null,
    val scenarios: List<ExamScenarioDto>,
    val result: ExamResultDto? = null,
)
