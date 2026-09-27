package ru.vsm.mobile.data.mapper

import ru.vsm.mobile.data.remote.dto.ChoiceAppliedDto
import ru.vsm.mobile.data.remote.dto.ChoiceOptionDto
import ru.vsm.mobile.data.remote.dto.NodeStateDto
import ru.vsm.mobile.data.remote.dto.NodeTypeDto
import ru.vsm.mobile.data.remote.dto.ProgressStateDto
import ru.vsm.mobile.data.remote.dto.ProgressStatusDto
import ru.vsm.mobile.data.remote.dto.ScenarioOutcomeDto
import ru.vsm.mobile.data.remote.dto.ScenarioSummaryDto
import ru.vsm.mobile.domain.model.ChoiceOption
import ru.vsm.mobile.domain.model.ChoiceResult
import ru.vsm.mobile.domain.model.NodeType
import ru.vsm.mobile.domain.model.ProgressStatus
import ru.vsm.mobile.domain.model.ScenarioNode
import ru.vsm.mobile.domain.model.ScenarioOutcome
import ru.vsm.mobile.domain.model.ScenarioProgress
import ru.vsm.mobile.domain.model.ScenarioSummary

fun NodeTypeDto.toDomain(): NodeType = when (this) {
    NodeTypeDto.DIALOGUE -> NodeType.DIALOGUE
    NodeTypeDto.ESCALATION -> NodeType.ESCALATION
    NodeTypeDto.TERMINAL -> NodeType.TERMINAL
}

fun ScenarioOutcomeDto.toDomain(): ScenarioOutcome = when (this) {
    ScenarioOutcomeDto.SUCCESS -> ScenarioOutcome.SUCCESS
    ScenarioOutcomeDto.PARTIAL -> ScenarioOutcome.PARTIAL
    ScenarioOutcomeDto.FAILURE -> ScenarioOutcome.FAILURE
}

fun ProgressStatusDto.toDomain(): ProgressStatus = when (this) {
    ProgressStatusDto.IN_PROGRESS -> ProgressStatus.IN_PROGRESS
    ProgressStatusDto.COMPLETED -> ProgressStatus.COMPLETED
    ProgressStatusDto.ABANDONED -> ProgressStatus.ABANDONED
}

fun ScenarioSummaryDto.toDomain(): ScenarioSummary = ScenarioSummary(
    id = id,
    code = code,
    situationRef = situationRef,
    block = block,
    title = title,
    description = description,
    flagship = flagship,
)

fun ChoiceOptionDto.toDomain(): ChoiceOption = ChoiceOption(id = id, code = code, text = text)

fun NodeStateDto.toDomain(): ScenarioNode = ScenarioNode(
    nodeId = nodeId,
    code = code,
    type = type.toDomain(),
    text = text,
    terminal = terminal,
    timerSeconds = timerSeconds,
    deadlineAt = deadlineAt,
    terminalOutcome = terminalOutcome?.toDomain(),
    outcomeSummary = outcomeSummary,
    choices = choices.map { it.toDomain() },
)

fun ProgressStateDto.toDomain(): ScenarioProgress = ScenarioProgress(
    progressId = progressId,
    scenarioId = scenarioId,
    scenarioCode = scenarioCode,
    status = status.toDomain(),
    loyaltyScore = loyaltyScore,
    safetyScore = safetyScore,
    currentNode = currentNode?.toDomain(),
)

fun ChoiceAppliedDto.toDomain(): ChoiceResult = ChoiceResult(
    progressId = progressId,
    appliedChoiceId = appliedChoiceId,
    appliedChoiceCode = appliedChoiceCode,
    wasTimeout = wasTimeout,
    loyaltyDelta = loyaltyDelta,
    safetyDelta = safetyDelta,
    loyaltyScore = loyaltyScore,
    safetyScore = safetyScore,
    status = status.toDomain(),
    finalOutcome = finalOutcome?.toDomain(),
    nextNode = nextNode?.toDomain(),
)
