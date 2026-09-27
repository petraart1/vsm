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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import ru.vsm.mobile.R
import ru.vsm.mobile.domain.model.Achievement
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.RingSpec
import ru.vsm.mobile.ui.components.ActivityRings
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.components.ShiftHeroCard
import ru.vsm.mobile.ui.components.StreakRow
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.screens.profile.blockLabel
import ru.vsm.mobile.ui.screens.profile.recommendationReasonLabel
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Главный экран "Сегодня" — правило чистого экрана: hero-карточка "Смена проводника", сводка
 * прогресса кольцами, рекомендованный сценарий из аналитики компетенций, серия входов, недавние
 * достижения и мини-рейтинг. Порядок блоков повторяет веб-версию (см. Today.jsx).
 */
@Composable
fun TodayScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel {
        TodayViewModel(container.gamificationRepository, container.feedbackRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var streak by remember { mutableStateOf(StreakInfo(streakDays = 0, week = emptyList())) }
    LaunchedEffect(Unit) { streak = TodayStreak.recordOpenToday(context) }

    when {
        state.loading && state.profile == null -> LoadingState(modifier = Modifier.fillMaxWidth())
        state.error != null && state.profile == null -> ErrorState(
            message = state.error ?: "Не удалось загрузить данные",
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> TodayContent(state = state, streak = streak, navigator = navigator)
    }
}

@Composable
private fun TodayContent(state: TodayUiState, streak: StreakInfo, navigator: AppNavigator) {
    val today = remember { LocalDate.now() }
    val dateLabel = remember(today) {
        val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru"))
        val rest = today.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
        "$weekday, $rest".replaceFirstChar { it.uppercase(Locale("ru")) }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    text = dateLabel.uppercase(Locale("ru")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Сегодня",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        item {
            ShiftHeroCard(
                title = "Смена проводника",
                subtitle = "Заступ, приёмка вагона и рейс Москва — Санкт-Петербург. Что случится в пути, заранее не известно.",
                buttonText = "Начать смену",
                onStart = { navigator.open(Routes.SHIFT) },
            )
        }

        val profile = state.profile
        if (profile != null) {
            item { ProgressRingsCard(profile = profile) }
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

        item { Text(text = "Последние достижения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        val recent = profile?.recentAchievements.orEmpty()
        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "Пока нет достижений",
                    text = "Пройдите первую ситуацию из каталога — награды появятся здесь.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                SectionCard {
                    recent.take(4).forEachIndexed { index, achievement ->
                        AchievementRow(achievement)
                        if (index != recent.take(4).lastIndex) Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        item { Text(text = "Подготовка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            SectionCard {
                NavRow(
                    iconRes = R.drawable.ic_list,
                    title = "Отработать ситуацию",
                    subtitle = "Каталог диалогов по блокам — без смены и таймера рейса",
                    onClick = { navigator.openTab(Routes.SCENARIOS) },
                )
                NavRow(
                    iconRes = R.drawable.ic_medal,
                    title = "Награды и квалификации",
                    subtitle = if (recent.isNotEmpty()) "Последняя награда: ${recent.first().title}" else "Первая медаль — за пройденный сценарий",
                    onClick = { navigator.openTab(Routes.AWARDS) },
                )
            }
        }

        item { StreakRow(streakDays = streak.streakDays, week = streak.week) }

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
    }
}

/**
 * Кольца прогресса вместо веб-версии (которая берёт метрики смены за неделю из локальной
 * истории браузера, недоступной мобильному клиенту): доля пройденного каталога и баланс двух
 * шкал (безопасность/лояльность), накопленных по прогрессу блоков из профиля.
 */
@Composable
private fun ProgressRingsCard(profile: ru.vsm.mobile.domain.model.Profile) {
    val total = profile.totalScenariosAvailable.coerceAtLeast(1)
    val completedFraction = (profile.scenariosCompleted.toFloat() / total).coerceIn(0f, 1f)
    val totalSafety = profile.blockProgress.sumOf { it.safetyPoints }.coerceAtLeast(0)
    val totalLoyalty = profile.blockProgress.sumOf { it.loyaltyPoints }.coerceAtLeast(0)
    val scaleSum = (totalSafety + totalLoyalty).coerceAtLeast(1)
    val safetyFraction = totalSafety.toFloat() / scaleSum
    val loyaltyFraction = totalLoyalty.toFloat() / scaleSum

    SectionCard(title = "Прогресс") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityRings(
                size = 96.dp,
                rings = listOf(
                    RingSpec(completedFraction, MaterialTheme.colorScheme.secondary, "Каталог"),
                    RingSpec(safetyFraction, VsmPalette.safety, "Безопасность"),
                    RingSpec(loyaltyFraction, VsmPalette.loyalty, "Лояльность"),
                ),
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                ProgressLine(label = "Ситуаций освоено", value = "${profile.scenariosCompleted}/${profile.totalScenariosAvailable}")
                ProgressLine(label = "Безопасность", value = "${(safetyFraction * 100).toInt()}%")
                ProgressLine(label = "Лояльность", value = "${(loyaltyFraction * 100).toInt()}%")
            }
        }
    }
}

@Composable
private fun ProgressLine(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecommendedCard(title: String, block: String, reason: String, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Рекомендуем пройти", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${blockLabel(block)} · $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
private fun NavRow(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AchievementRow(achievement: Achievement) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            painterResource(R.drawable.ic_medal),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = achievement.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = achievement.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
        Text(
            text = "${entry.rank}. ${entry.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "${entry.totalScore}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
