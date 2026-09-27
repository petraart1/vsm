package ru.vsm.mobile.domain.fake

import kotlinx.coroutines.delay
import ru.vsm.mobile.domain.model.BlockCompetencyStats
import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Debrief
import ru.vsm.mobile.domain.model.DebriefStep
import ru.vsm.mobile.domain.model.KeyMoment
import ru.vsm.mobile.domain.model.NodeType
import ru.vsm.mobile.domain.model.ProgressStatus
import ru.vsm.mobile.domain.model.RoleStep
import ru.vsm.mobile.domain.model.RoleStepCompliance
import ru.vsm.mobile.domain.model.ScenarioOutcome
import ru.vsm.mobile.domain.repository.FeedbackRepository

/** Фейковая реализация для UI-слоя до готовности сетевого data-слоя: один фиксированный разбор и аналитика. */
class FakeFeedbackRepository : FeedbackRepository {

    override suspend fun getDebrief(userProgressId: String): Result<Debrief> {
        delay(200)
        return Result.success(
            Debrief(
                userProgressId = userProgressId,
                scenarioId = "scenario-boarding-no-ticket",
                scenarioCode = "boarding-no-ticket",
                scenarioTitle = "Посадка без билета",
                scenarioBlock = "boarding",
                progressStatus = ProgressStatus.COMPLETED,
                outcome = ScenarioOutcome.SUCCESS,
                verdict = "Хорошее решение",
                interrupted = false,
                finalLoyaltyScore = 2,
                finalSafetyScore = 5,
                timeline = listOf(
                    DebriefStep(
                        sequenceIndex = 0,
                        nodeCode = "start",
                        nodeText = "У турникета посадки пассажир торопится и не предъявляет билет.",
                        nodeType = NodeType.DIALOGUE,
                        choiceCode = "check-ticket",
                        choiceText = "Вежливо попросить предъявить билет",
                        wasTimeout = false,
                        loyaltyDelta = 2,
                        safetyDelta = 5,
                        roleStepsCompleted = listOf("Признать ситуацию", "Обозначить правило"),
                        roleStepsSkipped = emptyList(),
                        scaleConflict = false,
                        explanation = "Проверка билета — обязательный шаг, выполнен вежливо и без конфликта.",
                        hiddenCommunicationEffect = false,
                    ),
                ),
                keyMoment = null,
                summary = "Прохождение без ошибок — норма посадки соблюдена, пассажир не пострадал в лояльности.",
                normReferences = listOf("СТО РЖД 03011 — готовность к посадке"),
            ),
        )
    }

    override suspend fun getCompetencies(playerId: String): Result<CompetencyAnalytics> {
        delay(200)
        return Result.success(
            CompetencyAnalytics(
                playerId = playerId,
                totalPlaythroughs = 2,
                blockStats = listOf(
                    BlockCompetencyStats(
                        block = "boarding",
                        playthroughs = 1,
                        avgLoyaltyScore = 2.0,
                        avgSafetyScore = 5.0,
                        successRate = 1.0,
                        failureRate = 0.0,
                        weak = false,
                    ),
                    BlockCompetencyStats(
                        block = "medical",
                        playthroughs = 1,
                        avgLoyaltyScore = -5.0,
                        avgSafetyScore = -10.0,
                        successRate = 0.0,
                        failureRate = 1.0,
                        weak = true,
                    ),
                ),
                roleStepCompliance = listOf(
                    RoleStepCompliance(RoleStep.ACKNOWLEDGE, "Признать ситуацию", timesFollowed = 2, timesSkipped = 0, complianceRate = 1.0),
                    RoleStepCompliance(RoleStep.RULE, "Обозначить правило", timesFollowed = 1, timesSkipped = 1, complianceRate = 0.5),
                    RoleStepCompliance(RoleStep.SOLUTION, "Предложить решение", timesFollowed = 1, timesSkipped = 1, complianceRate = 0.5),
                    RoleStepCompliance(RoleStep.REASSURE, "Заверить", timesFollowed = 0, timesSkipped = 2, complianceRate = 0.0),
                ),
                frequentNormViolations = emptyList(),
                weakCompetencies = listOf("medical"),
                recommendations = emptyList(),
            ),
        )
    }
}
