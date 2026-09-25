package ru.vsm.mobile.domain.fake

import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.ChoiceOption
import ru.vsm.mobile.domain.model.ChoiceResult
import ru.vsm.mobile.domain.model.LiveProgressEvent
import ru.vsm.mobile.domain.model.LiveProgressState
import ru.vsm.mobile.domain.model.NodeType
import ru.vsm.mobile.domain.model.ProgressStatus
import ru.vsm.mobile.domain.model.ScenarioNode
import ru.vsm.mobile.domain.model.ScenarioOutcome
import ru.vsm.mobile.domain.model.ScenarioProgress
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.domain.repository.ScenarioRepository

/**
 * Фейковая реализация для UI-слоя до готовности сетевого data-слоя. Два маленьких сценария в
 * памяти: один с таймером на первом узле (для проверки экрана таймера), один без. Каждое
 * прохождение — 1-2 развилки до терминального узла.
 */
class FakeScenarioRepository : ScenarioRepository {

    private data class FakeChoice(
        val option: ChoiceOption,
        val loyaltyDelta: Int,
        val safetyDelta: Int,
        val target: FakeNode?,
    )

    private data class FakeNode(
        val node: ScenarioNode,
        val choicesById: Map<String, FakeChoice>,
        val defaultChoiceId: String?,
    )

    private data class FakeScenario(
        val summary: ScenarioSummary,
        val entry: FakeNode,
    )

    private data class ActiveProgress(
        var progress: ScenarioProgress,
        val scenario: FakeScenario,
        var currentFakeNode: FakeNode,
    )

    private val scenarios: List<FakeScenario> = buildFakeScenarios()
    private val progressById = mutableMapOf<String, ActiveProgress>()

    override suspend fun list(block: String?): Result<List<ScenarioSummary>> {
        delay(200)
        val summaries = scenarios.map { it.summary }.filter { block == null || it.block == block }
        return Result.success(summaries)
    }

    override suspend fun start(scenarioId: String, playerId: String): Result<ScenarioProgress> {
        delay(200)
        val scenario = scenarios.find { it.summary.id == scenarioId }
            ?: return apiError(404, DomainError.SCENARIO_NOT_FOUND, "Сценарий '$scenarioId' не найден")

        val progressId = UUID.randomUUID().toString()
        val progress = ScenarioProgress(
            progressId = progressId,
            scenarioId = scenario.summary.id,
            scenarioCode = scenario.summary.code,
            status = ProgressStatus.IN_PROGRESS,
            loyaltyScore = 0,
            safetyScore = 0,
            currentNode = scenario.entry.node,
        )
        progressById[progressId] = ActiveProgress(progress, scenario, scenario.entry)
        return Result.success(progress)
    }

    override suspend fun getProgress(progressId: String, playerId: String): Result<ScenarioProgress> {
        delay(150)
        val active = progressById[progressId]
            ?: return apiError(404, DomainError.PROGRESS_NOT_FOUND, "Прохождение '$progressId' не найдено")
        return Result.success(active.progress)
    }

    override suspend fun choose(progressId: String, choiceId: String, playerId: String): Result<ChoiceResult> {
        delay(250)
        val active = progressById[progressId]
            ?: return apiError(404, DomainError.PROGRESS_NOT_FOUND, "Прохождение '$progressId' не найдено")
        if (active.progress.status != ProgressStatus.IN_PROGRESS) {
            return apiError(409, DomainError.PROGRESS_ALREADY_COMPLETED, "Прохождение уже завершено")
        }
        val fakeChoice = active.currentFakeNode.choicesById[choiceId]
            ?: return apiError(400, DomainError.CHOICE_NOT_AVAILABLE, "Выбор '$choiceId' недоступен в текущем узле")

        return Result.success(applyChoice(active, fakeChoice, wasTimeout = false))
    }

