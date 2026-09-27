package ru.vsm.mobile.ui.screens.awards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.navigation.AppNavigator

/** Каталог ачивок — сеткой, полученные выделены цветом, остальные приглушены (заблокированы). */
@Composable
fun AchievementsScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel { AchievementsViewModel(container.gamificationRepository, container.playerRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading && state.achievements.isEmpty() -> LoadingState(modifier = Modifier.fillMaxWidth())
        state.error != null && state.achievements.isEmpty() -> ErrorState(
            message = state.error ?: "Не удалось загрузить награды",
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxWidth(),
        )
        state.achievements.isEmpty() -> EmptyState(
            title = "Каталог наград пуст",
            text = "Сервер тренажёра пока не вернул ни одной ачивки.",
            modifier = Modifier.fillMaxWidth(),
        )
        else -> AchievementsContent(state = state)
    }
}

@Composable
private fun AchievementsContent(state: AchievementsUiState) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Получено ${state.earnedCount} из ${state.achievements.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        state.byCategory.forEach { (category, items) ->
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    text = category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(items, key = { it.code }) { achievement -> AchievementTile(achievement) }
        }
    }
}

@Composable
private fun AchievementTile(achievement: Achievement) {
    val containerColor = if (achievement.earned) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (achievement.earned) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (achievement.earned) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = contentColor,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (achievement.earned) "Получено" else achievement.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = contentColor,
                maxLines = 2,
            )
        }
    }
}
