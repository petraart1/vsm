package ru.vsm.mobile.ui.screens.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ru.vsm.mobile.domain.model.Exam
import ru.vsm.mobile.domain.model.ExamScenarioItem
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.EmptyState
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

private val EXAM_SIZES = listOf(5, 10)

private val GRADE_TITLES = mapOf(
    "EXCELLENT" to "Отлично",
    "GOOD" to "Хорошо",
    "SATISFACTORY" to "Удовлетворительно",
    "UNSATISFACTORY" to "Неудовлетворительно",
)

private val OUTCOME_TITLES = mapOf(
    "SUCCESS" to "Решено",
    "PARTIAL" to "Частично",
    "FAILURE" to "Не решено",
)

/**
 * Режим экзамена: N ситуаций из разных блоков подряд без подсказок, единая оценка в конце.
 * Каждый пункт открывается отдельным экраном прохождения через [Routes.play]; шкалы и разбор
 * раскрываются только в итоге.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(navigator: AppNavigator) {
    val container = appContainer()
    val viewModel: ExamViewModel = viewModel {
        ExamViewModel(container.examRepository, container.playerRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Открываем прохождение текущего пункта, как только оно готово.
    LaunchedEffect(state.pendingPlayScenarioId) {
        val scenarioId = state.pendingPlayScenarioId ?: return@LaunchedEffect
        navigator.open(Routes.play(scenarioId))
        viewModel.onPlayOpened()
    }

    // При возврате из прохождения пункта — подтягиваем актуальное состояние экзамена.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAfterReturn()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Экзамен") },
                navigationIcon = {
                    IconButton(onClick = { if (state.step == ExamStep.INTRO) navigator.back() else navigator.openTab(Routes.SCENARIOS) }) {
                        Icon(
                            if (state.step == ExamStep.INTRO) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                            contentDescription = "Назад",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loadingIntro && state.step == ExamStep.INTRO -> LoadingState()
                state.step == ExamStep.INTRO -> ExamIntroContent(state, viewModel)
                state.step == ExamStep.RUNNING -> ExamRunningContent(state, viewModel)
                state.step == ExamStep.RESULTS && state.exam != null -> ExamResultsContent(state.exam!!, navigator)
                else -> LoadingState()
            }
        }
    }
}

@Composable
private fun ExamIntroContent(state: ExamUiState, viewModel: ExamViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Проверка без подсказок: несколько ситуаций из разных блоков подряд. Шкалы и разбор откроются только в конце, вместе с итоговой оценкой.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
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
        }
        item {
            SectionCard(title = "Длина экзамена") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EXAM_SIZES.forEach { n ->
                        FilterChip(
                            selected = state.size == n,
                            onClick = { viewModel.selectSize(n) },
                            label = { Text("$n ситуаций") },
                        )
                    }
                }
            }
        }
        item {
            SectionCard(title = "Правила") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Шкалы безопасности и лояльности скрыты до конца экзамена.")
                    Text("• Ситуации из разных блоков идут одна за другой без подсказок.")
                    Text("• Если средняя безопасность ниже 60, оценка не выше «Удовлетворительно».")
                    Text("• За оценку начисляются бонусные очки, «Отлично» даёт достижение «Сертификат».")
                }
            }
        }
        if (state.introError) {
            item { Text("Не удалось начать экзамен. Проверьте связь и попробуйте ещё раз.", color = MaterialTheme.colorScheme.error) }
        }
        item {
            Button(onClick = viewModel::begin, enabled = !state.creating, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.creating) "Готовим ситуации…" else "Начать экзамен")
            }
        }
    }
}

@Composable
private fun ExamRunningContent(state: ExamUiState, viewModel: ExamViewModel) {
    val exam = state.exam
    if (exam == null) {
        LoadingState()
        return
    }
    val idx = exam.currentIndex.coerceIn(0, (exam.size - 1).coerceAtLeast(0))
    val position = (exam.currentIndex + 1).coerceAtMost(exam.size)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ситуация $position из ${exam.size}", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(exam.size) { i ->
                val done = i < exam.currentIndex
                Box(
                    Modifier
                        .size(if (i == idx) 12.dp else 10.dp)
                        .background(
                            if (done) VsmPalette.success else if (i == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            androidx.compose.foundation.shape.CircleShape,
                        )
                )
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.itemError -> ErrorState(message = "Не удалось получить ситуацию — нет связи с сервером.", onRetry = { viewModel.retryStartCurrentItem() })
                state.itemStarting || state.advancing -> LoadingState()
                else -> Text(
                    "Открываем прохождение…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.advanceError) {
            Text("Нет связи с сервером. Потяните экран, чтобы обновить.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ExamResultsContent(exam: Exam, navigator: AppNavigator) {
    val res = exam.result
    if (res == null) {
        EmptyState(title = "Итог формируется", text = "Все ситуации пройдены. Обновите экран через несколько секунд, чтобы увидеть оценку.")
        return
    }
    val gradeTitle = GRADE_TITLES[res.grade.name] ?: res.grade.name
    val weak = res.weakBlocks.distinct()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Экзамен · ${exam.size} ситуаций · ${CAR_CLASS_TITLES.getValue(exam.carClass)}", style = MaterialTheme.typography.labelLarge)
                    Text(gradeTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (res.grade.name == "EXCELLENT") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = VsmPalette.success)
                            Spacer(Modifier.size(6.dp))
                            Text("Достижение «Сертификат» добавлено в профиль")
                        }
                    }
                    if (res.avgSafetyScore < 60) {
                        Text(
                            "Средняя безопасность ниже 60 — по правилам экзамена оценка не выше «Удовлетворительно».",
                            color = VsmPalette.warning,
                        )
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn(label = "Безопасность", value = res.avgSafetyScore.toInt().toString(), tint = VsmPalette.safety)
                StatColumn(label = "Лояльность", value = res.avgLoyaltyScore.toInt().toString(), tint = VsmPalette.loyalty)
                StatColumn(label = "Решено", value = "${(res.successRate * 100).toInt()}%", tint = VsmPalette.success)
            }
        }
        item {
            SectionCard(title = "Ситуации") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    exam.scenarios.forEachIndexed { i, item -> ExamResultRow(i + 1, item, navigator) }
                }
            }
        }
        if (weak.isNotEmpty()) {
            item {
                SectionCard(title = "Что стоит повторить") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        weak.forEach { block -> Text("• $block") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { navigator.open(Routes.EXAM) }, modifier = Modifier.fillMaxWidth()) { Text("Новый экзамен") }
            }
        }
        item {
            OutlinedButton(onClick = { navigator.openTab(Routes.SCENARIOS) }, modifier = Modifier.fillMaxWidth()) {
                Text("К тренировкам")
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ExamResultRow(number: Int, item: ExamScenarioItem, navigator: AppNavigator) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("$number", style = MaterialTheme.typography.labelLarge)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(item.block, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.outcome?.let { outcome ->
            val tint = when (outcome.name) {
                "SUCCESS" -> VsmPalette.success
                "PARTIAL" -> VsmPalette.warning
                else -> VsmPalette.danger
            }
            Icon(Icons.Filled.Check, contentDescription = null, tint = tint)
            Text(OUTCOME_TITLES[outcome.name] ?: outcome.name, style = MaterialTheme.typography.labelMedium)
        }
        val progressId = item.userProgressId
        if (progressId != null) {
            IconButton(onClick = { navigator.open(Routes.debrief(progressId)) }) {
                Icon(Icons.Filled.Info, contentDescription = "Разбор")
            }
        }
    }
}
