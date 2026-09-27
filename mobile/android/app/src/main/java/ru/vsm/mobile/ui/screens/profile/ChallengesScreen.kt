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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.domain.model.Challenge
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.navigation.AppNavigator

/** Челленджи месяца игрока: цель, прогресс, награда в очках. */
@Composable
fun ChallengesScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel = viewModel { ChallengesViewModel(container.gamificationRepository, container.playerRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading && state.challenges.isEmpty() -> LoadingState(modifier = Modifier.fillMaxWidth())
        state.error != null && state.challenges.isEmpty() -> ErrorState(
            message = state.error ?: "Не удалось загрузить челленджи",
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxWidth(),
        )
        state.challenges.isEmpty() -> EmptyState(
            title = "Сейчас нет активных челленджей",
            text = "Новые челленджи месяца появятся здесь автоматически.",
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.challenges, key = { it.code }) { challenge -> ChallengeCard(challenge) }
        }
    }
}

@Composable
private fun ChallengeCard(challenge: Challenge) {
    val containerColor = if (challenge.completed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = challenge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector = if (challenge.completed) Icons.Filled.CheckCircle else Icons.Filled.MilitaryTech,
                    contentDescription = null,
                    tint = if (challenge.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = challenge.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            val progress = if (challenge.targetCount > 0) {
                (challenge.current.toFloat() / challenge.targetCount.toFloat()).coerceIn(0f, 1f)
            } else {
                if (challenge.completed) 1f else 0f
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (challenge.completed) "Выполнено" else "${challenge.current} из ${challenge.targetCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "+${challenge.rewardPoints} очков",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
