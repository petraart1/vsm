package ru.vsm.mobile.ui.screens.shift

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.ui.art.CarScene
import ru.vsm.mobile.ui.art.Hotspot
import ru.vsm.mobile.ui.art.MedRoom
import ru.vsm.mobile.ui.art.MedReadout
import ru.vsm.mobile.ui.art.Person
import ru.vsm.mobile.ui.art.SignalCall
import ru.vsm.mobile.ui.common.appContainer
import ru.vsm.mobile.ui.components.ActivityRings
import ru.vsm.mobile.ui.components.RingSpec
import ru.vsm.mobile.ui.components.SectionCard
import ru.vsm.mobile.ui.components.VsmBadge
import ru.vsm.mobile.ui.components.VsmButton
import ru.vsm.mobile.ui.components.VsmButtonVariant
import ru.vsm.mobile.ui.components.VsmSegmentedControl
import ru.vsm.mobile.ui.navigation.AppNavigator
import ru.vsm.mobile.ui.navigation.Routes
import ru.vsm.mobile.ui.theme.VsmPalette

private val CAR_CLASS_TITLES = mapOf(
    CarClass.STANDARD to "Стандарт",
    CarClass.COMFORT to "Комфорт",
    CarClass.BUSINESS to "Бизнес",
    CarClass.FIRST to "Первый",
)

private val CONDITION_LABELS = listOf(
    "random" to "Случайно", "fit" to "Норма", "fever" to "Температура", "alcohol" to "Алкоголь", "substances" to "Препараты",
)

/**
 * Мостик к корневому навграфу: во время «Рейса» глобальная шапка (заголовок/колокольчик/аватар)
 * временно скрывается, чтобы сцене вагона и диалогу доставалось больше места — таб-бар внизу при
 * этом остаётся видимым как обычно, ничего не перестраивая в навграфе постоянно.
 */
object ShiftChrome {
    var hideGlobalHeader by mutableStateOf(false)
        private set

    fun setHideHeader(hidden: Boolean) {
        hideGlobalHeader = hidden
    }
}

/**
 * «Смена проводника»: выбор режима -> заступ (медосмотр и инструктаж) -> приёмка вагона -> рейс
 * Москва — Санкт-Петербург (вызовы пассажиров) -> итог смены. Перенос `frontend/src/screens/Shift.jsx`.
 * Таб-бар остаётся видимым на всех этапах — своя мини-шапка рисуется поверх обычной только там, где
 * это уместно (заступ/приёмка/рейс), у остальных этапов — обычная шапка навграфа. Во время «Рейса»
 * скрывается и глобальная шапка (см. [ShiftChrome]) — экран сцены не должен скроллиться и не должен
 * терять место на заголовок, выход доступен системной кнопкой/жестом или через паузу в тулбаре рейса.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(navigator: AppNavigator) {
    val container = appContainer()
    val context = LocalContext.current.applicationContext
    val viewModel: ShiftViewModel = viewModel {
        ShiftViewModel(container.scenarioRepository, container.playerRepository, context)
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

    LaunchedEffect(state.stage) { ShiftChrome.setHideHeader(state.stage == ShiftStage.TRIP) }
    DisposableEffect(Unit) { onDispose { ShiftChrome.setHideHeader(false) } }

    Scaffold(
        topBar = {
            when (state.stage) {
                ShiftStage.MED -> PhaseBar("Заступ на смену", onExit = viewModel::exitToSetup)
                ShiftStage.INSPECT -> PhaseBar(
                    "Приёмка вагона",
                    onExit = viewModel::exitToSetup,
                    right = { Text(formatClock(state.inspectionSecondsLeft), style = MaterialTheme.typography.titleMedium, color = if (state.inspectionSecondsLeft <= 15) VsmPalette.danger else MaterialTheme.colorScheme.onSurface) },
                )
                // «Рейс» рисует свою компактную строку-тулбар в теле экрана — вторая шапка здесь не нужна.
                ShiftStage.TRIP -> {}
                else -> PhaseBar("Смена", onExit = null)
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.stage) {
                ShiftStage.SETUP -> SetupContent(state, viewModel)
                ShiftStage.BRIEF -> BriefContent(state, viewModel)
                ShiftStage.MED -> MedContent(state, viewModel)
                ShiftStage.REJECTED -> RejectedContent(state, viewModel)
                ShiftStage.INSPECT -> InspectContent(state, viewModel)
                ShiftStage.TRIP -> TripContent(state, viewModel)
                ShiftStage.SUMMARY -> SummaryContent(state, viewModel)
            }
        }
    }
}

private fun formatClock(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** Компактная однострочная шапка этапа смены: [назад] заголовок [таймер/др.] — минимум высоты,
 * чтобы сцене и диалогу под ней доставалось как можно больше места. Пояснения — в теле экрана. */
