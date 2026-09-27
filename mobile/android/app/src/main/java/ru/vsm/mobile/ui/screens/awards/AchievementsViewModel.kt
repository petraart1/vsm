package ru.vsm.mobile.ui.screens.awards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Состояние экрана каталога наград. */
data class AchievementsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val achievements: List<Achievement> = emptyList(),
) {
    val earnedCount: Int get() = achievements.count { it.earned }
    val byCategory: Map<String, List<Achievement>> get() = achievements.groupBy { it.category }
}

/** Каталог ачивок (включая нестандартные, если backend вернёт их отдельной категорией) — полученные и нет. */
class AchievementsViewModel(
    private val gamificationRepository: GamificationRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val playerId = playerRepository.getOrCreatePlayerId()
            gamificationRepository.getAchievements(playerId).fold(
                onSuccess = { list ->
                    _state.value = AchievementsUiState(loading = false, error = null, achievements = list)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Не удалось загрузить награды")
                },
            )
        }
    }
}
