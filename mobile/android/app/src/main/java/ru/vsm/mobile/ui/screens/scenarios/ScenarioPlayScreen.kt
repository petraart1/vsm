package ru.vsm.mobile.ui.screens.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.domain.model.ChoiceOption
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.ScaleBar
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Прохождение сценария: переписка с пассажиром, таймер текущего узла, шкалы лояльности и
 * безопасности (скрыты в режиме экзамена — когда сервер не раскрывает [LiveProgressState]/[ChoiceResult]
 * шкалы). Портирован с `frontend/src/screens/ScenarioPlay.jsx` + `ChatDialog.jsx`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioPlayScreen(scenarioId: String, navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: ScenarioPlayViewModel = viewModel {
        ScenarioPlayViewModel(scenarioId, container.scenarioRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val content = state as? ScenarioPlayUiState.Content ?: return@LaunchedEffect
        if (content.finished) navigator.open(Routes.debrief(content.progressId))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? ScenarioPlayUiState.Content)?.scenarioCode ?: "Сценарий"
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ScenarioPlayUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())
                is ScenarioPlayUiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = viewModel::start,
                    modifier = Modifier.fillMaxSize(),
                )
                is ScenarioPlayUiState.Content -> ScenarioPlayContent(state = s, onChoose = viewModel::choose)
            }
        }
    }
}

@Composable
private fun ScenarioPlayContent(state: ScenarioPlayUiState.Content, onChoose: (String) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.loyaltyScore != null || state.safetyScore != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.safetyScore?.let {
                    ScaleBar(label = "Безопасность", value = it, color = VsmPalette.safety, modifier = Modifier.weight(1f))
                }
                state.loyaltyScore?.let {
                    ScaleBar(label = "Лояльность", value = it, color = VsmPalette.loyalty, modifier = Modifier.weight(1f))
                }
            }
        }

        if (state.offlinePending > 0) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Нет связи: ${state.offlinePending} действий будет отправлено при восстановлении.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { message -> ChatBubble(message) }
            if (state.submitting) {
                item(key = "typing") {
                    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

        if (state.timerRemainingSeconds != null && state.timerTotalSeconds != null && state.timerTotalSeconds > 0) {
            val fraction = (state.timerRemainingSeconds.toFloat() / state.timerTotalSeconds).coerceIn(0f, 1f)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "Осталось: ${state.timerRemainingSeconds} с",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (fraction < 0.25f) VsmPalette.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (fraction < 0.25f) VsmPalette.danger else VsmPalette.safety,
                )
            }
        }

        if (state.awaitingChoice && !state.submitting) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.choices.forEach { choice -> ChoiceButton(choice = choice, onClick = { onChoose(choice.id) }) }
            }
        }
    }
}

@Composable
private fun ChoiceButton(choice: ChoiceOption, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(choice.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.Passenger -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Card(
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.escalation) {
                        Text(
                            text = "Эскалация",
                            style = MaterialTheme.typography.labelSmall,
                            color = VsmPalette.warning,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        is ChatMessage.Conductor -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Card(
                modifier = Modifier.fillMaxWidth(0.85f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        is ChatMessage.Note -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            val parts = buildList {
                if (message.wasTimeout) add("время вышло")
                message.safetyDelta?.let { add("безопасность ${formatDelta(it)}") }
                message.loyaltyDelta?.let { add("лояльность ${formatDelta(it)}") }
            }
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        is ChatMessage.Outcome -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun formatDelta(value: Int): String = if (value >= 0) "+$value" else "$value"