    override suspend fun timeout(progressId: String, playerId: String): Result<ChoiceResult> {
        delay(150)
        val active = progressById[progressId]
            ?: return apiError(404, DomainError.PROGRESS_NOT_FOUND, "Прохождение '$progressId' не найдено")
        if (active.progress.status != ProgressStatus.IN_PROGRESS) {
            return apiError(409, DomainError.PROGRESS_ALREADY_COMPLETED, "Прохождение уже завершено")
        }
        val defaultId = active.currentFakeNode.defaultChoiceId
            ?: return apiError(400, DomainError.NO_ACTIVE_TIMER, "У текущего узла нет таймера")
        val fakeChoice = active.currentFakeNode.choicesById.getValue(defaultId)
        return Result.success(applyChoice(active, fakeChoice, wasTimeout = true))
    }

    /**
     * Упрощённая фейковая имитация живого канала: раз в секунду тикает оставшееся время текущего
     * узла (если у него есть таймер), реального push от [choose]/[timeout] не делает — B опрашивает
     * [getProgress] после ответа REST, как и в реальном клиенте, когда WS недоступен.
     */
    override fun liveEvents(progressId: String, playerId: String): Flow<LiveProgressEvent> = flow {
        val active = progressById[progressId] ?: return@flow
        val timerSeconds = active.currentFakeNode.node.timerSeconds ?: return@flow
        var remaining = timerSeconds
        while (remaining >= 0) {
            emit(LiveProgressEvent.Tick(progressId, remaining))
            delay(1000)
            remaining--
        }
    }

    private fun applyChoice(active: ActiveProgress, fakeChoice: FakeChoice, wasTimeout: Boolean): ChoiceResult {
        val newLoyalty = active.progress.loyaltyScore + fakeChoice.loyaltyDelta
        val newSafety = active.progress.safetyScore + fakeChoice.safetyDelta
        val nextFakeNode = fakeChoice.target
        val nextNode = nextFakeNode?.node
        val completed = nextFakeNode == null || nextFakeNode.node.terminal
        val status = if (completed) ProgressStatus.COMPLETED else ProgressStatus.IN_PROGRESS
        val finalOutcome = if (completed) (nextFakeNode?.node?.terminalOutcome ?: ScenarioOutcome.PARTIAL) else null

        active.progress = active.progress.copy(
            status = status,
            loyaltyScore = newLoyalty,
            safetyScore = newSafety,
            currentNode = nextNode,
        )
        if (nextFakeNode != null) {
            active.currentFakeNode = nextFakeNode
        }

        return ChoiceResult(
            progressId = active.progress.progressId,
            appliedChoiceId = fakeChoice.option.id,
            appliedChoiceCode = fakeChoice.option.code,
            wasTimeout = wasTimeout,
            loyaltyDelta = fakeChoice.loyaltyDelta,
            safetyDelta = fakeChoice.safetyDelta,
            loyaltyScore = newLoyalty,
            safetyScore = newSafety,
            status = status,
            finalOutcome = finalOutcome,
            nextNode = nextNode,
        )
    }

    private fun <T> apiError(statusCode: Int, errorCode: String, message: String): Result<T> =
        Result.failure(DomainError.Api(statusCode, errorCode, message))

