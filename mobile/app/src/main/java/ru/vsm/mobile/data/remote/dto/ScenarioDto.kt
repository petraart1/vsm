package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** `ru.vsm.backend.scenario.web.dto.ScenarioSummaryResponse` */
@Serializable
data class ScenarioSummaryDto(
    val id: String,
    val code: String,
    val situationRef: Int? = null,
    val block: String,
    val title: String,
    val description: String,
    val flagship: Boolean,
)

/** `ru.vsm.backend.scenario.web.dto.ChoiceOptionResponse` */
@Serializable
data class ChoiceOptionDto(
    val id: String,
    val code: String,
    val text: String,
)

/** `ru.vsm.backend.scenario.web.dto.NodeStateResponse` */
@Serializable
data class NodeStateDto(
    val nodeId: String,
    val code: String,
    val type: NodeTypeDto,
    val text: String,
    val terminal: Boolean,
    val timerSeconds: Int? = null,
    val deadlineAt: String? = null,
    val terminalOutcome: ScenarioOutcomeDto? = null,
    val outcomeSummary: String? = null,
    val choices: List<ChoiceOptionDto> = emptyList(),
)

/** `ru.vsm.backend.scenario.web.dto.ProgressStateResponse` */
@Serializable
data class ProgressStateDto(
    val progressId: String,
    val scenarioId: String,
    val scenarioCode: String,
    val status: ProgressStatusDto,
    val loyaltyScore: Int,
    val safetyScore: Int,
    val currentNode: NodeStateDto? = null,
)

/**
 * `ru.vsm.backend.scenario.web.dto.ChoiceAppliedResponse` — [loyaltyDelta]/[safetyDelta]/
 * [loyaltyScore]/[safetyScore] сериализуются как явный JSON `null` (не отсутствуют в теле — Jackson
 * не подавляет null для этого record'а), когда прохождение — пункт экзамена (см. `ExamService`);
 * поэтому здесь они `Int?`, а не `Int`, иначе разбор ответа падал бы во время экзамена.
 */
@Serializable
data class ChoiceAppliedDto(
    val progressId: String,
    val appliedChoiceId: String,
    val appliedChoiceCode: String,
    val wasTimeout: Boolean,
    val loyaltyDelta: Int? = null,
    val safetyDelta: Int? = null,
    val loyaltyScore: Int? = null,
    val safetyScore: Int? = null,
    val status: ProgressStatusDto,
    val finalOutcome: ScenarioOutcomeDto? = null,
    val nextNode: NodeStateDto? = null,
)

/** `ru.vsm.backend.scenario.web.dto.ErrorResponse` — единый формат тела ошибки REST. */
@Serializable
data class ErrorResponseDto(
    val error: String,
    val message: String? = null,
)
