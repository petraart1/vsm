package ru.vsm.mobile.ui.screens.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.BlockProgress
import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.ScaleBar
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Профиль проводника: уровень/звание с прогрессом до следующего, очки, прогресс по блокам,
 * аналитика компетенций (просевшие + рекомендации), ссылки на рейтинг/челленджи/настройки/экзамен.
 */
@Composable
fun ProfileScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel {
        ProfileViewModel(container.gamificationRepository, container.feedbackRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading && state.profile == null -> LoadingState(modifier = Modifier.fillMaxWidth())
        state.error != null && state.profile == null -> ErrorState(
            message = state.error ?: "Не удалось загрузить профиль",
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxWidth(),
        )
        state.profile != null -> ProfileContent(profile = state.profile!!, competencies = state.competencies, navigator = navigator)
    }
}

@Composable
private fun ProfileContent(profile: Profile, competencies: CompetencyAnalytics?, navigator: AppNavigator) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { IdentityCard(profile) }
        item { LevelCard(profile) }
        if (profile.blockProgress.isNotEmpty()) {
            item { BlockProgressCard(profile.blockProgress) }
        }
        item { ShortcutsCard(navigator) }
        item { CompetenciesCard(competencies, navigator) }
    }
}

@Composable
private fun IdentityCard(profile: Profile) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val initials = initialsFor(profile.displayName)
        Card(modifier = Modifier.size(64.dp), shape = CircleShape) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = initials, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profile.totalScore} очков · ${profile.scenariosCompleted} из ${profile.totalScenariosAvailable} освоено",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun initialsFor(name: String): String {
    val parts = name.split(Regex("[\\s-]+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "ПР"
    if (parts.size == 1) return parts[0].take(2).uppercase()
    return (parts[0].first().toString() + parts[1].first().toString()).uppercase()
}

/**
 * Уровень и звание с прогрессом до следующего. [Profile.level]/[Profile.levelTitle]/
 * [Profile.levelProgress]/[Profile.pointsToNextLevel] — поля лестницы разрядов из ответа профиля.
 */
@Composable
private fun LevelCard(profile: Profile) {
    SectionCard(title = "Разряд") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = "${profile.level}. ${profile.levelTitle}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (profile.levelProgress.coerceIn(0, 100)) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = profile.pointsToNextLevel?.let { "До следующего разряда: $it очков" } ?: "Высший разряд программы",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockProgressCard(blocks: List<BlockProgress>) {
    SectionCard(title = "Прогресс по блокам") {
        blocks.forEach { block ->
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                Text(text = blockLabel(block.block), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Пройдено: ${block.scenariosCompleted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = "Безопасность: ${block.safetyPoints}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VsmPalette.safety,
                        maxLines = 1,
                    )
                    Text(
                        text = "Лояльность: ${block.loyaltyPoints}",
                        style = MaterialTheme.typography.bodySmall,
                        color = VsmPalette.loyalty,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutsCard(navigator: AppNavigator) {
    SectionCard {
        ShortcutRow(icon = Icons.Filled.Leaderboard, title = "Рейтинг", onClick = { navigator.open(Routes.LEADERBOARD) })
        ShortcutRow(icon = Icons.Filled.Notifications, title = "Уведомления", onClick = { navigator.open(Routes.NOTIFICATIONS) })
        ShortcutRow(icon = Icons.Filled.School, title = "Экзамен", onClick = { navigator.open(Routes.EXAM) })
        ShortcutRow(icon = Icons.Filled.Settings, title = "Настройки", onClick = { navigator.open(Routes.SETTINGS) })
    }
}

@Composable
private fun ShortcutRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompetenciesCard(competencies: CompetencyAnalytics?, navigator: AppNavigator) {
    SectionCard(title = "Компетенции") {
        if (competencies == null || competencies.totalPlaythroughs == 0) {
            Text(
                text = "Разбор по компетенциям появится после нескольких прохождений.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Text(text = "Успешность по блокам", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        competencies.blockStats.forEach { block ->
            ScaleBar(
                label = blockLabel(block.block),
                value = (block.successRate * 100).toInt().coerceIn(0, 100),
                color = if (block.weak) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (competencies.weakCompetencies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Требуют внимания", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = competencies.weakCompetencies.joinToString(", ") { blockLabel(it) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (competencies.recommendations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Рекомендуем пройти", style = MaterialTheme.typography.labelLarge)
            competencies.recommendations.forEach { rec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navigator.open(Routes.play(rec.scenarioId)) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rec.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = recommendationReasonLabel(rec.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
