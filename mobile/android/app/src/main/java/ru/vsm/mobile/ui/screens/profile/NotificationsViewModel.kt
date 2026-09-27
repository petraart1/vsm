package ru.vsm.mobile.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.error.userMessage
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

/** Состояние экрана уведомлений. */
data class NotificationsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val playerId: String? = null,
    val notifications: List<Notification> = emptyList(),
) {
    val unreadCount: Int get() = notifications.count { it.unread }
}

/** Список уведомлений игрока: отметить одно/все прочитанными. */
class NotificationsViewModel(
    private val gamificationRepository: GamificationRepository,
    private val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val playerId = playerRepository.getOrCreatePlayerId()
            gamificationRepository.getNotifications(playerId).fold(
                onSuccess = { list ->
                    _state.value = NotificationsUiState(loading = false, error = null, playerId = playerId, notifications = list)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.userMessage())
                },
            )
        }
    }

    fun markRead(notificationId: String) {
        val playerId = _state.value.playerId ?: return
        viewModelScope.launch {
            gamificationRepository.markRead(notificationId, playerId).onSuccess { updated ->
                _state.value = _state.value.copy(
                    notifications = _state.value.notifications.map { if (it.id == updated.id) updated else it },
                )
            }
        }
    }

    fun markAllRead() {
        val playerId = _state.value.playerId ?: return
        viewModelScope.launch {
            gamificationRepository.markAllRead(playerId).onSuccess {
                load()
            }
        }
    }
}