    companion object {
        @Suppress("LongMethod")
        private fun buildFakeScenarios(): List<FakeScenario> {
            val successNode = FakeNode(
                node = ScenarioNode(
                    nodeId = "node-boarding-success",
                    code = "success",
                    type = NodeType.TERMINAL,
                    text = "Пассажир доволен, порядок посадки соблюдён.",
                    terminal = true,
                    timerSeconds = null,
                    deadlineAt = null,
                    terminalOutcome = ScenarioOutcome.SUCCESS,
                    outcomeSummary = "Оба показателя выросли — образцовое решение.",
                    choices = emptyList(),
                ),
                choicesById = emptyMap(),
                defaultChoiceId = null,
            )
            val failureNode = FakeNode(
                node = ScenarioNode(
                    nodeId = "node-boarding-failure",
                    code = "failure",
                    type = NodeType.TERMINAL,
                    text = "Пассажир прошёл без проверки — риск для безопасности.",
                    terminal = true,
                    timerSeconds = null,
                    deadlineAt = null,
                    terminalOutcome = ScenarioOutcome.FAILURE,
                    outcomeSummary = "Норма посадки нарушена.",
                    choices = emptyList(),
                ),
                choicesById = emptyMap(),
                defaultChoiceId = null,
            )
            val boardingChoiceOk = ChoiceOption("choice-boarding-ok", "check-ticket", "Вежливо попросить предъявить билет")
            val boardingChoiceSkip = ChoiceOption("choice-boarding-skip", "let-through", "Пропустить без проверки")
            val boardingEntry = FakeNode(
                node = ScenarioNode(
                    nodeId = "node-boarding-start",
                    code = "start",
                    type = NodeType.DIALOGUE,
                    text = "У турникета посадки пассажир торопится и не предъявляет билет.",
                    terminal = false,
                    timerSeconds = null,
                    deadlineAt = null,
                    terminalOutcome = null,
                    outcomeSummary = null,
                    choices = listOf(boardingChoiceOk, boardingChoiceSkip),
                ),
                choicesById = mapOf(
                    boardingChoiceOk.id to FakeChoice(boardingChoiceOk, loyaltyDelta = 2, safetyDelta = 5, target = successNode),
                    boardingChoiceSkip.id to FakeChoice(boardingChoiceSkip, loyaltyDelta = 3, safetyDelta = -8, target = failureNode),
                ),
                defaultChoiceId = boardingChoiceOk.id,
            )
            val boardingScenario = FakeScenario(
                summary = ScenarioSummary(
                    id = "scenario-boarding-no-ticket",
                    code = "boarding-no-ticket",
                    situationRef = 2,
                    block = "boarding",
                    title = "Посадка без билета",
                    description = "Пассажир пытается пройти на посадку, не предъявив билет.",
                    flagship = false,
                ),
                entry = boardingEntry,
            )

            val medicalSuccess = FakeNode(
                node = ScenarioNode(
                    nodeId = "node-medical-success",
                    code = "success",
                    type = NodeType.TERMINAL,
                    text = "Медицинская помощь вызвана вовремя, пассажирка успокоилась.",
                    terminal = true,
                    timerSeconds = null,
                    deadlineAt = null,
                    terminalOutcome = ScenarioOutcome.SUCCESS,
                    outcomeSummary = "Экстренная ситуация разрешена по регламенту.",
                    choices = emptyList(),
                ),
                choicesById = emptyMap(),
                defaultChoiceId = null,
            )
            val medicalPartial = FakeNode(
                node = ScenarioNode(
                    nodeId = "node-medical-partial",
                    code = "partial",
                    type = NodeType.TERMINAL,
                    text = "Помощь подоспела с задержкой из-за нерешительности.",
                    terminal = true,
                    timerSeconds = null,
                    deadlineAt = null,
                    terminalOutcome = ScenarioOutcome.PARTIAL,
                    outcomeSummary = "Решение верное, но принято поздно.",
                    choices = emptyList(),
                ),
                choicesById = emptyMap(),
                defaultChoiceId = null,
            )
            val medicalCall = ChoiceOption("choice-medical-call", "call-chief", "Немедленно вызвать начальника поезда и медиков")
            val medicalWait = ChoiceOption("choice-medical-wait", "wait-and-see", "Подождать, вдруг само пройдёт")
            val medicalEntry = FakeNode(
                node = ScenarioNode(
                    nodeId = "node-medical-start",
                    code = "start",
                    type = NodeType.DIALOGUE,
                    text = "Пассажирке внезапно стало плохо, она бледна и просит о помощи.",
                    terminal = false,
                    timerSeconds = 20,
                    deadlineAt = null,
                    terminalOutcome = null,
                    outcomeSummary = null,
                    choices = listOf(medicalCall, medicalWait),
                ),
                choicesById = mapOf(
                    medicalCall.id to FakeChoice(medicalCall, loyaltyDelta = 5, safetyDelta = 8, target = medicalSuccess),
                    medicalWait.id to FakeChoice(medicalWait, loyaltyDelta = -5, safetyDelta = -10, target = medicalPartial),
                ),
                defaultChoiceId = medicalWait.id,
            )
            val medicalScenario = FakeScenario(
                summary = ScenarioSummary(
                    id = "scenario-medical-unwell",
                    code = "medical-passenger-unwell",
                    situationRef = 1,
                    block = "medical",
                    title = "Пассажиру стало плохо",
                    description = "Экстренная ситуация с таймером принятия решения.",
                    flagship = true,
                ),
                entry = medicalEntry,
            )

            return listOf(boardingScenario, medicalScenario)
        }
    }
}
