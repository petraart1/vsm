package ru.vsm.mobile.ui.screens.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.Exam
import ru.vsm.mobile.domain.repository.ExamRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Шаг экрана экзамена (см. `ExamScreen`). */
enum class ExamStep { INTRO, RUNNING, RESULTS }

data class ExamUiState(
    val step: ExamStep = ExamStep.INTRO,
    val loadingIntro: Boolean = true,
    // Intro
    val carClass: CarClass = CarClass.STANDARD,
    val size: Int = 5,
    val creating: Boolean = false,
    val introError: Boolean = false,
    // Running / results
    val exam: Exam? = null,
    val itemStarting: Boolean = false,
    val itemError: Boolean = false,
    val advancing: Boolean = false,
    val advanceError: Boolean = false,
    // navigation side-effect: сценарий текущего пункта, который нужно открыть через Routes.play
    val pendingPlayScenarioId: String? = null,
)

/**
 * Экран экзамена: вводная с параметрами -> прохождение пунктов подряд (каждый пункт открывается
 * отдельным экраном через `Routes.play`, прогресс отслеживается через [ExamRepository.get] при
 * возврате) -> итог с оценкой.
 */
class ExamViewModel(
    private val examRepository: ExamRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExamUiState())
    val state: StateFlow<ExamUiState> = _state.asStateFlow()

    private var playerId: String? = null
    private var currentExamId: String? = null

    init {
        // "Продолжить экзамен" работает только в рамках текущего процесса приложения (id хранится
        // только в памяти этой ViewModel) — упрощение для MVP, без отдельного локального хранилища
        // за пределами этого экрана.
        viewModelScope.launch {
            playerId = playerRepository.getOrCreatePlayerId()
            _state.update { it.copy(loadingIntro = false) }
        }
    }

    fun selectCarClass(carClass: CarClass) = _state.update { it.copy(carClass = carClass) }
    fun selectSize(size: Int) = _state.update { it.copy(size = size) }

    fun begin() {
        val id = playerId ?: return
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, introError = false) }
        viewModelScope.launch {
            examRepository.start(id, _state.value.carClass, _state.value.size).onSuccess { exam ->
                currentExamId = exam.examId
                _state.update { it.copy(creating = false, exam = exam, step = ExamStep.RUNNING) }
                startCurrentItem()
            }.onFailure {
                _state.update { it.copy(creating = false, introError = true) }
            }
        }
    }

    /** Начинает (или продолжает) текущий непройденный пункт и просит экран открыть его прохождение. */
    private fun startCurrentItem() {
        val id = playerId ?: return
        val examId = currentExamId ?: return
        val exam = _state.value.exam ?: return
        if (isFinished(exam)) {
            _state.update { it.copy(step = ExamStep.RESULTS) }
            return
        }
        _state.update { it.copy(itemStarting = true, itemError = false) }
        viewModelScope.launch {
            examRepository.startCurrent(examId, id).onSuccess { progress ->
                _state.update {
                    it.copy(itemStarting = false, pendingPlayScenarioId = progress.scenarioId)
                }
            }.onFailure {
                _state.update { it.copy(itemStarting = false, itemError = true) }
            }
        }
    }

    /** Экран вызывает после того, как открыл экран прохождения — сбрасывает флаг-сигнал. */
    fun onPlayOpened() = _state.update { it.copy(pendingPlayScenarioId = null) }

    /** Экран вызывает при возврате из прохождения пункта (résumé жизненного цикла). */
    fun refreshAfterReturn() {
        val id = playerId ?: return
        val examId = currentExamId ?: return
        if (_state.value.step != ExamStep.RUNNING) return
        if (_state.value.advancing) return
        _state.update { it.copy(advancing = true, advanceError = false) }
        viewModelScope.launch {
            examRepository.get(examId, id).onSuccess { exam ->
                _state.update { it.copy(advancing = false, exam = exam) }
                if (isFinished(exam)) {
                    _state.update { it.copy(step = ExamStep.RESULTS) }
                } else {
                    startCurrentItem()
                }
            }.onFailure {
                _state.update { it.copy(advancing = false, advanceError = true) }
            }
        }
    }

    fun retryStartCurrentItem() = startCurrentItem()

    private fun isFinished(exam: Exam) = exam.status.name == "COMPLETED" || exam.currentIndex >= exam.size
}
