package ru.vsm.mobile.ui.screens.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.ChoiceOption
import ru.vsm.mobile.domain.model.ChoiceOutcome
import ru.vsm.mobile.domain.model.ChoiceResult
import ru.vsm.mobile.domain.model.LiveProgressEvent
import ru.vsm.mobile.domain.model.LiveProgressState
import ru.vsm.mobile.domain.model.NodeType
import ru.vsm.mobile.domain.model.ProgressStatus
import ru.vsm.mobile.domain.model.ScenarioNode
import ru.vsm.mobile.domain.repository.PlayerRepository
import ru.vsm.mobile.domain.repository.ScenarioRepository

/** Одна запись в переписке проводника с пассажиром на экране прохождения. */
sealed interface ChatMessage {
    val id: String

    /** Реплика/ситуация текущего узла графа. */
    data class Passenger(override val id: String, val text: String, val escalation: Boolean) : ChatMessage

    /** Выбор, который сделал игрок. */
    data class Conductor(override val id: String, val text: String) : ChatMessage

    /** Служебная строка: эффект выбора на шкалы (пусто, если разбор скрыт — режим экзамена). */
    data class Note(
        override val id: String,
        val loyaltyDelta: Int?,
        val safetyDelta: Int?,
        val wasTimeout: Boolean,
    ) : ChatMessage

    /** Итог терминального узла. */
    data class Outcome(override val id: String, val text: String) : ChatMessage
}

/** Состояние экрана прохождения сценария. */
sealed interface ScenarioPlayUiState {
    data object Loading : ScenarioPlayUiState

    data class Error(val message: String) : ScenarioPlayUiState

    data class Content(
        val progressId: String,
        val scenarioCode: String,
        val messages: List<ChatMessage>,
        val choices: List<ChoiceOption>,
        val awaitingChoice: Boolean,
        val submitting: Boolean,
        /** `null` в режиме экзамена — шкалы не раскрываются по ходу прохождения. */
        val loyaltyScore: Int?,
        val safetyScore: Int?,
        val timerTotalSeconds: Int?,
        val timerRemainingSeconds: Int?,
        val offlinePending: Int,
        val finished: Boolean,
    ) : ScenarioPlayUiState
}

/**
 * Ведёт одно прохождение сценария: держит переписку как список сообщений, таймер текущего узла
 * (локальный отсчёт + живой канал, если сервер его отдаёт) и офлайн-устойчивую отправку выбора.
 * Портирован с `frontend/src/components/dialog/useScenarioDialog.js`.
 */