@Composable
private fun PhaseBar(title: String, onExit: (() -> Unit)?, right: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 4.dp, vertical = 2.dp).height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onExit != null) {
            IconButton(onClick = onExit, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, contentDescription = "Выйти из смены", modifier = Modifier.size(18.dp)) }
        } else {
            Box(Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
        if (right != null) Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) { right() } else Box(Modifier.width(36.dp))
    }
}

// ---------------------------------------------------------------------------
// Выбор режима и вагона
// ---------------------------------------------------------------------------

@Composable
private fun SetupContent(state: ShiftUiState, vm: ShiftViewModel) {
    val context = LocalContext.current
    val story = state.story
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Новая смена", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Рейс Москва — Санкт-Петербург. Что случится в пути, заранее не известно.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        VsmSegmentedControl(
            options = listOf(ShiftMode.STORY to "Сюжет", ShiftMode.FREE to "Свободная", ShiftMode.RANDOM to "Случайный рейс"),
            selected = state.mode, onSelect = vm::selectMode, modifier = Modifier.fillMaxWidth(),
        )

        when (state.mode) {
            ShiftMode.STORY -> {
                val passedCount = CHAPTERS.count { story[it.id]?.passed == true }
                Text("Карьера проводника · пройдено $passedCount из ${CHAPTERS.size}", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CHAPTERS.forEachIndexed { i, c ->
                        val open = chapterUnlocked(story, i)
                        val passed = story[c.id]?.passed == true
                        val selected = c.id == state.selectedChapterId
                        ChapterRow(index = i + 1, chapter = c, open = open, passed = passed, selected = selected, onClick = { if (open) vm.selectChapter(c.id) })
                    }
                }
                val selectedChapter = CHAPTERS.find { it.id == state.selectedChapterId } ?: CHAPTERS.first()
                Text(selectedChapter.plan.brief?.story.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ShiftMode.RANDOM -> {
                Text("Полная случайность", style = MaterialTheme.typography.titleSmall)
                BulletRow("Всё решает случай", "Вагон, самочувствие на заступе, особое указание, темп и число сложных ситуаций")
                BulletRow("Короткая история перед сменой", "Начальник поезда расскажет, что за рейс и что от вас ждут")
                BulletRow("Оценка — как обычно", "Допуск по итогам всей смены")
            }
            ShiftMode.FREE -> {
                Text("Вагон по наряду", style = MaterialTheme.typography.titleSmall)
                CarClassSelector(selected = state.freeCarClass, onSelect = vm::selectFreeCarClass)
                Text("Самочувствие на заступе", style = MaterialTheme.typography.titleSmall)
                VsmSegmentedControl(options = CONDITION_LABELS, selected = state.freeConditionMode, onSelect = vm::selectFreeCondition, modifier = Modifier.fillMaxWidth())
            }
        }

        Text("Порядок смены", style = MaterialTheme.typography.titleSmall)
        BulletRow("Заступ", "Медосмотр и инструктаж — каждый раз немного по-разному")
        BulletRow("Приёмка вагона", "75 секунд, чтобы найти неисправности")
        BulletRow("Рейс", "Пассажиры позовут сами — подходите вовремя")
        BulletRow("Итог", "Оценивается вся смена, а не отдельный ответ")

        val ctaText = when (state.mode) {
            ShiftMode.STORY -> {
                val c = CHAPTERS.find { it.id == state.selectedChapterId } ?: CHAPTERS.first()
                if (state.situationsReady) "Начать: «${c.title}»" else "Готовим рейс…"
            }
            ShiftMode.RANDOM -> if (state.situationsReady) "Бросить кубики и начать" else "Готовим рейс…"
            ShiftMode.FREE -> if (state.situationsReady) "Начать смену" else "Готовим рейс…"
        }
        VsmButton(text = ctaText, onClick = vm::startFromSetup, enabled = state.situationsReady, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChapterRow(index: Int, chapter: Chapter, open: Boolean, passed: Boolean, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = open, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                when {
                    passed -> Icon(Icons.Filled.Check, contentDescription = "Пройдено", tint = VsmPalette.success)
                    !open -> Icon(Icons.Filled.Lock, contentDescription = "Заблокировано", modifier = Modifier.size(16.dp))
                    else -> Text("$index", style = MaterialTheme.typography.titleSmall)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(chapter.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (open) "${chapter.subtitle} · вагон «${CAR_CLASS_TITLES.getValue(chapter.plan.carClass)}»" else "Откроется после допуска в предыдущей главе",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BulletRow(title: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Сетка 2×2, одинаковая ширина/высота чипов — самая длинная подпись не переносится на узких экранах. */
@Composable
private fun CarClassSelector(selected: CarClass, onSelect: (CarClass) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CarClass.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cls ->
                    FilterChip(
                        selected = selected == cls, onClick = { onSelect(cls) },
                        label = { Text(CAR_CLASS_TITLES.getValue(cls), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Брифинг (сюжет / случайный рейс)
// ---------------------------------------------------------------------------

@Composable
private fun BriefContent(state: ShiftUiState, vm: ShiftViewModel) {
    val plan = state.plan ?: return
    val brief = plan.brief ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Person(outfit = "chief", hair = 2, modifier = Modifier.size(64.dp))
            Column {
                Text(state.chapter?.let { "Глава ${CHAPTERS.indexOf(it) + 1} · ${it.title}" } ?: "Случайный рейс", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.chapter?.subtitle ?: "Вагон ${state.artCls.car} · ${state.artCls.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Text(brief.story, style = MaterialTheme.typography.bodyMedium)
        Text("Инструкция на смену", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            brief.tasks.forEach { t -> Text("· $t", style = MaterialTheme.typography.bodyMedium) }
        }
        SectionCard {
            FactRow("Вагон", "${state.artCls.car} · ${state.artCls.title}")
            FactRow("Сложных ситуаций", if (plan.stressCount == 0) "нет" else plan.stressCount.toString())
            FactRow("Темп", brief.paceLabel ?: if (plan.patience > 1.1f) "Спокойный" else if (plan.patience < 0.95f) "Напряжённый" else "Обычный")
        }
        Text("Цель: ${brief.goal}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        VsmButton(text = "На медосмотр", onClick = vm::goToMedFromBrief, modifier = Modifier.fillMaxWidth())
        VsmButton(text = "Назад к выбору", onClick = vm::exitToSetup, variant = VsmButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------------------
// Заступ: медосмотр и инструктаж
// ---------------------------------------------------------------------------

@Composable
private fun MedContent(state: ShiftUiState, vm: ShiftViewModel) {
    val condition = state.condition
    val readout = if (condition != MedCondition.FIT) {
        val info = MED_CONDITIONS.getValue(condition)
        MedReadout(icon = if (condition == MedCondition.FEVER) "thermometer" else if (condition == MedCondition.ALCOHOL) "alert" else "stethoscope", text = info.measure, bad = true)
    } else null
    val place = if (state.dialog.outfit == "chief") "briefing" else "medpoint"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.dialog.role.ifBlank { "Заступ на смену" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MedRoom(place = place, npc = state.dialog.outfit ?: "medic", heroTalking = state.dialog.busy, npcTalking = !state.dialog.busy, readout = readout)
        DialogSheet(
            state = state.dialog, onChoose = vm::chooseDialog,
            footer = {
                if (state.dialog.final) {
                    val text = if (condition != MedCondition.FIT) "Итог заступа" else "Выйти к вагону — приёмка"
                    VsmButton(text = text, onClick = vm::confirmMedFinished, modifier = Modifier.fillMaxWidth())
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Недопуск к смене
// ---------------------------------------------------------------------------

@Composable
private fun RejectedContent(state: ShiftUiState, vm: ShiftViewModel) {
    val info = MED_CONDITIONS.getValue(state.condition)
    val critical = state.medLog.any { it.critical }
    val honest = state.medLog.all { it.best }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Вагон ${state.artCls.car} · Заступ на смену", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Не допущен к смене", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SectionCard(title = "Решение медработника") {
            Text(info.verdict, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(info.law, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(title = "Оценка поведения") {
            Text(
                if (critical) "Серьёзное нарушение порядка медосмотра" else if (honest) "Вы действовали правильно" else "Есть что улучшить",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
            )
            Text(
                if (critical) "Попытка скрыть состояние, подделать результат или выйти на смену вопреки решению медработника — повод для служебного расследования."
                else "Недопуск — не провал тренировки. Проводник, который честно сообщает о своём состоянии, защищает пассажиров и бригаду.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(title = "Разбор") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.medLog.forEach { m ->
                    Text(
                        "${if (m.best) "Верно" else if (m.critical) "Серьёзная ошибка" else "Ошибка"} — ${m.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (m.best) VsmPalette.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        VsmButton(text = "К выбору смены", onClick = vm::retryFromRejected, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------
// Приёмка вагона
// ---------------------------------------------------------------------------

@Composable
private fun InspectContent(state: ShiftUiState, vm: ShiftViewModel) {
    val toast = state.inspectionPoints.find { it.point.key == state.inspectionToastKey }
    val checked = state.inspectionPoints.count { it.checked }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            CarScene(
                cls = state.artCls, passengers = emptyList(),
                hotspots = state.inspectionPoints.map { p ->
                    Hotspot(key = p.point.key, x = p.x, icon = p.icon, title = p.point.title, state = if (p.checked) (if (p.faulty) "fault" else "ok") else "idle")
                },
                stationName = "Москва",
                onInteract = { type, key -> if (type == "hotspot") vm.onInspectHotspot(key) },
            )
            if (toast != null) {
                Card(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp).fillMaxWidth(0.9f),
                    colors = CardDefaults.cardColors(containerColor = if (toast.faulty) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(toast.point.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        Text(if (toast.faulty) "${toast.point.fault} Заявка передана в депо." else toast.point.ok, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (checked == 0) {
                Text(
                    "Обойдите вагон: у каждой точки осмотра — синяя метка.",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        VsmButton(
            text = if (checked == state.inspectionPoints.size) "Принять вагон" else "Принять вагон ($checked/${state.inspectionPoints.size})",
            onClick = vm::acceptVehicle, modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------------------
// Рейс
// ---------------------------------------------------------------------------

@Composable
private fun TripContent(state: ShiftUiState, vm: ShiftViewModel) {
    LaunchedEffect(state.tripToast) {
        if (state.tripToast != null) {
            kotlinx.coroutines.delay(2600)
            vm.clearTripToast()
        }
    }
    val activeIncident = state.incidents.find { it.key == state.activeIncidentKey }
    // Сцена вагона — во всю доступную высоту (aspect ratio по своей ширине, задаётся внутри CarScene),
    // а не через weight(1f): её размер не должен пересчитываться при появлении/исчезновении диалога
    // или паузы под ней — те накладываются поверх сцены, не сдвигая и не сжимая её.
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = vm::pauseTrip, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Pause, contentDescription = "Пауза", modifier = Modifier.size(18.dp)) }
            RouteLine(progress = state.tripProgress, modifier = Modifier.weight(1f))
            VsmButton(text = "${state.tripSpeed}×", onClick = vm::toggleTripSpeed, variant = VsmButtonVariant.Secondary)
            VsmBadge(text = "${state.incidents.count { it.status == IncidentStatus.DONE }}")
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).weight(1f)) {
            CarScene(
                cls = state.artCls, passengers = state.passengers,
                signals = state.incidents.filter { it.status == IncidentStatus.ACTIVE }.map { i ->
                    SignalCall(key = i.key, seat = i.seat, vestibuleEnd = i.vestibule, urgent = i.urgent, remaining = i.remaining, total = i.total)
                },
                moods = state.incidents.filter { it.mood != null && !it.vestibule && (it.status == IncidentStatus.ACTIVE || it.status == IncidentStatus.TALKING) }
                    .associate { it.seat to it.mood!! },
                moving = state.tripMoving, stationName = state.tripStationName,
                disabled = state.tripPaused || state.activeIncidentKey != null,
                onInteract = { type, key -> if (type == "signal") vm.onSignalTap(key) },
            )
            if (state.tripToast != null) {
                Card(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                    Text(state.tripToast, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }

            TripDialogPanel(
                dialogState = state.dialog, incidentActive = activeIncident != null,
                onChoose = vm::chooseDialog, onDismiss = vm::dismissTripDialog,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )

            if (state.tripPaused) {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)).padding(16.dp), contentAlignment = Alignment.Center) {
                    SectionCard(title = "Смена на паузе") {
                        Text("Таймеры остановлены. Досрочное завершение не засчитывается.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        VsmButton(text = "Продолжить", onClick = vm::resumeTrip, modifier = Modifier.fillMaxWidth())
                        VsmButton(text = "Завершить смену", onClick = vm::exitToSetup, variant = VsmButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * Диалог рейса как выезжающая снизу панель: слайд-ин при появлении вызова, слайд-аут после выбора
 * ответа или свайпа вниз (только когда диалог уже завершён — свайп не отменяет незавершённый выбор).
 * Последнее состояние запоминается, чтобы анимация скрытия доигрывала на настоящем контенте, а не на
 * пустоте, когда вызов уже обнулился в состоянии экрана.
 */
@Composable
private fun TripDialogPanel(
    dialogState: DialogUiState,
    incidentActive: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastDialogState by remember { mutableStateOf(dialogState) }
    if (dialogState.active && incidentActive) lastDialogState = dialogState
    val visible = dialogState.active && incidentActive

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        var dragOffset by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.pointerInput(lastDialogState.final) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0) {
                            dragOffset += dragAmount
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (lastDialogState.final && dragOffset > 120f) onDismiss()
                        dragOffset = 0f
                    },
                )
            },
        ) {
            DialogSheet(
                state = lastDialogState, onChoose = onChoose,
                footer = { if (lastDialogState.final) VsmButton(text = "Вернуться к работе", onClick = onDismiss, modifier = Modifier.fillMaxWidth()) },
            )
        }
    }
}

@Composable
private fun RouteLine(progress: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Москва", style = MaterialTheme.typography.labelSmall)
            Text("Санкт-Петербург", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ---------------------------------------------------------------------------
// Итог смены
// ---------------------------------------------------------------------------

@Composable
private fun SummaryContent(state: ShiftUiState, vm: ShiftViewModel) {
    val summary = state.summary ?: return
    val rings = state.rings ?: return
    val score = Math.round(((rings.procedure + rings.reaction + rings.quality) / 3) * 100)
    val chapter = state.chapter
    val nextChapter = chapter?.let { CHAPTERS.getOrNull(CHAPTERS.indexOf(it) + 1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Вагон ${state.artCls.car} · ${state.artCls.title} · Москва — Санкт-Петербург", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Смена завершена", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ActivityRings(
                rings = listOf(
                    RingSpec(rings.procedure, VsmPalette.safety, "Регламент"),
                    RingSpec(rings.reaction, MaterialTheme.colorScheme.primary, "Реакция"),
                    RingSpec(rings.quality, VsmPalette.loyalty, "Качество"),
                ),
                size = 140.dp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem("Регламент", rings.procedure)
            LegendItem("Реакция", rings.reaction)
            LegendItem("Качество", rings.quality)
        }

        SectionCard(title = "Решение для HR") {
            Text(if (summary.admitted) "Допуск к самостоятельной работе подтверждён" else "Нужна повторная смена", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                summaryNote(summary),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Балл смены $score", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }

        Text("Что произошло в рейсе", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.incidents.forEach { i ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(i.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${if (i.vestibule) "Тамбур" else "Место ${i.seat + 1}"} · ${if (i.status == IncidentStatus.DONE) i.resultVerdict ?: "Пройдено" else "Не подошли вовремя"}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text("Заступ и приёмка", style = MaterialTheme.typography.titleSmall)
        SectionCard {
            FactRow("Медосмотр и инструктаж", "${summary.medCorrect}/${summary.medTotal}")
            FactRow("Неисправности найдены", "${summary.inspectionFound}/${summary.inspectionFaults}")
        }

        if (chapter != null) {
            SectionCard(title = "Глава ${CHAPTERS.indexOf(chapter) + 1} · ${chapter.title}") {
                Text(
                    if (summary.admitted) {
                        if (nextChapter != null) "Глава пройдена. Открыта следующая: «${nextChapter.title}»" else "Все главы пройдены — карьера проводника завершена"
                    } else "Глава не пройдена — нужен допуск по итогам смены",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (chapter != null && summary.admitted && nextChapter != null) {
            VsmButton(text = "Следующая глава", onClick = vm::nextChapterAfterSummary, modifier = Modifier.fillMaxWidth())
        } else if (chapter != null) {
            VsmButton(text = if (summary.admitted) "Пройти главу ещё раз" else "Попробовать ещё раз", onClick = vm::nextChapterAfterSummary, modifier = Modifier.fillMaxWidth())
        } else {
            VsmButton(text = "Новая смена", onClick = vm::exitToSetup, modifier = Modifier.fillMaxWidth())
        }
        VsmButton(text = "К выбору смены", onClick = vm::exitToSetup, variant = VsmButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

private fun summaryNote(summary: ShiftSummary): String = when {
    summary.admitted -> summary.upgrade?.let { "Рекомендация: перевод в вагон класса «${it.title}»." } ?: "Класс вагона сохраняется."
    summary.critical -> "В одном из диалогов — критическая ошибка безопасности."
    summary.incidentsMissed > 0 -> "Пассажир не дождался проводника."
    !summary.honest -> "На медосмотре — ответ не по регламенту."
    summary.inspectionFound * 2 < summary.inspectionFaults -> "Вагон принят с неисправностями."
    else -> "Итоговая безопасность ниже порога."
}

@Composable
private fun LegendItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${Math.round(value * 100)}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
