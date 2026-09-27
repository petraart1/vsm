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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.R
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
import ru.vsm.mobile.ui.components.VerifiedBadge
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.screens.shift.ShiftHistoryStats
import ru.vsm.mobile.ui.screens.shift.readShiftHistory
import ru.vsm.mobile.ui.screens.today.STREAK_MILESTONES
import ru.vsm.mobile.ui.screens.today.StreakMilestone
import ru.vsm.mobile.ui.screens.today.TodayStreak

/**
 * Награды — как на сайте (`Achievements.jsx`): сетка объёмных медалей по разделам, полученные
 * в цвете, будущие — заблокированная серая отделка. Тап по медали открывает свидетельство.
 * Разделы: смены без замечаний, официальные награды (только для подтверждённых через демо-ЕСИА
 * учётных записей), особые награды администратора, серии входов, учебные модули, служебные отличия.
 */
@Composable
fun AchievementsScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel { AchievementsViewModel(container.gamificationRepository, container.playerRepository, container.scenarioRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentUser by container.authRepository.currentUser.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val verified = currentUser?.verified == true
    val shiftHistory = remember(state.loading) { readShiftHistory(context) }
    val longestStreak = remember(state.loading) { TodayStreak.longestStreak(context) }

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
        else -> AchievementsContent(
            state = state,
            verified = verified,
            shiftHistory = shiftHistory,
            longestStreak = longestStreak,
            onConfirmIdentity = { navigator.open(Routes.LOGIN) },
        )
    }
}

private sealed interface AwardDetail {
    data class Standard(val achievement: Achievement) : AwardDetail
    data class Custom(val award: CustomAward) : AwardDetail
    data class Official(val award: OfficialAwardUi, val earned: Boolean) : AwardDetail
    data class Streak(val milestone: StreakMilestone, val earned: Boolean) : AwardDetail
    data class Module(val qualification: QualificationUi) : AwardDetail
}