class ScenarioPlayViewModel(
    private val scenarioId: String,
    private val scenarioRepository: ScenarioRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScenarioPlayUiState>(ScenarioPlayUiState.Loading)
    val state: StateFlow<ScenarioPlayUiState> = _state.asStateFlow()

    private var playerId: String? = null
    private var countdownJob: Job? = null
    private var liveJob: Job? = null
    private var timeoutInFlight = false

    init {
        start()
    }

    fun start() {
        _state.value = ScenarioPlayUiState.Loading
        viewModelScope.launch {
            val pid = playerRepository.getOrCreatePlayerId()
            playerId = pid

            scenarioRepository.start(scenarioId, pid).fold(
                onSuccess = { progress ->
                    val node = progress.currentNode
                    val messages = if (node != null) listOf(nodeToMessage(node)) else emptyList()
                    _state.value = ScenarioPlayUiState.Content(
                        progressId = progress.progressId,
                        scenarioCode = progress.scenarioCode,
                        messages = messages,
                        choices = node?.choices.orEmpty(),
                        awaitingChoice = node != null && !node.terminal,
                        submitting = false,
                        loyaltyScore = progress.loyaltyScore,
                        safetyScore = progress.safetyScore,
                        timerTotalSeconds = node?.timerSeconds,
                        timerRemainingSeconds = null,
                        offlinePending = 0,
                        finished = node == null || node.terminal,
                    )
                    node?.let(::startTimerFor)
                    collectPendingOfflineCount()
                    collectLiveEvents(progress.progressId, pid)
                },
                onFailure = { error -> _state.value = ScenarioPlayUiState.Error(messageFor(error)) },
            )
        }
    }

    fun choose(choiceId: String) {
        val current = _state.value as? ScenarioPlayUiState.Content ?: return
        if (!current.awaitingChoice || current.submitting) return
        val pid = playerId ?: return
        val choiceText = current.choices.firstOrNull { it.id == choiceId }?.text ?: return

        countdownJob?.cancel()
        _state.update { s -> (s as? ScenarioPlayUiState.Content)?.copy(submitting = true, awaitingChoice = false) ?: s }

        viewModelScope.launch {
            scenarioRepository.chooseOrQueue(current.progressId, choiceId, pid).fold(
                onSuccess = { outcome -> applyOutcome(outcome, choiceText, wasTimeout = false) },
                onFailure = { error ->
                    _state.update { s ->
                        (s as? ScenarioPlayUiState.Content)?.copy(submitting = false, awaitingChoice = true) ?: s
                    }
                    // Ошибка (не сетевая — сеть уходит в офлайн-очередь и не считается ошибкой):
                    // возвращаем экран в состояние выбора, чтобы игрок мог повторить.
                    if (error !is DomainError.Network) {
                        // ничего специального сверх возврата к выбору — сообщение видно в логах API.
                    }
                },
            )
        }
    }

    private fun onTimeout() {
        val current = _state.value as? ScenarioPlayUiState.Content ?: return
        if (current.finished || timeoutInFlight) return
        val pid = playerId ?: return
        timeoutInFlight = true
        _state.update { s -> (s as? ScenarioPlayUiState.Content)?.copy(submitting = true, awaitingChoice = false) ?: s }

        viewModelScope.launch {
            scenarioRepository.timeoutOrQueue(current.progressId, pid).fold(
                onSuccess = { outcome -> applyOutcome(outcome, choiceText = null, wasTimeout = true) },
                onFailure = {
                    _state.update { s ->
                        (s as? ScenarioPlayUiState.Content)?.copy(submitting = false, awaitingChoice = true) ?: s
                    }
                },
            )
            timeoutInFlight = false
        }
    }

    private fun applyOutcome(outcome: ChoiceOutcome, choiceText: String?, wasTimeout: Boolean) {
        when (outcome) {
            is ChoiceOutcome.Applied -> applyChoiceResult(outcome.result, choiceText)
            is ChoiceOutcome.QueuedOffline -> {
                _state.update { s ->
                    (s as? ScenarioPlayUiState.Content)?.let { c ->
                        val extra = mutableListOf<ChatMessage>()
                        if (choiceText != null) extra += ChatMessage.Conductor(id = "queued-${outcome.queuedAt}", text = choiceText)
                        c.copy(
                            messages = c.messages + extra,
                            submitting = false,
                            awaitingChoice = false,
                            offlinePending = c.offlinePending + 1,
                        )
                    } ?: s
                }
            }
        }
    }

    private fun applyChoiceResult(result: ChoiceResult, choiceText: String?) {
        _state.update { s ->
            val current = s as? ScenarioPlayUiState.Content ?: return@update s
            val extra = mutableListOf<ChatMessage>()
            if (choiceText != null) extra += ChatMessage.Conductor(id = "choice-${result.appliedChoiceId}", text = choiceText)
            extra += ChatMessage.Note(
                id = "note-${result.appliedChoiceId}-${result.status}",
                loyaltyDelta = result.loyaltyDelta,
                safetyDelta = result.safetyDelta,
                wasTimeout = result.wasTimeout,
            )
            val node = result.nextNode
            if (node != null) extra += nodeToMessage(node)
            if (result.status == ProgressStatus.COMPLETED && result.finalOutcome != null && node == null) {
                extra += ChatMessage.Outcome(id = "outcome-${result.progressId}", text = outcomeLabel(result.finalOutcome))
            }

            current.copy(
                messages = current.messages + extra,
                choices = node?.choices.orEmpty(),
                awaitingChoice = node != null && !node.terminal,
                submitting = false,
                loyaltyScore = result.loyaltyScore ?: current.loyaltyScore,
                safetyScore = result.safetyScore ?: current.safetyScore,
                timerTotalSeconds = node?.timerSeconds,
                timerRemainingSeconds = null,
                finished = result.status == ProgressStatus.COMPLETED || node == null,
            )
        }
        (_state.value as? ScenarioPlayUiState.Content)?.let { c ->
            if (c.finished) {
                countdownJob?.cancel()
                liveJob?.cancel()
            } else {
                result.nextNode?.let(::startTimerFor)
            }
        }
    }

    private fun collectLiveEvents(progressId: String, pid: String) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            scenarioRepository.liveEvents(progressId, pid).collect { event ->
                when (event) {
                    is LiveProgressEvent.Tick -> {
                        _state.update { s ->
                            (s as? ScenarioPlayUiState.Content)?.copy(timerRemainingSeconds = event.secondsRemaining) ?: s
                        }
                    }
                    is LiveProgressEvent.Timeout -> applyLiveState(event.state, serverTimeout = true)
                    is LiveProgressEvent.State -> applyLiveState(event.state, serverTimeout = false)
                    is LiveProgressEvent.Completed -> applyLiveState(event.state, serverTimeout = false)
                    LiveProgressEvent.Disconnected, LiveProgressEvent.Reconnected -> Unit
                }
            }
        }
    }

    /** Сервер применил выбор по умолчанию сам (истёк дедлайн) — синхронизируем локальное состояние. */
    private fun applyLiveState(liveState: LiveProgressState, serverTimeout: Boolean) {
        if (!serverTimeout || liveState.appliedChoiceId == null) return
        countdownJob?.cancel()
        val syntheticResult = ChoiceResult(
            progressId = liveState.progressId,
            appliedChoiceId = liveState.appliedChoiceId,
            appliedChoiceCode = liveState.appliedChoiceCode.orEmpty(),
            wasTimeout = true,
            loyaltyDelta = liveState.loyaltyDelta,
            safetyDelta = liveState.safetyDelta,
            loyaltyScore = liveState.loyaltyScore,
            safetyScore = liveState.safetyScore,
            status = liveState.status,
            finalOutcome = liveState.finalOutcome,
            nextNode = liveState.currentNode,
        )
        applyChoiceResult(syntheticResult, choiceText = null)
    }

    private fun collectPendingOfflineCount() {
        viewModelScope.launch {
            scenarioRepository.pendingOfflineCount().collect { count ->
                _state.update { s -> (s as? ScenarioPlayUiState.Content)?.copy(offlinePending = count) ?: s }
            }
        }
    }

    private fun startTimerFor(node: ScenarioNode) {
        countdownJob?.cancel()
        val remaining = remainingSecondsFor(node) ?: run {
            _state.update { s -> (s as? ScenarioPlayUiState.Content)?.copy(timerRemainingSeconds = null) ?: s }
            return
        }
        _state.update { s -> (s as? ScenarioPlayUiState.Content)?.copy(timerRemainingSeconds = remaining) ?: s }
        countdownJob = viewModelScope.launch {
            var left = remaining
            while (left > 0) {
                delay(1_000)
                left -= 1
                _state.update { s -> (s as? ScenarioPlayUiState.Content)?.copy(timerRemainingSeconds = left) ?: s }
            }
            onTimeout()
        }
    }

    private fun remainingSecondsFor(node: ScenarioNode): Int? {
        val deadline = node.deadlineAt
        if (deadline != null) {
            return try {
                val seconds = Duration.between(Instant.now(), Instant.parse(deadline)).seconds
                if (seconds < 0) 0 else seconds.toInt()
            } catch (e: Exception) {
                node.timerSeconds
            }
        }
        return node.timerSeconds
    }

    private fun nodeToMessage(node: ScenarioNode): ChatMessage.Passenger =
        ChatMessage.Passenger(id = node.nodeId, text = node.text, escalation = node.type == NodeType.ESCALATION)

    private fun outcomeLabel(outcome: ru.vsm.mobile.domain.model.ScenarioOutcome): String = when (outcome) {
        ru.vsm.mobile.domain.model.ScenarioOutcome.SUCCESS -> "Ситуация решена успешно."
        ru.vsm.mobile.domain.model.ScenarioOutcome.PARTIAL -> "Решение принято, но не идеально."
        ru.vsm.mobile.domain.model.ScenarioOutcome.FAILURE -> "Ситуация решена неудачно."
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is DomainError.Network -> "Нет связи с сервером тренажёра. Проверьте подключение и повторите."
        is DomainError.Api -> error.message ?: "Сервер тренажёра вернул ошибку."
        else -> "Не удалось начать прохождение."
    }

    override fun onCleared() {
        countdownJob?.cancel()
        liveJob?.cancel()
        super.onCleared()
    }
}
