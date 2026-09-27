package ru.vsm.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.error.userMessage
import ru.vsm.mobile.domain.model.Challenge
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Состояние экрана челленджей месяца. */
data class ChallengesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val challenges: List<Challenge> = emptyList(),
)

/** Активные челленджи месяца с прогрессом игрока (см. [Challenge]). */
class ChallengesViewModel(
    private val gamificationRepository: GamificationRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChallengesUiState())
    val state: StateFlow<ChallengesUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val playerId = playerRepository.getOrCreatePlayerId()
            gamificationRepository.getChallenges(playerId).fold(
                onSuccess = { list ->
                    _state.value = ChallengesUiState(loading = false, error = null, challenges = list)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.userMessage())
                },
            )
        }
    }
}
