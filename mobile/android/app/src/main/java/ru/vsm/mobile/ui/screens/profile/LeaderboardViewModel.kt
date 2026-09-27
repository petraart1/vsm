package ru.vsm.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.domain.model.Team
import ru.vsm.mobile.domain.model.TeamLeaderboardEntry
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Вкладка рейтинга. */
enum class LeaderboardTab { PLAYERS, TEAMS }

/** Состояние экрана рейтинга — проводники и бригады. */
data class LeaderboardUiState(
    val tab: LeaderboardTab = LeaderboardTab.PLAYERS,
    val loading: Boolean = true,
    val error: String? = null,
    val playerId: String? = null,
    val myLevel: Int? = null,
    val playersTop: List<LeaderboardEntry> = emptyList(),
    val myEntry: LeaderboardEntry? = null,
    val teamsTop: List<TeamLeaderboardEntry> = emptyList(),
    val myTeamId: String? = null,
    val availableTeams: List<Team> = emptyList(),
    val joiningTeamId: String? = null,
)

/**
 * Рейтинг: вкладка "Проводники" (топ + закреплённая карточка "Ваше место") и "Бригады" (рейтинг
 * команд по среднему баллу + вступление в команду, если игрок ещё не в команде).
 */
class LeaderboardViewModel(
    private val gamificationRepository: GamificationRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun selectTab(tab: LeaderboardTab) {
        _state.value = _state.value.copy(tab = tab)
        if (tab == LeaderboardTab.TEAMS && _state.value.teamsTop.isEmpty()) {
            loadTeams()
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val playerId = playerRepository.getOrCreatePlayerId()
            val profile = gamificationRepository.getProfile(playerId).getOrNull()
            gamificationRepository.getLeaderboard(limit = 20, playerId = playerId).fold(
                onSuccess = { board ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = null,
                        playerId = playerId,
                        myLevel = profile?.level,
                        playersTop = board.top,
                        myEntry = board.me,
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Не удалось загрузить рейтинг")
                },
            )
            if (_state.value.tab == LeaderboardTab.TEAMS) loadTeams()
        }
    }

    private fun loadTeams() {
        viewModelScope.launch {
            val playerId = _state.value.playerId ?: playerRepository.getOrCreatePlayerId()
            val teams = gamificationRepository.getTeamLeaderboard(limit = 20, playerId = playerId).getOrNull().orEmpty()
            val available = if (teams.none { it.teamId == _state.value.myTeamId }) {
                gamificationRepository.listTeams().getOrNull().orEmpty()
            } else {
                emptyList()
            }
            _state.value = _state.value.copy(teamsTop = teams, availableTeams = available)
        }
    }

    fun joinTeam(teamId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(joiningTeamId = teamId)
            val playerId = _state.value.playerId ?: playerRepository.getOrCreatePlayerId()
            gamificationRepository.joinTeam(teamId, playerId).onSuccess {
                _state.value = _state.value.copy(myTeamId = teamId)
                loadTeams()
            }
            _state.value = _state.value.copy(joiningTeamId = null)
        }
    }
}
