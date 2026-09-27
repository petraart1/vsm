package ru.vsm.mobile.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.domain.model.ScenarioRecommendation
import ru.vsm.mobile.domain.error.userMessage
import ru.vsm.mobile.domain.repository.FeedbackRepository
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Состояние главного экрана "Сегодня". */
data class TodayUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val playerId: String? = null,
    val profile: Profile? = null,
    val recommended: ScenarioRecommendation? = null,
    val leaderboardTop: List<LeaderboardEntry> = emptyList(),
    val myRank: Long? = null,
)

/**
 * Главный экран: кнопка "Начать смену" (переход в каталог сценариев), рекомендованный сценарий
 * из аналитики компетенций, короткая сводка прогресса и мини-рейтинг.
 */
class TodayViewModel(
    private val gamificationRepository: GamificationRepository,
    private val feedbackRepository: FeedbackRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val playerId = playerRepository.getOrCreatePlayerId()

            val profileResult = gamificationRepository.getProfile(playerId)
            val profile = profileResult.getOrNull()

            val recommended = feedbackRepository.getCompetencies(playerId)
                .getOrNull()
                ?.recommendations
                ?.firstOrNull()

            val leaderboard = gamificationRepository.getLeaderboard(limit = 5, playerId = playerId).getOrNull()

            if (profileResult.isFailure && leaderboard == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = profileResult.exceptionOrNull()?.userMessage() ?: "Не удалось загрузить данные",
                )
                return@launch
            }

            _state.value = TodayUiState(
                loading = false,
                error = null,
                playerId = playerId,
                profile = profile,
                recommended = recommended,
                leaderboardTop = leaderboard?.top.orEmpty(),
                myRank = leaderboard?.me?.rank ?: profile?.leaderboardRank,
            )
        }
    }
}
