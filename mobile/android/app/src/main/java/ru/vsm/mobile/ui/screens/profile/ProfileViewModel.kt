package ru.vsm.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.domain.repository.FeedbackRepository
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Состояние экрана профиля игрока. */
data class ProfileUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val profile: Profile? = null,
    val competencies: CompetencyAnalytics? = null,
)

/**
 * Профиль: уровень/звание с прогрессом, очки, прогресс по блокам, аналитика компетенций
 * (просевшие компетенции + рекомендованные сценарии).
 */
class ProfileViewModel(
    private val gamificationRepository: GamificationRepository,
    private val feedbackRepository: FeedbackRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val playerId = playerRepository.getOrCreatePlayerId()

            val profileResult = gamificationRepository.getProfile(playerId)
            val competencies = feedbackRepository.getCompetencies(playerId).getOrNull()

            profileResult.fold(
                onSuccess = { profile ->
                    _state.value = ProfileUiState(loading = false, error = null, profile = profile, competencies = competencies)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Не удалось загрузить профиль")
                },
            )
        }
    }
}
