package ru.vsm.mobile.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.domain.model.Team
import ru.vsm.mobile.domain.model.TeamLeaderboardEntry
import ru.vsm.mobile.ui.art.Medal
import ru.vsm.mobile.ui.art.MedalFinish
import ru.vsm.mobile.ui.art.MedalShape
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.VsmAvatar
import ru.vsm.mobile.ui.components.VsmAvatarTone
import ru.vsm.mobile.ui.navigation.AppNavigator

/** Рейтинг — как на сайте (`Leaderboard.jsx`): подиум топ-3 медалями, таблица мест 4+. Вкладка "Бригады" — только в мобильном клиенте. */
@Composable
fun LeaderboardScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel { LeaderboardViewModel(container.gamificationRepository, container.playerRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(text = "Рейтинг", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Проводники по сумме очков компетенций. Больше очков — за решения без ошибок безопасности.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        TabRow(selectedTabIndex = state.tab.ordinal) {
            Tab(
                selected = state.tab == LeaderboardTab.PLAYERS,
                onClick = { viewModel.selectTab(LeaderboardTab.PLAYERS) },
                text = { Text("Проводники") },
            )
            Tab(
                selected = state.tab == LeaderboardTab.TEAMS,
                onClick = { viewModel.selectTab(LeaderboardTab.TEAMS) },
                text = { Text("Бригады") },
            )
        }

        when {
            state.loading && state.playersTop.isEmpty() -> LoadingState(modifier = Modifier.fillMaxWidth())
            state.error != null && state.playersTop.isEmpty() -> ErrorState(
                message = state.error ?: "Не удалось загрузить рейтинг",
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxWidth(),
            )
            state.tab == LeaderboardTab.PLAYERS -> PlayersTab(state)
            else -> TeamsTab(state = state, onJoin = viewModel::joinTeam)
        }
    }
}

@Composable
private fun PlayersTab(state: LeaderboardUiState) {
    if (state.playersTop.isEmpty()) {
        EmptyState(
            title = "Рейтинг пока пуст",
            text = "Пройдите первый сценарий — и вы откроете таблицу.",
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val max = state.playersTop.maxOf { it.totalScore }.coerceAtLeast(1)
    val meInTop = state.playersTop.any { it.me }
    val myRank = state.myEntry?.rank
    val top = state.playersTop

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!meInTop && myRank != null) {
            item { PlacePlaque(rank = myRank) }
        }
        if (top.size >= 3) {
            item { Podium(top1 = top[0], top2 = top[1], top3 = top[2]) }
            item {
                Text(
                    text = "Нажмите на коллегу, чтобы посмотреть его награды.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        val rest = if (top.size >= 3) top.drop(3) else top
        items(rest) { entry -> PlayerRow(entry = entry, max = max, isMe = entry.me) }
        if (state.myEntry != null && !meInTop) {
            item { PlayerRow(entry = state.myEntry, max = max, isMe = true) }
        }
    }
}

@Composable
private fun PlacePlaque(rank: Long) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(text = "$rank", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                text = "ваше место в рейтинге",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun Podium(top1: LeaderboardEntry, top2: LeaderboardEntry, top3: LeaderboardEntry) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            PodiumColumn(entry = top2, place = 2, finish = MedalFinish.METAL, medalSize = 56.dp)
            PodiumColumn(entry = top1, place = 1, finish = MedalFinish.ENAMEL, medalSize = 72.dp)
            PodiumColumn(entry = top3, place = 3, finish = MedalFinish.GLASS, medalSize = 56.dp)
        }
    }
}

@Composable
private fun PodiumColumn(entry: LeaderboardEntry, place: Int, finish: MedalFinish, medalSize: androidx.compose.ui.unit.Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Medal(shape = MedalShape.CIRCLE, finish = finish, text = "$place", size = medalSize)
        Text(
            text = if (entry.me) "Вы" else entry.displayName.substringBefore(" "),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "${entry.totalScore}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayerRow(entry: LeaderboardEntry, max: Int, isMe: Boolean) {
    val containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val share = (entry.totalScore.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${entry.rank}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.width(28.dp),
            )
            VsmAvatar(
                initials = initials(entry.displayName),
                size = 32.dp,
                tone = if (isMe) VsmAvatarTone.Solid else VsmAvatarTone.Soft,
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isMe) "Вы" else entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    text = "${entry.levelTitle} · ${entry.scenariosCompleted} сцен.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(share)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = "${entry.totalScore}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

private fun initials(name: String): String {
    val parts = name.split(Regex("[\\s-]+")).filter { it.isNotBlank() }
    return if (parts.size > 1) (parts[0].first().toString() + parts[1].first().toString()).uppercase() else name.take(2).uppercase()
}

@Composable
private fun TeamsTab(state: LeaderboardUiState, onJoin: (String) -> Unit) {
    if (state.teamsTop.isEmpty() && state.availableTeams.isEmpty()) {
        LoadingState(modifier = Modifier.fillMaxWidth())
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.teamsTop) { entry -> TeamRow(entry = entry, isMine = entry.teamId == state.myTeamId) }

        if (state.availableTeams.isNotEmpty()) {
            item {
                Text(
                    text = "Вступить в бригаду",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(state.availableTeams) { team ->
                JoinTeamRow(team = team, joining = state.joiningTeamId == team.id, onJoin = { onJoin(team.id) })
            }
        }
    }
}

@Composable
private fun TeamRow(entry: TeamLeaderboardEntry, isMine: Boolean) {
    val containerColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.width(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${entry.depot} · ${entry.memberCount} чел.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${entry.averageScore.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun JoinTeamRow(team: Team, joining: Boolean, onJoin: () -> Unit) {
    Card(shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = team.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${team.depot} · ${team.memberCount} чел.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onJoin, enabled = !joining) {
                Text(if (joining) "Вступаем…" else "Вступить", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
