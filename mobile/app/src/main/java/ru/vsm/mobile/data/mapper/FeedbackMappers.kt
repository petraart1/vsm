package ru.vsm.mobile.data.mapper

import ru.vsm.mobile.data.remote.dto.BlockCompetencyStatsDto
import ru.vsm.mobile.data.remote.dto.CompetencyAnalyticsResponseDto
import ru.vsm.mobile.data.remote.dto.DebriefResponseDto
import ru.vsm.mobile.data.remote.dto.DebriefStepDto
import ru.vsm.mobile.data.remote.dto.KeyMomentDto
import ru.vsm.mobile.data.remote.dto.NormViolationDto
import ru.vsm.mobile.data.remote.dto.RecommendationReasonDto
import ru.vsm.mobile.data.remote.dto.RoleStepComplianceDto
import ru.vsm.mobile.data.remote.dto.RoleStepDto
import ru.vsm.mobile.data.remote.dto.ScenarioRecommendationDto
import ru.vsm.mobile.domain.model.BlockCompetencyStats
import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Debrief
import ru.vsm.mobile.domain.model.DebriefStep
import ru.vsm.mobile.domain.model.KeyMoment
import ru.vsm.mobile.domain.model.NormViolation
import ru.vsm.mobile.domain.model.RecommendationReason
import ru.vsm.mobile.domain.model.RoleStep
import ru.vsm.mobile.domain.model.RoleStepCompliance
import ru.vsm.mobile.domain.model.ScenarioRecommendation

fun RoleStepDto.toDomain(): RoleStep = when (this) {
    RoleStepDto.ACKNOWLEDGE -> RoleStep.ACKNOWLEDGE
    RoleStepDto.RULE -> RoleStep.RULE
    RoleStepDto.SOLUTION -> RoleStep.SOLUTION
    RoleStepDto.REASSURE -> RoleStep.REASSURE
}

fun RecommendationReasonDto.toDomain(): RecommendationReason = when (this) {
    RecommendationReasonDto.NOT_PLAYED -> RecommendationReason.NOT_PLAYED
    RecommendationReasonDto.FAILED -> RecommendationReason.FAILED
    RecommendationReasonDto.PARTIAL -> RecommendationReason.PARTIAL
}

fun DebriefStepDto.toDomain(): DebriefStep = DebriefStep(
    sequenceIndex = sequenceIndex,
    nodeCode = nodeCode,
    nodeText = nodeText,
    nodeType = nodeType.toDomain(),
    choiceCode = choiceCode,
    choiceText = choiceText,
    wasTimeout = wasTimeout,
    loyaltyDelta = loyaltyDelta,
    safetyDelta = safetyDelta,
    roleStepsCompleted = roleStepsCompleted,
    roleStepsSkipped = roleStepsSkipped,
    scaleConflict = scaleConflict,
    explanation = explanation,
    hiddenCommunicationEffect = hiddenCommunicationEffect,
)

fun KeyMomentDto.toDomain(): KeyMoment = KeyMoment(
    sequenceIndex = sequenceIndex,
    nodeText = nodeText,
    chosenChoiceText = chosenChoiceText,
    chosenLoyaltyDelta = chosenLoyaltyDelta,
    chosenSafetyDelta = chosenSafetyDelta,
    betterChoiceText = betterChoiceText,
    betterLoyaltyDelta = betterLoyaltyDelta,
    betterSafetyDelta = betterSafetyDelta,
    adviceText = adviceText,
    betterExplanation = betterExplanation,
)

fun DebriefResponseDto.toDomain(): Debrief = Debrief(
    userProgressId = userProgressId,
    scenarioId = scenarioId,
    scenarioCode = scenarioCode,
    scenarioTitle = scenarioTitle,
    scenarioBlock = scenarioBlock,
    progressStatus = progressStatus.toDomain(),
    outcome = outcome?.toDomain(),
    verdict = verdict,
    interrupted = interrupted,
    finalLoyaltyScore = finalLoyaltyScore,
    finalSafetyScore = finalSafetyScore,
    timeline = timeline.map { it.toDomain() },
    keyMoment = keyMoment?.toDomain(),
    summary = summary,
    normReferences = normReferences,
)

fun BlockCompetencyStatsDto.toDomain(): BlockCompetencyStats = BlockCompetencyStats(
    block = block,
    playthroughs = playthroughs,
    avgLoyaltyScore = avgLoyaltyScore,
    avgSafetyScore = avgSafetyScore,
    successRate = successRate,
    failureRate = failureRate,
    weak = weak,
)

fun RoleStepComplianceDto.toDomain(): RoleStepCompliance = RoleStepCompliance(
    step = step.toDomain(),
    stepLabel = stepLabel,
    timesFollowed = timesFollowed,
    timesSkipped = timesSkipped,
    complianceRate = complianceRate,
)

fun NormViolationDto.toDomain(): NormViolation = NormViolation(normRef = normRef, count = count)

fun ScenarioRecommendationDto.toDomain(): ScenarioRecommendation = ScenarioRecommendation(
    scenarioId = scenarioId,
    code = code,
    title = title,
    block = block,
    reason = reason.toDomain(),
)

fun CompetencyAnalyticsResponseDto.toDomain(): CompetencyAnalytics = CompetencyAnalytics(
    playerId = playerId,
    totalPlaythroughs = totalPlaythroughs,
    blockStats = blockStats.map { it.toDomain() },
    roleStepCompliance = roleStepCompliance.map { it.toDomain() },
    frequentNormViolations = frequentNormViolations.map { it.toDomain() },
    weakCompetencies = weakCompetencies,
    recommendations = recommendations.map { it.toDomain() },
)
