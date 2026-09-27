package ru.vsm.mobile.ui.screens.shift

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.domain.repository.PlayerRepository
import ru.vsm.mobile.domain.repository.ScenarioRepository

enum class ShiftStage { SETUP, PRE_SHIFT, TRIP, SUMMARY }

/** Состояние одного пройденного пункта рейса — упрощённый таймлайн вместо анимации вагона. */
data class TripItem(
    val scenario: ScenarioSummary,
    val opened: Boolean = false,
    val done: Boolean = false,
)

data class ShiftUiState(
    val stage: ShiftStage = ShiftStage.SETUP,
    val carClass: CarClass = CarClass.STANDARD,
    // Заступ на смену
    val preShiftSteps: List<PreShiftStep> = emptyList(),
    val preShiftIndex: Int = 0,
    val preShiftFeedback: PreShiftChoice? = null,
    val preShiftSafety: Int = 0,
    val preShiftLoyalty: Int = 0,
    // Рейс
    val tripLoading: Boolean = false,
    val tripError: Boolean = false,
    val tripItems: List<TripItem> = emptyList(),
    val tripIndex: Int = 0,
    val pendingPlayScenarioId: String? = null,
)

/**
 * Упрощённая «Смена»: заступ (чек-лист медосмотра и инструктажа) -> серия сценариев рейса из
 * каталога backend, каждый открывается отдельным экраном прохождения -> итог смены. Без анимации
 * вагона — таймлайн карточек.
 */
class ShiftViewModel(
    private val scenarioRepository: ScenarioRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShiftUiState())
    val state: StateFlow<ShiftUiState> = _state.asStateFlow()

    private var playerId: String? = null

    init {
        viewModelScope.launch { playerId = playerRepository.getOrCreatePlayerId() }
    }

    fun selectCarClass(carClass: CarClass) = _state.update { it.copy(carClass = carClass) }

    fun startShift() {
        val steps = buildPreShiftSteps(_state.value.carClass)
        _state.update {
            it.copy(
                stage = ShiftStage.PRE_SHIFT,
                preShiftSteps = steps,
                preShiftIndex = 0,
                preShiftFeedback = null,
                preShiftSafety = 0,
                preShiftLoyalty = 0,
            )
        }
    }

    fun answerPreShift(choice: PreShiftChoice) {
        _state.update {
            it.copy(
                preShiftFeedback = choice,
                preShiftSafety = it.preShiftSafety + choice.safetyDelta,
                preShiftLoyalty = it.preShiftLoyalty + choice.loyaltyDelta,
            )
        }
    }

    fun nextPreShiftStep() {
        val s = _state.value
        val nextIndex = s.preShiftIndex + 1
        if (nextIndex >= s.preShiftSteps.size) {
            loadTrip()
        } else {
            _state.update { it.copy(preShiftIndex = nextIndex, preShiftFeedback = null) }
        }
    }

    private fun loadTrip() {
        _state.update { it.copy(stage = ShiftStage.TRIP, tripLoading = true, tripError = false) }
        viewModelScope.launch {
            scenarioRepository.list(null).onSuccess { all ->
                val picked = all.shuffled().take(3)
                _state.update {
                    it.copy(
                        tripLoading = false,
                        tripItems = picked.map { s -> TripItem(scenario = s) },
                        tripIndex = 0,
                    )
                }
            }.onFailure {
                _state.update { it.copy(tripLoading = false, tripError = true) }
            }
        }
    }

    fun retryLoadTrip() = loadTrip()

    fun openCurrentTripItem() {
        val s = _state.value
        val item = s.tripItems.getOrNull(s.tripIndex) ?: return
        _state.update {
            it.copy(
                pendingPlayScenarioId = item.scenario.id,
                tripItems = it.tripItems.mapIndexed { i, ti -> if (i == it.tripIndex) ti.copy(opened = true) else ti },
            )
        }
    }

    fun onPlayOpened() = _state.update { it.copy(pendingPlayScenarioId = null) }

    /** Возврат из прохождения пункта рейса — считаем открытый пункт пройденным и переходим к следующему. */
    fun onReturnedFromPlay() {
        val s = _state.value
        if (s.stage != ShiftStage.TRIP) return
        val item = s.tripItems.getOrNull(s.tripIndex) ?: return
        if (!item.opened || item.done) return
        val updatedItems = s.tripItems.mapIndexed { i, ti -> if (i == s.tripIndex) ti.copy(done = true) else ti }
        val nextIndex = s.tripIndex + 1
        if (nextIndex >= updatedItems.size) {
            _state.update { it.copy(tripItems = updatedItems, stage = ShiftStage.SUMMARY) }
        } else {
            _state.update { it.copy(tripItems = updatedItems, tripIndex = nextIndex) }
        }
    }
}
