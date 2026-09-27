package ru.vsm.mobile.ui.screens.awards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.CustomAward
import ru.vsm.mobile.ui.art.Medal
import ru.vsm.mobile.ui.art.MedalFinish
import ru.vsm.mobile.ui.art.MedalShape
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.navigation.AppNavigator

/**
 * Награды — как на сайте (`Achievements.jsx`): сетка объёмных медалей по категориям, полученные
 * в цвете, остальные — заблокированная серая отделка. Тап по медали открывает свидетельство.
 */
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

private sealed interface AwardDetail {
    data class Standard(val achievement: Achievement) : AwardDetail
    data class Custom(val award: CustomAward) : AwardDetail
}

@Composable
private fun AchievementsContent(state: AchievementsUiState) {
    var opened by remember { mutableStateOf<AwardDetail?>(null) }
    val fullSpan = GridItemSpan(2)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { fullSpan }) {
            Column {
                Text(text = "Награды", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Служебные отличия и учебные достижения за пройденные сценарии.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(span = { fullSpan }) {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TotalsStat(label = "Отличия", value = state.earnedCount, of = state.achievements.size)
                    if (state.customAwards.isNotEmpty()) {
                        TotalsStat(label = "Особые", value = state.earnedCustomCount, of = state.customAwards.size)
                    }
                }
            }
        }

        state.byCategory.forEach { (category, items) ->
            item(span = { fullSpan }) {
                Text(
                    text = categoryLabel(category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(items, key = { it.code }) { achievement ->
                val (shape, glyph) = categoryMedal(category)
                AwardTile(
                    title = achievement.title,
                    meta = if (achievement.earned) "Получено" else achievement.description,
                    earned = achievement.earned,
                    shape = shape,
                    glyph = glyph,
                    onClick = { opened = AwardDetail.Standard(achievement) },
                )
            }
        }

        if (state.customAwards.isNotEmpty()) {
            item(span = { fullSpan }) {
                Text(
                    text = "Особые награды",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.customAwards, key = { it.id }) { award ->
                AwardTile(
                    title = award.title,
                    meta = if (award.earned) "Получено" else award.description,
                    earned = award.earned,
                    shape = parseMedalShape(award.shape),
                    glyph = award.glyph ?: "medal",
                    onClick = { opened = AwardDetail.Custom(award) },
                )
            }
        }
    }

    val detail = opened
    if (detail != null) {
        AwardDetailDialog(detail = detail, onClose = { opened = null })
    }
}

@Composable
private fun TotalsStat(label: String, value: Int, of: Int) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "/$of",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp, start = 1.dp),
            )
        }
    }
}

@Composable
private fun AwardTile(
    title: String,
    meta: String,
    earned: Boolean,
    shape: MedalShape,
    glyph: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Medal(shape = shape, finish = MedalFinish.ENAMEL, glyph = glyph, earned = earned, size = 84.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AwardDetailDialog(detail: AwardDetail, onClose: () -> Unit) {
    val title: String
    val description: String
    val earned: Boolean
    val earnedAt: String?
    val shape: MedalShape
    val glyph: String
    when (detail) {
        is AwardDetail.Standard -> {
            title = detail.achievement.title
            description = detail.achievement.description
            earned = detail.achievement.earned
            earnedAt = detail.achievement.earnedAt
            val (s, g) = categoryMedal(detail.achievement.category)
            shape = s; glyph = g
        }
        is AwardDetail.Custom -> {
            title = detail.award.title
            description = detail.award.description
            earned = detail.award.earned
            earnedAt = detail.award.earnedAt
            shape = parseMedalShape(detail.award.shape)
            glyph = detail.award.glyph ?: "medal"
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {},
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Medal(shape = shape, finish = MedalFinish.ENAMEL, glyph = glyph, earned = earned, size = 128.dp)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (earned && earnedAt != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(text = "Получено $earnedAt", style = MaterialTheme.typography.labelMedium)
                    }
                }
                VsmButton(text = "Закрыть", onClick = onClose, modifier = Modifier.padding(top = 20.dp))
            }
        },
    )
}

private fun categoryLabel(category: String): String = when (category.lowercase()) {
    "milestone" -> "Вехи"
    "style" -> "Мастерство"
    "volume" -> "Активность"
    "challenge" -> "Челленджи"
    else -> category.replaceFirstChar { it.uppercase() }
}

/** Форма+глиф медали по категории ачивки — единый код для всех наград одной категории. */
private fun categoryMedal(category: String): Pair<MedalShape, String> = when (category.lowercase()) {
    "milestone" -> MedalShape.SHIELD to "flag"
    "style" -> MedalShape.HEXAGON to "sparkle"
    "volume" -> MedalShape.OCTAGON to "bolt"
    "challenge" -> MedalShape.CIRCLE to "medal"
    else -> MedalShape.CIRCLE to "medal"
}

private fun parseMedalShape(shape: String): MedalShape = when (shape.lowercase()) {
    "hexagon" -> MedalShape.HEXAGON
    "octagon" -> MedalShape.OCTAGON
    "shield" -> MedalShape.SHIELD
    else -> MedalShape.CIRCLE
}
