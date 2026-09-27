package ru.vsm.mobile.ui.screens.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.screens.profile.blockLabel
import ru.vsm.mobile.ui.screens.profile.recommendationReasonLabel

/**
 * Главный экран "Сегодня" — правило чистого экрана: одна главная кнопка (начать со сценариев),
 * рекомендованный сценарий из аналитики компетенций, короткая сводка прогресса и мини-рейтинг.
 */
@Composable
fun TodayScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel {
        TodayViewModel(container.gamificationRepository, container.feedbackRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading && state.profile == null -> LoadingState(modifier = Modifier.fillMaxWidth())
        state.error != null && state.profile == null -> ErrorState(
            message = state.error ?: "Не удалось загрузить данные",
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> TodayContent(state = state, navigator = navigator)
    }
}

@Composable
private fun TodayContent(state: TodayUiState, navigator: AppNavigator) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeroCard(onStart = { navigator.openTab(Routes.SCENARIOS) }) }

        val profile = state.profile
        if (profile != null) {
            item { ProgressSummaryCard(completed = profile.scenariosCompleted, total = profile.totalScenariosAvailable) }
        }

        val recommended = state.recommended
        if (recommended != null) {
            item {
                RecommendedCard(
                    title = recommended.title,
                    block = recommended.block,
                    reason = recommendationReasonLabel(recommended.reason),
                    onClick = { navigator.open(Routes.play(recommended.scenarioId)) },
                )
            }
        }

        item {
            SectionCard(title = "Подготовка") {
                NavRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "Отработать ситуацию",
                    subtitle = "Каталог диалогов по блокам",
                    onClick = { navigator.openTab(Routes.SCENARIOS) },
                )
                NavRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Награды и достижения",
                    subtitle = if ((profile?.recentAchievements?.size ?: 0) > 0) {
                        "Последняя ачивка: ${profile!!.recentAchievements.first().title}"
                    } else {
                        "Первая награда — за пройденный сценарий"
                    },
                    onClick = { navigator.openTab(Routes.AWARDS) },
                )
            }
        }

        if (state.leaderboardTop.isNotEmpty()) {
            item {
                SectionCard(title = "Рейтинг") {
                    if (state.myRank != null) {
                        Text(
                            text = "Ваше место: ${state.myRank}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    state.leaderboardTop.take(3).forEach { entry -> MiniLeaderboardRow(entry) }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Весь рейтинг",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { navigator.open(Routes.LEADERBOARD) },
                    )
                }
            }
        }

        if (state.profile != null && state.profile.scenariosCompleted == 0) {
            item {
                EmptyState(
                    title = "Пока нет пройденных сценариев",
                    text = "Начните с любой ситуации из каталога — итоги появятся здесь.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Тренажёр проводника ВСМ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Отработка ситуаций в рейсе Москва — Санкт-Петербург",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Что случится в пути — заранее не известно. Диалог с пассажиром, таймер и две шкалы: лояльность и безопасность.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.height(0.dp))
                Text(text = "  Начать сценарий")
            }
        }
    }
}

@Composable
private fun ProgressSummaryCard(completed: Int, total: Int) {
    SectionCard(title = "Прогресс") {
        val fraction = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        Text(
            text = "$completed из $total ситуаций освоено",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ru.vsm.mobile.ui.components.ScaleBar(
            label = "Освоение каталога",
            value = (fraction * 100).toInt().coerceIn(0, 100),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecommendedCard(title: String, block: String, reason: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Рекомендуем пройти", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${blockLabel(block)} · $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(0.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniLeaderboardRow(entry: LeaderboardEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "${entry.rank}. ${entry.displayName}", style = MaterialTheme.typography.bodyMedium)
        Text(text = "${entry.totalScore}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
