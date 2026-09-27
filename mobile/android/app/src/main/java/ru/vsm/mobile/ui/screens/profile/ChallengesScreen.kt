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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.vsm.mobile.R
import ru.vsm.mobile.domain.model.Challenge
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.common.viewModel
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.VsmBadge
import ru.vsm.mobile.ui.components.VsmTone
import ru.vsm.mobile.ui.navigation.AppNavigator

/** Челленджи месяца — карточки в стиле сайта (белая панель, радиус 22, без обводки/тени). */
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
            item {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(text = "Челленджи месяца", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Выполните цель до конца месяца и получите бонус очков компетенций.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            items(state.challenges, key = { it.code }) { challenge -> ChallengeCard(challenge) }
        }
    }
}

@Composable
private fun ChallengeCard(challenge: Challenge) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (challenge.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (challenge.completed) R.drawable.ic_check else R.drawable.ic_medal),
                        contentDescription = null,
                        tint = if (challenge.completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = challenge.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            val progress = if (challenge.targetCount > 0) {
                (challenge.current.toFloat() / challenge.targetCount.toFloat()).coerceIn(0f, 1f)
            } else {
                if (challenge.completed) 1f else 0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (challenge.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (challenge.completed) "Выполнено" else "${challenge.current} из ${challenge.targetCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                VsmBadge(
                    text = "+${challenge.rewardPoints} очков",
                    tone = if (challenge.completed) VsmTone.Green else VsmTone.Blue,
                )
            }
        }
    }
}
