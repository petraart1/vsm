package ru.vsm.mobile.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.Notification
import ru.vsm.mobile.domain.model.NotificationType
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.navigation.AppNavigator

/** Иконка для каждого типа уведомления геймификации, fallback — обычный колокольчик. */
private fun iconFor(type: NotificationType): ImageVector = when (type) {
    NotificationType.ACHIEVEMENT_UNLOCKED -> Icons.Filled.EmojiEvents
    NotificationType.NEW_PERSONAL_BEST -> Icons.Filled.Star
    NotificationType.LEADERBOARD_RANK_UP -> Icons.Filled.TrendingUp
    NotificationType.RECOMMENDED_SCENARIO -> Icons.Filled.School
    NotificationType.CHALLENGE_COMPLETED -> Icons.Filled.MilitaryTech
    NotificationType.TEAM_RANK_UP -> Icons.Filled.Groups
    NotificationType.EXAM_COMPLETED -> Icons.Filled.CheckCircle
    NotificationType.LEVEL_UP -> Icons.Filled.TrendingUp
    NotificationType.NEW_SCENARIO -> Icons.Filled.School
    NotificationType.NEW_CHALLENGE -> Icons.Filled.MilitaryTech
    NotificationType.POINTS_EXPIRING -> Icons.Filled.Timer
    NotificationType.POINTS_EXPIRED -> Icons.Filled.TrendingDown
    NotificationType.UNKNOWN -> Icons.Filled.Notifications
}

/** Подпись типа уведомления для второстепенной строки под заголовком. */
private fun typeLabel(type: NotificationType): String = when (type) {
    NotificationType.ACHIEVEMENT_UNLOCKED -> "Новая ачивка"
    NotificationType.NEW_PERSONAL_BEST -> "Личный рекорд"
    NotificationType.LEADERBOARD_RANK_UP -> "Рост в рейтинге"
    NotificationType.RECOMMENDED_SCENARIO -> "Рекомендация"
    NotificationType.CHALLENGE_COMPLETED -> "Челлендж выполнен"
    NotificationType.TEAM_RANK_UP -> "Рост бригады"
    NotificationType.EXAM_COMPLETED -> "Экзамен завершён"
    NotificationType.LEVEL_UP -> "Новый разряд"
    NotificationType.NEW_SCENARIO -> "Новый сценарий"
    NotificationType.NEW_CHALLENGE -> "Новый челлендж"
    NotificationType.POINTS_EXPIRING -> "Очки сгорают"
    NotificationType.POINTS_EXPIRED -> "Очки сгорели"
    NotificationType.UNKNOWN -> "Уведомление"
}

/** Список уведомлений игрока: прочитать одно по нажатию, отметить все прочитанными. */
@Composable
fun NotificationsScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel { NotificationsViewModel(container.gamificationRepository, container.playerRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.notifications.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.unreadCount > 0) "Непрочитанных: ${state.unreadCount}" else "Всё прочитано",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.unreadCount > 0) {
                    TextButton(onClick = viewModel::markAllRead) { Text("Прочитать все") }
                }
            }
        }

        when {
            state.loading && state.notifications.isEmpty() -> LoadingState(modifier = Modifier.fillMaxWidth())
            state.error != null && state.notifications.isEmpty() -> ErrorState(
                message = state.error ?: "Не удалось загрузить уведомления",
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxWidth(),
            )
            state.notifications.isEmpty() -> EmptyState(
                title = "Пока нет уведомлений",
                text = "Здесь появятся новые ачивки, рост в рейтинге и рекомендации.",
                modifier = Modifier.fillMaxWidth(),
            )
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.notifications, key = { it.id }) { notification ->
                    NotificationRow(notification = notification, onClick = { if (notification.unread) viewModel.markRead(notification.id) })
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: Notification, onClick: () -> Unit) {
    val containerColor = if (notification.unread) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = notification.unread, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(notification.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp).fillMaxWidth()) {
                Text(text = typeLabel(notification.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = notification.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(text = notification.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
