package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** `ru.vsm.backend.feedback.dto.DebriefStepDto` */
@Serializable
data class DebriefStepDto(
    val sequenceIndex: Int,
    val nodeCode: String,
    val nodeText: String,
    val nodeType: NodeTypeDto,
    val choiceCode: String,
    val choiceText: String,
    val wasTimeout: Boolean,
    val loyaltyDelta: Int,
    val safetyDelta: Int,
    val roleStepsCompleted: List<String> = emptyList(),
    val roleStepsSkipped: List<String> = emptyList(),
    val scaleConflict: Boolean,
    val explanation: String,
    val hiddenCommunicationEffect: Boolean,
)

/** `ru.vsm.backend.feedback.dto.KeyMomentDto` */
@Serializable
data class KeyMomentDto(
    val sequenceIndex: Int,
    val nodeText: String,
    val chosenChoiceText: String,
    val chosenLoyaltyDelta: Int,
    val chosenSafetyDelta: Int,
    val betterChoiceText: String,
    val betterLoyaltyDelta: Int,
    val betterSafetyDelta: Int,
    val adviceText: String,
    val betterExplanation: String,
)

/** `ru.vsm.backend.feedback.dto.DebriefResponse` */
@Serializable
data class DebriefResponseDto(
    val userProgressId: String,
    val scenarioId: String,
    val scenarioCode: String,
    val scenarioTitle: String,
    val scenarioBlock: String,
    val progressStatus: ProgressStatusDto,
    val outcome: ScenarioOutcomeDto? = null,
    val verdict: String,
    val interrupted: Boolean,
    val finalLoyaltyScore: Int,
    val finalSafetyScore: Int,
    val timeline: List<DebriefStepDto> = emptyList(),
    val keyMoment: KeyMomentDto? = null,
    val summary: String,
    val normReferences: List<String> = emptyList(),
)
