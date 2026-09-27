package ru.vsm.mobile.ui.screens.awards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.CustomAward
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Порядок категорий каталога ачивок (см. `AchievementCode` на сервере) для стабильного отображения. */
val ACHIEVEMENT_CATEGORY_ORDER = listOf("milestone", "volume", "style", "challenge")

/** Состояние экрана каталога наград. */
data class AchievementsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val achievements: List<Achievement> = emptyList(),
    val customAwards: List<CustomAward> = emptyList(),
) {
    val earnedCount: Int get() = achievements.count { it.earned }
    val earnedCustomCount: Int get() = customAwards.count { it.earned }
    val byCategory: Map<String, List<Achievement>>
        get() {
            val grouped = achievements.groupBy { it.category }
            val ordered = LinkedHashMap<String, List<Achievement>>()
            ACHIEVEMENT_CATEGORY_ORDER.forEach { key -> grouped[key]?.let { ordered[key] = it } }
            grouped.keys.filter { it !in ACHIEVEMENT_CATEGORY_ORDER }.forEach { key -> ordered[key] = grouped.getValue(key) }
            return ordered
        }
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
            val custom = gamificationRepository.getCustomAwards(playerId).getOrNull().orEmpty()
            gamificationRepository.getAchievements(playerId).fold(
                onSuccess = { list ->
                    _state.value = AchievementsUiState(loading = false, error = null, achievements = list, customAwards = custom)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Не удалось загрузить награды")
                },
            )
        }
    }
}
