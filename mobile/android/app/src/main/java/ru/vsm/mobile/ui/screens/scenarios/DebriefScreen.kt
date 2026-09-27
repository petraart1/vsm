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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.R
import ru.vsm.mobile.domain.model.Debrief
import ru.vsm.mobile.domain.model.DebriefStep
import ru.vsm.mobile.domain.model.KeyMoment
import ru.vsm.mobile.domain.model.NodeType
import ru.vsm.mobile.domain.model.ScenarioOutcome
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.DeltaBadges
import ru.vsm.mobile.ui.components.EmptyState
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.ScaleBar
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.components.VsmBadge
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.components.VsmButtonSize
import ru.vsm.mobile.ui.components.VsmButtonVariant
import ru.vsm.mobile.ui.components.VsmTone
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.screens.profile.blockLabel
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { DebriefHeader(debrief) }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ScaleBar(
                    label = "Рейтинг безопасности",
                    value = debrief.finalSafetyScore,
                    color = VsmPalette.safety,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = VsmPalette.safety, modifier = Modifier.size(14.dp)) },
                )
                ScaleBar(
                    label = "Лояльность пассажира",
                    value = debrief.finalLoyaltyScore,
                    color = VsmPalette.loyalty,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(painterResource(R.drawable.ic_smile), contentDescription = null, tint = VsmPalette.loyalty, modifier = Modifier.size(14.dp)) },
                )
            }
        }

        item {
            SectionCard(title = "Что усилить") {
                Text(debrief.summary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Text("Ваши решения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(debrief.timeline, key = { it.sequenceIndex }) { step -> DebriefStepCard(step) }

        debrief.keyMoment?.let { moment ->
            item { KeyMomentCard(moment) }
        }

        if (debrief.normReferences.isNotEmpty()) {
            item {
                SectionCard(title = "Нормы регламента") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        debrief.normReferences.forEach { ref ->
                            Text("• $ref", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                VsmButton(
                    text = "Пройти ещё раз",
                    onClick = { navigator.open(Routes.play(debrief.scenarioId)) },
                    modifier = Modifier.fillMaxWidth(),
                    size = VsmButtonSize.Large,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_rotate), contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                VsmButton(
                    text = "Другие сценарии",
                    onClick = { navigator.openTab(Routes.SCENARIOS) },
                    modifier = Modifier.fillMaxWidth(),
                    size = VsmButtonSize.Large,
                    variant = VsmButtonVariant.Secondary,
                )
                VsmButton(
                    text = "Профиль",
                    onClick = { navigator.open(Routes.PROFILE) },
                    modifier = Modifier.fillMaxWidth(),
                    size = VsmButtonSize.Large,
                    variant = VsmButtonVariant.Ghost,
                )
            }
        }
    }
}

@Composable
private fun DebriefHeader(debrief: Debrief) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Подпись блока может быть длинной ("Медицинские и экстренные ситуации") — отдельной
        // строкой над названием, чтобы не сжимать его до нечитаемого остатка в узком ряду.
        VsmBadge(text = blockLabel(debrief.scenarioBlock), tone = VsmTone.Neutral)
        Text(
            debrief.scenarioTitle,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = debrief.verdict,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colorForOutcome(debrief.outcome),
        )
        if (debrief.interrupted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_alert), contentDescription = null, tint = VsmPalette.warning, modifier = Modifier.size(14.dp))
                Text(
                    text = "Прохождение завершено автоматически, а не последним решением.",
                    style = MaterialTheme.typography.labelMedium,
                    color = VsmPalette.warning,
                )
            }
        }
    }
}

@Composable
private fun DebriefStepCard(step: DebriefStep) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    VsmBadge(
                        text = "Эскалация",
                        tone = VsmTone.Neutral,
                        icon = { Icon(painterResource(R.drawable.ic_phone), contentDescription = null, modifier = Modifier.size(11.dp)) },
                    )
                }
                if (step.wasTimeout) {
                    VsmBadge(text = "Время вышло", tone = VsmTone.Red)
                }
                if (step.scaleConflict) {
                    VsmBadge(text = "Конфликт шкал", tone = VsmTone.Amber)
                }
            }
            Text(step.nodeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(step.choiceText, style = MaterialTheme.typography.bodyMedium)
            DeltaBadges(safetyDelta = step.safetyDelta, loyaltyDelta = step.loyaltyDelta)
            if (step.roleStepsCompleted.isNotEmpty() || step.roleStepsSkipped.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    step.roleStepsCompleted.forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = VsmPalette.success, modifier = Modifier.size(12.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = VsmPalette.success)
                        }
                    }
                    step.roleStepsSkipped.forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(R.drawable.ic_x), contentDescription = null, tint = VsmPalette.danger, modifier = Modifier.size(12.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = VsmPalette.danger)
                        }
                    }
                }
            }
            if (step.explanation.isNotBlank()) {
                Text(step.explanation, style = MaterialTheme.typography.bodySmall)
            }
            if (step.hiddenCommunicationEffect) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_radio), contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Решение принято в служебных переговорах: пассажир его не слышит, на лояльность не влияет, на безопасность — да.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyMomentCard(moment: KeyMoment) {
    SectionCard(title = "Ключевая развилка") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(moment.nodeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ваш ответ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(moment.chosenChoiceText, style = MaterialTheme.typography.bodyMedium)
                DeltaBadges(safetyDelta = moment.chosenSafetyDelta, loyaltyDelta = moment.chosenLoyaltyDelta)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Сильнее", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = VsmPalette.success)
                Text(moment.betterChoiceText, style = MaterialTheme.typography.bodyMedium)
                DeltaBadges(safetyDelta = moment.betterSafetyDelta, loyaltyDelta = moment.betterLoyaltyDelta)
                if (moment.betterExplanation.isNotBlank()) {
                    Text(moment.betterExplanation, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (moment.adviceText.isNotBlank()) {
                Text(moment.adviceText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
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
