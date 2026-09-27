package ru.vsm.mobile.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.R
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.BlockProgress
import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Profile
import ru.vsm.mobile.ui.art.Medal
import ru.vsm.mobile.ui.art.MedalFinish
import ru.vsm.mobile.ui.art.MedalShape
import ru.vsm.mobile.ui.art.Seal
import ru.vsm.mobile.ui.art.SealState
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.ScaleBar
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.components.VsmAvatar
import ru.vsm.mobile.ui.components.VsmAvatarTone
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.screens.settings.SettingsSection
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Профиль проводника — как на сайте (`Profile.jsx`): личность+разряд, витрина наград,
 * сводные показатели, квалификации по модулям, последние награды, аналитика компетенций.
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
        item { ShowcaseCard(profile.recentAchievements) }
        item { StatsRow(profile) }
        if (profile.blockProgress.isNotEmpty()) {
            item { QualificationsCard(profile.blockProgress) }
        }
        if (profile.recentAchievements.isNotEmpty()) {
            item { RecentAwardsCard(profile.recentAchievements) }
        }
        item { CompetenciesCard(competencies, navigator) }
        item { ShortcutsCard(navigator) }
        item { SettingsSection(navigator) }
    }
}

/** Аватар + имя + разряд-звание, ниже — блок очков компетенций с сегментированной лесенкой прогресса. */
@Composable
private fun IdentityCard(profile: Profile) {
    Column {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            VsmAvatar(initials = initialsFor(profile.displayName), size = 72.dp, tone = VsmAvatarTone.Solid)
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${profile.level}. ${profile.levelTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${profile.scenariosCompleted} из ${profile.totalScenariosAvailable} ситуаций освоено",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SectionCard {
            Text(
                text = "${profile.totalScore}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "очков компетенций",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            GradeLadder(progress = profile.levelProgress)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = profile.pointsToNextLevel?.let { "До следующего разряда осталось $it" } ?: "Высший разряд программы",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Лесенка разрядов — сегменты, заполненные пропорционально прогрессу внутри текущего уровня. */
@Composable
private fun GradeLadder(progress: Int) {
    val segments = 7
    val filled = ((progress.coerceIn(0, 100) / 100f) * segments).toInt().coerceIn(0, segments)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(segments) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    ),
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

/** Витрина: до 6 недавних наград медалями, остаток — пустые слоты (как на сайте). */
@Composable
private fun ShowcaseCard(recentAchievements: List<Achievement>) {
    SectionCard(title = "Витрина") {
        val slots = 6
        val shown = recentAchievements.take(slots)
        val rows = (0 until slots).chunked(3)
        rows.forEach { rowIndices ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                rowIndices.forEach { i ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (i < shown.size) {
                            val (shape, glyph) = categoryMedal(shown[i].category)
                            Medal(shape = shape, finish = MedalFinish.ENAMEL, glyph = glyph, earned = true, size = 64.dp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_plus),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Сводные показатели — из полей, реально доступных в [Profile]. */
@Composable
private fun StatsRow(profile: Profile) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(label = "Очки", value = "${profile.totalScore}", modifier = Modifier.weight(1f))
        StatTile(label = "Разряд", value = "${profile.level}", modifier = Modifier.weight(1f))
        StatTile(
            label = "Рейтинг",
            value = profile.leaderboardRank?.let { "$it место" } ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Квалификации по блокам ситуаций — печать учебного модуля + счётчик пройденного. */
@Composable
private fun QualificationsCard(blocks: List<BlockProgress>) {
    SectionCard(title = "Квалификации") {
        blocks.take(4).forEach { block ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Seal(
                    mark = blockMark(block.block),
                    caption = null,
                    state = if (block.scenariosCompleted > 0) SealState.IN_TRAINING else SealState.NOT_STARTED,
                    size = 48.dp,
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = blockLabel(block.block),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${block.scenariosCompleted} пройдено · безопасность ${block.safetyPoints} · лояльность ${block.loyaltyPoints}",
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

/** Метка на печати квалификации — по первым буквам русского названия блока, а не служебного кода. */
private fun blockMark(block: String): String = blockLabel(block).take(2).uppercase()

/** Последние полученные награды — список с медалью-миниатюрой (вместо ленты прохождений, которой нет в клиенте). */
@Composable
private fun RecentAwardsCard(achievements: List<Achievement>) {
    SectionCard(title = "Последние награды") {
        achievements.take(6).forEach { a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                val (shape, glyph) = categoryMedal(a.category)
                Medal(shape = shape, finish = MedalFinish.ENAMEL, glyph = glyph, earned = a.earned, size = 36.dp)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = a.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (a.earnedAt != null) {
                        Text(
                            text = a.earnedAt,
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
}

@Composable
private fun ShortcutsCard(navigator: AppNavigator) {
    SectionCard {
        ShortcutRow(icon = Icons.Filled.Leaderboard, title = "Рейтинг", onClick = { navigator.open(Routes.LEADERBOARD) })
        ShortcutRow(icon = Icons.Filled.Notifications, title = "Уведомления", onClick = { navigator.open(Routes.NOTIFICATIONS) })
        ShortcutRow(icon = Icons.Filled.School, title = "Экзамен", onClick = { navigator.open(Routes.EXAM) })
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

/** Форма+глиф медали по категории ачивки — тот же код, что на экране наград. */
private fun categoryMedal(category: String): Pair<MedalShape, String> = when (category.lowercase()) {
    "milestone" -> MedalShape.SHIELD to "flag"
    "style" -> MedalShape.HEXAGON to "sparkle"
    "volume" -> MedalShape.OCTAGON to "bolt"
    "challenge" -> MedalShape.CIRCLE to "medal"
    else -> MedalShape.CIRCLE to "medal"
}
