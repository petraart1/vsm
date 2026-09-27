package ru.vsm.mobile.ui.screens.scenarios

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import ru.vsm.mobile.R
import ru.vsm.mobile.ui.art.PassengerBust
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.DeltaBadges
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.ScaleBar
import ru.vsm.mobile.ui.components.VsmBadge
import ru.vsm.mobile.ui.components.VsmChoiceButton
import ru.vsm.mobile.ui.components.VsmTimer
import ru.vsm.mobile.ui.components.VsmTone
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Прохождение сценария: переписка с пассажиром в формате чата — портрет и шкалы в шапке, пузыри
 * реплик, полноширинные варианты ответа, кольцевой таймер. Портирован с
 * `frontend/src/screens/ScenarioPlay.jsx` + `frontend/src/components/dialog/ChatDialog.jsx`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioPlayScreen(scenarioId: String, navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: ScenarioPlayViewModel = viewModel {
        ScenarioPlayViewModel(scenarioId, container.scenarioRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        val content = state as? ScenarioPlayUiState.Content ?: return@LaunchedEffect
        if (content.finished) navigator.open(Routes.debrief(content.progressId))
    }

    // Портрет пассажира — только декоративный вывод из id сценария, как на сайте
    // (`Number(scenarioId) * 7 % 40`); в состоянии прохождения внешность не хранится.
    val passengerVariant = remember(scenarioId) {
        val n = scenarioId.toIntOrNull() ?: abs(scenarioId.hashCode())
        abs(n * 7) % 40
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Выйти из сценария?") },
            text = { Text("Прогресс текущего прохождения будет потерян.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; navigator.openTab(Routes.SCENARIOS) }) { Text("Выйти") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Остаться") } },
        )
    }

    Scaffold(
        topBar = {
            ChatHeader(
                title = "Пассажир",
                subtitle = (state as? ScenarioPlayUiState.Content)?.scenarioCode ?: "Сценарий",
                passengerVariant = passengerVariant,
                safetyScore = (state as? ScenarioPlayUiState.Content)?.safetyScore,
                loyaltyScore = (state as? ScenarioPlayUiState.Content)?.loyaltyScore,
                onClose = {
                    val content = state as? ScenarioPlayUiState.Content
                    if (content == null || content.finished || content.messages.size <= 1) {
                        navigator.openTab(Routes.SCENARIOS)
                    } else {
                        confirmExit = true
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
                is ScenarioPlayUiState.Content -> ScenarioPlayContent(
                    state = s,
                    passengerVariant = passengerVariant,
                    onChoose = viewModel::choose,
                )
            }
        }
    }
}

/** Шапка диалога: круглая кнопка выхода, портрет + имя/роль, компактные шкалы. */
@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    passengerVariant: Int,
    safetyScore: Int?,
    loyaltyScore: Int?,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.ic_x), contentDescription = "Выйти", modifier = Modifier.size(16.dp))
            }
            PassengerBust(variant = passengerVariant, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (safetyScore != null || loyaltyScore != null) {
                Column(modifier = Modifier.widthIn(min = 96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (safetyScore != null) {
                        ScaleBar(
                            label = "Безопасность",
                            value = safetyScore,
                            color = VsmPalette.safety,
                            compact = true,
                            icon = { Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = VsmPalette.safety, modifier = Modifier.size(11.dp)) },
                        )
                    }
                    if (loyaltyScore != null) {
                        ScaleBar(
                            label = "Лояльность",
                            value = loyaltyScore,
                            color = VsmPalette.loyalty,
                            compact = true,
                            icon = { Icon(painterResource(R.drawable.ic_smile), contentDescription = null, tint = VsmPalette.loyalty, modifier = Modifier.size(11.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioPlayContent(
    state: ScenarioPlayUiState.Content,
    passengerVariant: Int,
    onChoose: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { message -> ChatBubble(message, passengerVariant) }
            if (state.submitting) {
                item(key = "typing") {
                    Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                        TypingDots()
                    }
                }
            }
        }

        // Нижний док: таймер (если есть) + варианты ответа — как `.dock` в `ChatDialog`.
        if (state.awaitingChoice && !state.submitting) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.timerRemainingSeconds != null && state.timerTotalSeconds != null && state.timerTotalSeconds > 0) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            VsmTimer(
                                remainingSeconds = state.timerRemainingSeconds,
                                totalSeconds = state.timerTotalSeconds,
                            )
                        }
                    }
                    state.choices.forEach { choice -> VsmChoiceButton(text = choice.text, onClick = { onChoose(choice.id) }) }
                }
            }
        } else if (state.finished) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Открываем разбор…",
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Три пульсирующие точки — индикатор «собеседник печатает» (`.typing` в `ChatDialog.module.css`). */
@Composable
private fun TypingDots() {
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = i * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), CircleShape),
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, passengerVariant: Int) {
    when (message) {
        is ChatMessage.Passenger -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            PassengerBust(variant = passengerVariant, size = 28.dp)
            Column(modifier = Modifier.padding(start = 8.dp).widthIn(max = 320.dp)) {
                if (message.escalation) {
                    VsmBadge(text = "Эскалация", tone = VsmTone.Amber, modifier = Modifier.padding(bottom = 4.dp))
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
                ) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        is ChatMessage.Conductor -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        is ChatMessage.Note -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (message.wasTimeout) {
                Text(
                    "Время вышло",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (message.safetyDelta != null && message.loyaltyDelta != null) {
                DeltaBadges(
                    safetyDelta = message.safetyDelta,
                    loyaltyDelta = message.loyaltyDelta,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        is ChatMessage.Outcome -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 14.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
