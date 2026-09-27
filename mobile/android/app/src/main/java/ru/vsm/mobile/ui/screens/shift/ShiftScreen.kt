package ru.vsm.mobile.ui.screens.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.ErrorState
import ru.vsm.mobile.ui.components.LoadingState
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.theme.VsmPalette

private val CAR_CLASS_TITLES = mapOf(
    CarClass.STANDARD to "Стандарт",
    CarClass.COMFORT to "Комфорт",
    CarClass.BUSINESS to "Бизнес",
    CarClass.FIRST to "Первый",
)

private val SPEAKER_TITLES = mapOf(
    PreShiftSpeaker.MEDIC to "Медработник",
    PreShiftSpeaker.CHIEF to "Начальник поезда",
)

/**
 * Упрощённая «Смена»: заступ (медосмотр, инструктаж, приёмка) -> серия ситуаций рейса -> итог.
 * Каждая ситуация рейса открывается отдельным экраном прохождения через [Routes.play].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: ShiftViewModel = viewModel {
        ShiftViewModel(container.scenarioRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.pendingPlayScenarioId) {
        val scenarioId = state.pendingPlayScenarioId ?: return@LaunchedEffect
        navigator.open(Routes.play(scenarioId))
        viewModel.onPlayOpened()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onReturnedFromPlay()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Смена") }) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.stage) {
                ShiftStage.SETUP -> ShiftSetupContent(state, viewModel)
                ShiftStage.PRE_SHIFT -> PreShiftContent(state, viewModel)
                ShiftStage.TRIP -> TripContent(state, viewModel)
                ShiftStage.SUMMARY -> ShiftSummaryContent(state, navigator)
            }
        }
    }
}

@Composable
private fun ShiftSetupContent(state: ShiftUiState, viewModel: ShiftViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Смена проводника: заступ, приёмка вагона и серия ситуаций в рейсе Москва — Санкт-Петербург.",
            style = MaterialTheme.typography.bodyLarge,
        )
        SectionCard(title = "Класс вагона") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CarClass.entries.forEach { cls ->
                    FilterChip(
                        selected = state.carClass == cls,
                        onClick = { viewModel.selectCarClass(cls) },
                        label = { Text(CAR_CLASS_TITLES.getValue(cls)) },
                    )
                }
            }
        }
        Button(onClick = viewModel::startShift, modifier = Modifier.fillMaxWidth()) {
            Text("Заступить на смену")
        }
    }
}

@Composable
private fun PreShiftContent(state: ShiftUiState, viewModel: ShiftViewModel) {
    val step = state.preShiftSteps.getOrNull(state.preShiftIndex) ?: run { LoadingState(); return }
    val feedback = state.preShiftFeedback

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Заступ на смену · шаг ${state.preShiftIndex + 1} из ${state.preShiftSteps.size}",
            style = MaterialTheme.typography.labelLarge,
        )
        SectionCard(title = SPEAKER_TITLES.getValue(step.speaker)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(step.context, style = MaterialTheme.typography.bodyMedium)
                Text(step.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            }
        }
        if (feedback == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                step.choices.forEach { choice ->
                    OutlinedButton(onClick = { viewModel.answerPreShift(choice) }, modifier = Modifier.fillMaxWidth()) {
                        Text(choice.text)
                    }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(feedback.reply, fontWeight = FontWeight.Medium)
                    Text(feedback.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = viewModel::nextPreShiftStep, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.preShiftIndex + 1 >= state.preShiftSteps.size) "К рейсу" else "Дальше")
            }
        }
    }
}

@Composable
private fun TripContent(state: ShiftUiState, viewModel: ShiftViewModel) {
    if (state.tripLoading) {
        LoadingState()
        return
    }
    if (state.tripError) {
        ErrorState(message = "Не удалось получить ситуации рейса — нет связи с сервером.", onRetry = { viewModel.retryLoadTrip() })
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Рейс Москва — Санкт-Петербург: ${state.tripItems.count { it.done }} из ${state.tripItems.size} ситуаций пройдено.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        itemsIndexed(state.tripItems) { index, tripItem ->
            TripTimelineCard(
                number = index + 1,
                item = tripItem,
                isCurrent = index == state.tripIndex,
                onPlay = { viewModel.openCurrentTripItem() },
            )
        }
    }
}

@Composable
private fun TripTimelineCard(number: Int, item: TripItem, isCurrent: Boolean, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$number", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(item.scenario.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(item.scenario.block, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                item.done -> Icon(Icons.Filled.Check, contentDescription = "Пройдено", tint = VsmPalette.success)
                isCurrent -> Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(" Играть")
                }
                else -> Text("Ожидает", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ShiftSummaryContent(state: ShiftUiState, navigator: AppNavigator) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Смена завершена", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard(title = "Заступ на смену") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Безопасность: ${state.preShiftSafety}", color = VsmPalette.safety)
                Text("Лояльность: ${state.preShiftLoyalty}", color = VsmPalette.loyalty)
            }
        }
        SectionCard(title = "Рейс") {
            Text("Пройдено ${state.tripItems.count { it.done }} из ${state.tripItems.size} ситуаций.")
        }
        Button(onClick = { navigator.openTab(Routes.TODAY) }, modifier = Modifier.fillMaxWidth()) {
            Text("Завершить смену")
        }
    }
}
