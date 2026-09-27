package ru.vsm.mobile.ui.screens.scenarios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.error.isDebriefUnavailableDuringExam
import ru.vsm.mobile.domain.model.Debrief
import ru.vsm.mobile.domain.repository.FeedbackRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Состояние экрана разбора прохождения. */
sealed interface DebriefUiState {
    data object Loading : DebriefUiState

    data class Error(val message: String) : DebriefUiState

    /** Разбор существует, но недоступен, пока не завершён экзамен целиком. */
    data object UnavailableDuringExam : DebriefUiState

    data class Content(val debrief: Debrief) : DebriefUiState
}

/** Загружает обучающий разбор одного завершённого прохождения. Портирован с `frontend/src/screens/Debrief.jsx`. */
class DebriefViewModel(
    private val progressId: String,
    private val feedbackRepository: FeedbackRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<DebriefUiState>(DebriefUiState.Loading)
    val state: StateFlow<DebriefUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = DebriefUiState.Loading
        viewModelScope.launch {
            val playerId = playerRepository.getOrCreatePlayerId()
            feedbackRepository.getDebrief(progressId, playerId).fold(
                onSuccess = { debrief -> _state.value = DebriefUiState.Content(debrief) },
                onFailure = { error ->
                    _state.value = if (error is DomainError.Api && error.isDebriefUnavailableDuringExam()) {
                        DebriefUiState.UnavailableDuringExam
                    } else {
                        DebriefUiState.Error(messageFor(error))
                    }
                },
            )
        }
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is DomainError.Network -> "Нет связи с сервером тренажёра. Проверьте подключение и повторите."
        is DomainError.Api -> error.message ?: "Сервер тренажёра вернул ошибку."
        else -> "Не удалось загрузить разбор прохождения."
    }
}
