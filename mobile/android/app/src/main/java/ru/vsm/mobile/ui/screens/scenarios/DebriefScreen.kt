package ru.vsm.mobile.ui.screens.scenarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.domain.model.Debrief
import ru.vsm.mobile.domain.model.DebriefStep
import ru.vsm.mobile.domain.model.KeyMoment
import ru.vsm.mobile.domain.model.NodeType
import ru.vsm.mobile.domain.model.ScenarioOutcome
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.theme.VsmPalette

/**
 * Разбор одного прохождения: итог, таймлайн решений с дельтами шкал и соблюдением шагов ролевой
 * модели, ключевая развилка ("можно было сделать иначе"), ссылки на нормы.
 * Портирован с `frontend/src/screens/Debrief.jsx`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebriefScreen(progressId: String, navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: DebriefViewModel = viewModel {
        DebriefViewModel(progressId, container.feedbackRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Разбор прохождения") },
                navigationIcon = {
                    IconButton(onClick = { navigator.openTab(Routes.SCENARIOS) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К сценариям")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is DebriefUiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize())

                is DebriefUiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                )

                is DebriefUiState.UnavailableDuringExam -> EmptyState(
                    title = "Разбор недоступен",
                    text = "Появится, когда экзамен, в который входит это прохождение, будет завершён целиком.",
                    modifier = Modifier.fillMaxSize(),
                )

                is DebriefUiState.Content -> DebriefContent(debrief = s.debrief, navigator = navigator)
            }
        }
    }
}

@Composable
private fun DebriefContent(debrief: Debrief, navigator: AppNavigator) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DebriefHeader(debrief) }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ScoreTile(label = "Безопасность", value = debrief.finalSafetyScore, color = VsmPalette.safety, modifier = Modifier.weight(1f))
                ScoreTile(label = "Лояльность", value = debrief.finalLoyaltyScore, color = VsmPalette.loyalty, modifier = Modifier.weight(1f))
            }
        }

        item {
            SectionCard(title = "Что усилить") {
                Text(debrief.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Text("Ваши решения", style = MaterialTheme.typography.titleMedium)
        }
        items(debrief.timeline, key = { it.sequenceIndex }) { step -> DebriefStepCard(step) }

        debrief.keyMoment?.let { moment ->
            item { KeyMomentCard(moment) }
        }

        if (debrief.normReferences.isNotEmpty()) {
            item {
                SectionCard(title = "Нормы регламента") {
                    debrief.normReferences.forEach { ref ->
                        Text("• $ref", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { navigator.open(Routes.play(debrief.scenarioId)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Пройти ещё раз")
                }
                OutlinedButton(onClick = { navigator.openTab(Routes.SCENARIOS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Другие сценарии")
                }
            }
        }
    }
}

@Composable
private fun DebriefHeader(debrief: Debrief) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(debrief.scenarioBlock, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            Text(
                debrief.scenarioTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = debrief.verdict,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colorForOutcome(debrief.outcome),
        )
        if (debrief.interrupted) {
            Text(
                text = "Прохождение завершено автоматически, а не последним решением.",
                style = MaterialTheme.typography.labelMedium,
                color = VsmPalette.warning,
            )
        }
    }
}

@Composable
private fun ScoreTile(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DebriefStepCard(step: DebriefStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "${step.sequenceIndex + 1}.",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                if (step.nodeType == NodeType.ESCALATION) {
                    AssistChip(onClick = {}, label = { Text("Эскалация") })
                }
                if (step.wasTimeout) {
                    AssistChip(onClick = {}, label = { Text("Время вышло") })
                }
                if (step.scaleConflict) {
                    AssistChip(onClick = {}, label = { Text("Конфликт шкал") })
                }
            }
            Text(step.nodeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(step.choiceText, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "безопасность ${formatDelta(step.safetyDelta)} · лояльность ${formatDelta(step.loyaltyDelta)}",
                style = MaterialTheme.typography.labelMedium,
            )
            if (step.roleStepsCompleted.isNotEmpty() || step.roleStepsSkipped.isNotEmpty()) {
                Column {
                    step.roleStepsCompleted.forEach { label -> Text("✓ $label", style = MaterialTheme.typography.labelSmall, color = VsmPalette.success) }
                    step.roleStepsSkipped.forEach { label -> Text("✗ $label", style = MaterialTheme.typography.labelSmall, color = VsmPalette.danger) }
                }
            }
            if (step.explanation.isNotBlank()) {
                Text(step.explanation, style = MaterialTheme.typography.bodySmall)
            }
            if (step.hiddenCommunicationEffect) {
                Text(
                    text = "Решение принято в служебных переговорах: пассажир его не слышит, на лояльность не влияет, на безопасность — да.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun KeyMomentCard(moment: KeyMoment) {
    SectionCard(title = "Ключевая развилка") {
        Text(moment.nodeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Ваш ответ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(moment.chosenChoiceText, style = MaterialTheme.typography.bodyMedium)
        Text(
            "безопасность ${formatDelta(moment.chosenSafetyDelta)} · лояльность ${formatDelta(moment.chosenLoyaltyDelta)}",
            style = MaterialTheme.typography.labelSmall,
        )

        Text("Сильнее", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = VsmPalette.success)
        Text(moment.betterChoiceText, style = MaterialTheme.typography.bodyMedium)
        Text(
            "безопасность ${formatDelta(moment.betterSafetyDelta)} · лояльность ${formatDelta(moment.betterLoyaltyDelta)}",
            style = MaterialTheme.typography.labelSmall,
        )
        if (moment.betterExplanation.isNotBlank()) {
            Text(moment.betterExplanation, style = MaterialTheme.typography.bodySmall)
        }
        if (moment.adviceText.isNotBlank()) {
            Text(moment.adviceText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun colorForOutcome(outcome: ScenarioOutcome?): androidx.compose.ui.graphics.Color = when (outcome) {
    ScenarioOutcome.SUCCESS -> VsmPalette.success
    ScenarioOutcome.PARTIAL -> VsmPalette.warning
    ScenarioOutcome.FAILURE -> VsmPalette.danger
    null -> VsmPalette.warning
}

private fun formatDelta(value: Int): String = if (value >= 0) "+$value" else "$value"