@Composable
private fun AchievementsContent(
    state: AchievementsUiState,
    verified: Boolean,
    shiftHistory: ShiftHistoryStats,
    longestStreak: Int,
    onConfirmIdentity: () -> Unit,
) {
    var opened by remember { mutableStateOf<AwardDetail?>(null) }
    val fullSpan = GridItemSpan(2)
    val certifiedModules = state.qualifications.count { it.status == "certified" }
    val official = officialAwards(shiftHistory.admittedCount, shiftHistory.hasUpgrade, certifiedModules > 0)

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
                    text = "Учебные модули, служебные отличия и смены без замечаний.",
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
                    TotalsStat(label = "Модули", value = certifiedModules, of = state.qualifications.size)
                    TotalsStat(label = "Отличия", value = state.earnedCount, of = state.achievements.size)
                    TotalsStat(label = "Смены", value = shiftHistory.admittedCount, of = null)
                }
            }
        }

        if (shiftHistory.admittedCount > 0) {
            item(span = { fullSpan }) {
                Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Medal(
                            shape = MedalShape.CIRCLE,
                            finish = MedalFinish.ENAMEL,
                            glyph = "train",
                            earned = true,
                            size = 84.dp,
                            flippable = true,
                            backTitle = "Смена без замечаний",
                            backNote = "${shiftHistory.admittedCount} " + shiftCountWord(shiftHistory.admittedCount),
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = "Смена без замечаний", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Допуск подтверждён ${shiftHistory.admittedCount} ${shiftCountWord(shiftHistory.admittedCount)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                text = "Нажмите на медаль, чтобы перевернуть",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        item(span = { fullSpan }) {
            SectionHeader(title = "Официальные награды") {
                if (verified) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VerifiedBadge()
                        Text(
                            text = "Подтверждено",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                } else {
                    Text(
                        text = "Подтвердить через Госуслуги",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onConfirmIdentity),
                    )
                }
            }
        }
        if (!verified) {
            item(span = { fullSpan }) {
                Text(
                    text = "Официальные награды получают подтверждённые учётные записи: они учитываются в допуске и решении для HR. Выполненные условия сохраняются — награды появятся сразу после подтверждения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(official, key = { it.id }) { award ->
            val got = verified && award.condition
            AwardTile(
                title = award.title,
                meta = if (got) "Получено" else if (award.condition) "Условие выполнено — нужна проверка личности" else award.note,
                earned = got,
                shape = MedalShape.SHIELD,
                glyph = award.glyph,
                onClick = { opened = AwardDetail.Official(award, got) },
            )
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
                    meta = if (award.earned) "Получено" else award.description + if (award.verifiedOnly) " · для подтверждённых" else "",
                    earned = award.earned,
                    shape = parseMedalShape(award.shape),
                    glyph = award.glyph ?: "medal",
                    onClick = { opened = AwardDetail.Custom(award) },
                )
            }
        }

        item(span = { fullSpan }) {
            Text(
                text = "Серии входов",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(STREAK_MILESTONES, key = { it.days }) { milestone ->
            val got = longestStreak >= milestone.days
            AwardTile(
                title = milestone.title,
                meta = if (got) "Получено" else milestone.note,
                earned = got,
                shape = MedalShape.CIRCLE,
                glyph = null,
                text = milestone.days.toString(),
                onClick = { opened = AwardDetail.Streak(milestone, got) },
            )
        }

        item(span = { fullSpan }) {
            Text(
                text = "Учебные модули",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(state.qualifications, key = { it.block }) { q ->
            AwardTile(
                title = q.title,
                meta = when (q.status) {
                    "certified" -> "Присвоено"
                    "in_training" -> "${q.completed} из ${q.total}"
                    else -> "Модуль ${q.code}"
                },
                earned = q.status == "certified",
                shape = MedalShape.CIRCLE,
                glyph = MODULE_GLYPH[q.block] ?: "medal",
                onClick = { opened = AwardDetail.Module(q) },
            )
        }

        item(span = { fullSpan }) {
            Text(
                text = "Служебные отличия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(state.achievements, key = { it.code }) { achievement ->
            val (shape, glyph) = categoryMedal(achievement.category)
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

    val detail = opened
    if (detail != null) {
        AwardDetailDialog(detail = detail, onClose = { opened = null })
    }
}

@Composable
private fun SectionHeader(title: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        trailing()
    }
}

private fun shiftCountWord(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "раз"
    else -> "раза"
}

@Composable
private fun TotalsStat(label: String, value: Int, of: Int?) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "$value", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (of != null) {
                Text(
                    text = "/$of",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp, start = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun AwardTile(
    title: String,
    meta: String,
    earned: Boolean,
    shape: MedalShape,
    glyph: String?,
    onClick: () -> Unit,
    text: String? = null,
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
            Medal(shape = shape, finish = MedalFinish.ENAMEL, glyph = glyph, text = text, earned = earned, size = 84.dp)
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
    val glyph: String?
    val text: String?
    val requirements: List<QualificationRequirement>
    when (detail) {
        is AwardDetail.Standard -> {
            title = detail.achievement.title
            description = detail.achievement.description
            earned = detail.achievement.earned
            earnedAt = detail.achievement.earnedAt
            val (s, g) = categoryMedal(detail.achievement.category)
            shape = s; glyph = g; text = null; requirements = emptyList()
        }
        is AwardDetail.Custom -> {
            title = detail.award.title
            description = detail.award.description
            earned = detail.award.earned
            earnedAt = detail.award.earnedAt
            shape = parseMedalShape(detail.award.shape)
            glyph = detail.award.glyph ?: "medal"; text = null; requirements = emptyList()
        }
        is AwardDetail.Official -> {
            title = detail.award.title
            description = detail.award.note
            earned = detail.earned
            earnedAt = null
            shape = MedalShape.SHIELD; glyph = detail.award.glyph; text = null; requirements = emptyList()
        }
        is AwardDetail.Streak -> {
            title = detail.milestone.title
            description = detail.milestone.note
            earned = detail.earned
            earnedAt = null
            shape = MedalShape.CIRCLE; glyph = null; text = detail.milestone.days.toString(); requirements = emptyList()
        }
        is AwardDetail.Module -> {
            title = detail.qualification.title
            description = "Модуль ${detail.qualification.code}"
            earned = detail.qualification.status == "certified"
            earnedAt = null
            shape = MedalShape.CIRCLE; glyph = MODULE_GLYPH[detail.qualification.block] ?: "medal"; text = null
            requirements = detail.qualification.requirements
        }
    }
    val backNote = if (earned && earnedAt != null) "Получено $earnedAt" else if (earned) "Получено" else "Ещё не получено"
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {},
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Medal(
                    shape = shape,
                    finish = MedalFinish.ENAMEL,
                    glyph = glyph,
                    text = text,
                    earned = earned,
                    size = 128.dp,
                    flippable = true,
                    backTitle = title,
                    backNote = backNote,
                )
                Text(
                    text = "Нажмите на медаль, чтобы перевернуть",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
                requirements.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            painter = painterResource(if (r.met) R.drawable.ic_check else R.drawable.ic_x),
                            contentDescription = null,
                            tint = if (r.met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(text = r.label, style = MaterialTheme.typography.bodySmall)
                            Text(text = r.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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
