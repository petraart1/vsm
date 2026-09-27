package ru.vsm.mobile.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.LeaderboardEntry
import ru.vsm.mobile.domain.model.Team
import ru.vsm.mobile.domain.model.TeamLeaderboardEntry
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.navigation.AppNavigator

/** Рейтинг: вкладки "Проводники" (индивидуальный) и "Бригады" (командный). */
@Composable
fun LeaderboardScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel { LeaderboardViewModel(container.gamificationRepository, container.playerRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth()) {
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
    val meInTop = state.playersTop.any { it.playerId == state.myEntry?.playerId }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.playersTop) { entry ->
            PlayerRow(entry = entry, max = max, isMe = entry.playerId == state.myEntry?.playerId)
        }
        if (state.myEntry != null && !meInTop) {
            item { PlayerRow(entry = state.myEntry, max = max, isMe = true) }
        }
    }
}

@Composable
private fun PlayerRow(entry: LeaderboardEntry, max: Int, isMe: Boolean) {
    val containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column {
                    Text(
                        text = if (isMe) "Вы" else entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        text = "${entry.scenariosCompleted} сценариев",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(text = "${entry.totalScore}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TeamsTab(state: LeaderboardUiState, onJoin: (String) -> Unit) {
    if (state.teamsTop.isEmpty() && state.availableTeams.isEmpty()) {
        LoadingState(modifier = Modifier.fillMaxWidth())
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${entry.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column {
                    Text(text = entry.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        text = "${entry.depot} · ${entry.memberCount} чел.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(text = "${entry.averageScore.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun JoinTeamRow(team: Team, joining: Boolean, onJoin: () -> Unit) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = team.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${team.depot} · ${team.memberCount} чел.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onJoin, enabled = !joining) {
                Text(if (joining) "Вступаем…" else "Вступить")
            }
        }
    }
}
