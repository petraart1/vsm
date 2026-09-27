package ru.vsm.mobile.ui.screens.shift

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.domain.repository.PlayerRepository
import ru.vsm.mobile.domain.repository.ScenarioRepository
import ru.vsm.mobile.ui.art.CarClass as ArtCarClass
import ru.vsm.mobile.ui.art.Passenger

private const val INSPECT_SECONDS = 75
private const val TICK_MS = 250L
private const val DWELL_MS = 3500L

enum class ShiftStage { SETUP, BRIEF, MED, REJECTED, INSPECT, TRIP, SUMMARY }

data class ShiftUiState(
    val stage: ShiftStage = ShiftStage.SETUP,
    // выбор режима (setup)
    val mode: ShiftMode = ShiftMode.STORY,
    val freeCarClass: CarClass = CarClass.STANDARD,
    val freeConditionMode: String = "random", // random|fit|fever|alcohol|substances
    val story: Map<String, ChapterState> = emptyMap(),
    val selectedChapterId: String = CHAPTERS.first().id,
    val situationsReady: Boolean = false,
    // активный план
    val plan: ShiftPlan? = null,
    val chapter: Chapter? = null,
    val artCls: ArtCarClass = artClassOf(CarClass.STANDARD),
    val condition: MedCondition = MedCondition.FIT,
    // заступ
    val medLog: List<DialogLogEntry> = emptyList(),
    // приёмка
    val inspectionPoints: List<InspectionState> = emptyList(),
    val inspectionSecondsLeft: Int = INSPECT_SECONDS,
    val inspectionToastKey: String? = null,
    // рейс
    val passengers: List<Passenger> = emptyList(),
    val incidents: List<TripIncident> = emptyList(),
    val tripProgress: Float = 0f,
    val tripStationName: String? = null,
    val tripMoving: Boolean = false,
    val tripSpeed: Int = 1,
    val tripPaused: Boolean = false,
    val tripToast: String? = null,
    val activeIncidentKey: String? = null,
    val pendingPlayScenarioId: String? = null,
    // итог
    val summary: ShiftSummary? = null,
    val rings: ShiftRings? = null,
    // диалог поверх сцены (заступ / стрессовая ситуация) — единый локальный движок
    val dialog: DialogUiState = DialogUiState(),
)

/**
 * «Смена проводника»: выбор режима (сюжет/свободная/случайный рейс) -> заступ (медосмотр и
 * инструктаж, чат с медиком/начальником поезда) -> приёмка вагона (точки осмотра на сцене вагона,
 * таймер) -> рейс (сцена вагона, вызовы пассажиров — часть из каталога backend, часть локальные
 * стрессовые ситуации) -> итог смены (кольца, решение для HR, список произошедшего).
 *
 * Анимация служб/посадки-высадки пассажиров в рейсе веб-версии (движение охраны/полиции к месту,
 * смена пассажиров на стоянках) здесь не переносилась — вызовы решаются тем же диалогом и той же
 * оценкой, только без промежуточной ходьбы персонажей по сцене.
 */
class ShiftViewModel(
    private val scenarioRepository: ScenarioRepository,
    private val playerRepository: PlayerRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ShiftUiState())
    val state: StateFlow<ShiftUiState> = _state.asStateFlow()

    private var playerId: String? = null
    private var situations: List<ScenarioSummary> = emptyList()
    private var contentRand = ShiftRng(1)
    private var preShiftScript: DialogScript? = null

    private var tripJob: Job? = null
    private var inspectTimerJob: Job? = null

    // --- локальный движок диалога (заступ / стрессовая ситуация) ---
    private var dialogScript: DialogScript? = null
    private var dialogNodeId: String? = null
    private var dialogSeq = 0
    private val dialogLog = mutableListOf<DialogLogEntry>()
    private var dialogOnDone: ((DialogOutcome) -> Unit)? = null
    private var dialogTimerJob: Job? = null
    private var mainDialogSpeaker = DialogSpeaker(kind = "passenger", name = "", role = "")

    init {
        viewModelScope.launch { playerId = playerRepository.getOrCreatePlayerId() }
        _state.update { it.copy(story = readStory(appContext)) }
        viewModelScope.launch {
            scenarioRepository.list(null)
                .onSuccess { situations = it; _state.update { s -> s.copy(situationsReady = true) } }
                .onFailure { _state.update { s -> s.copy(situationsReady = true) } }
        }
    }

    // ---------------------------------------------------------------------
    // Выбор режима и вагона
    // ---------------------------------------------------------------------

    fun selectMode(mode: ShiftMode) = _state.update { it.copy(mode = mode) }
    fun selectChapter(id: String) = _state.update { it.copy(selectedChapterId = id) }
    fun selectFreeCarClass(cls: CarClass) = _state.update { it.copy(freeCarClass = cls) }
    fun selectFreeCondition(mode: String) = _state.update { it.copy(freeConditionMode = mode) }

    fun startFromSetup() {
        val s = _state.value
        when (s.mode) {
            ShiftMode.STORY -> {
                val chapter = CHAPTERS.find { it.id == s.selectedChapterId } ?: CHAPTERS.first()
                begin(chapter.plan, chapter)
            }
            ShiftMode.FREE -> begin(freePlan(s.freeCarClass, s.freeConditionMode), null)
            ShiftMode.RANDOM -> begin(randomPlan(ShiftRng(System.currentTimeMillis() % 100000 + 5)), null)
        }
    }

    private fun freePlan(cls: CarClass, conditionMode: String): ShiftPlan {
        val condition = when (conditionMode) {
            "fit" -> MedCondition.FIT
            "fever" -> MedCondition.FEVER
            "alcohol" -> MedCondition.ALCOHOL
            "substances" -> MedCondition.SUBSTANCES
            else -> null // "random"
        }
        return ShiftPlan(carClass = cls, condition = condition)
    }

    private fun begin(plan: ShiftPlan, chapter: Chapter?) {
        val seed = System.currentTimeMillis() % 100000
        contentRand = ShiftRng(seed)
        val artCls = artClassOf(plan.carClass)
        val passengers = seatPassengers(artCls, contentRand)
        val condition = plan.condition ?: rollCondition(ShiftRng(seed + 17), plan.medRate)
        preShiftScript = buildPreShift(artCls, ShiftRng(seed + 29), plan.notice)
        dialogLog.clear()
        _state.update {
            it.copy(
                stage = if (plan.brief != null) ShiftStage.BRIEF else ShiftStage.MED,
                plan = plan, chapter = chapter, artCls = artCls, condition = condition,
                passengers = passengers, medLog = emptyList(), incidents = emptyList(),
                tripProgress = 0f, summary = null, rings = null,
            )
        }
        if (plan.brief == null) enterMed()
    }

    fun goToMedFromBrief() = enterMed()

    fun exitToSetup() {
        tripJob?.cancel()
        inspectTimerJob?.cancel()
        dialogTimerJob?.cancel()
        _state.update { it.copy(stage = ShiftStage.SETUP, dialog = DialogUiState()) }
    }

    // ---------------------------------------------------------------------
    // Заступ на смену
    // ---------------------------------------------------------------------

    /** Итог диалога заступа хранится до тех пор, пока игрок не нажмёт кнопку под финальной репликой. */
    private var pendingMedOutcome: DialogOutcome? = null

    private fun enterMed() {
        val condition = _state.value.condition
        _state.update { it.copy(stage = ShiftStage.MED) }
        val script = if (condition != MedCondition.FIT) MED_CONDITIONS.getValue(condition).script else preShiftScript!!
        val speaker = DialogSpeaker(kind = "person", outfit = "medic", name = "Ирина Сергеевна", role = "Медработник, предрейсовый осмотр")
        pendingMedOutcome = null
        startDialog(script, speaker) { outcome -> pendingMedOutcome = outcome }
    }

    /** Нажатие кнопки под финальной репликой заступа — переход к приёмке или к экрану недопуска. */
    fun confirmMedFinished() {
        val outcome = pendingMedOutcome ?: return
        pendingMedOutcome = null
        val condition = _state.value.condition
        _state.update { it.copy(medLog = outcome.log, dialog = DialogUiState()) }
        if (condition != MedCondition.FIT) {
            _state.value.chapter?.let { recordChapter(appContext, it.id, false, 0) }
            _state.update { it.copy(story = readStory(appContext), stage = ShiftStage.REJECTED) }
        } else {
            toInspection()
        }
    }

    fun retryFromRejected() = exitToSetup()

    // ---------------------------------------------------------------------
    // Приёмка вагона
    // ---------------------------------------------------------------------

    private fun toInspection() {
        val cls = _state.value.artCls
        val points = planInspection(cls, contentRand)
        _state.update { it.copy(stage = ShiftStage.INSPECT, inspectionPoints = points, inspectionSecondsLeft = INSPECT_SECONDS, dialog = DialogUiState()) }
        inspectTimerJob?.cancel()
        inspectTimerJob = viewModelScope.launch {
            while (_state.value.inspectionSecondsLeft > 0 && _state.value.stage == ShiftStage.INSPECT) {
                delay(1000)
                _state.update { it.copy(inspectionSecondsLeft = (it.inspectionSecondsLeft - 1).coerceAtLeast(0)) }
            }
            if (_state.value.stage == ShiftStage.INSPECT) acceptVehicle()
        }
    }

    fun onInspectHotspot(key: String) {
        val s = _state.value
        if (s.stage != ShiftStage.INSPECT) return
        val point = s.inspectionPoints.find { it.point.key == key } ?: return
        if (point.checked) return
        _state.update {
            it.copy(
                inspectionPoints = it.inspectionPoints.map { p -> if (p.point.key == key) p.copy(checked = true) else p },
                inspectionToastKey = key,
            )
        }
        viewModelScope.launch {
            delay(3200)
            _state.update { if (it.inspectionToastKey == key) it.copy(inspectionToastKey = null) else it }
        }
    }

    fun acceptVehicle() {
        if (_state.value.stage != ShiftStage.INSPECT) return
        inspectTimerJob?.cancel()
        toTrip()
    }

    // ---------------------------------------------------------------------
    // Рейс
    // ---------------------------------------------------------------------

    private fun toTrip() {
        val s = _state.value
        val plan = s.plan ?: return
        val cls = s.artCls
        val patience = plan.patience

        val faultyRefs = s.inspectionPoints.filter { it.faulty && !it.checked && it.point.situationRef != null }
            .map { it.point.situationRef!! }.distinct()
        val hasPassengers = s.passengers.isNotEmpty()
        val poolPassengers = if (hasPassengers) s.passengers else listOf(Passenger(seat = 0, variant = 0))
        val chosen = planIncidentSummaries(situations, hasPassengers, contentRand, faultyRefs)
        val seatOrder = poolPassengers.shuffled()
        val backendAll = chosen.mapIndexed { i, sc ->
            val urgent = URGENT_BLOCKS.contains(sc.block)
            val base = if (urgent) 35f else 50f
            TripIncident(
                key = "inc-$i", kind = IncidentKind.BACKEND, title = sc.title, block = sc.block, blockLabel = sc.block,
                urgent = urgent, seat = seatOrder[i % seatOrder.size].seat, scenarioId = sc.id,
                spawnAt = 0f, remaining = base * patience, total = base * patience, fromInspection = i >= 3,
            )
        }

        val urgentStress = STRESS_SCENARIOS.filter { it.urgent }
        val forced = findStress(plan.forcedStressId)
        val first = forced ?: urgentStress.random()
        val rest = STRESS_SCENARIOS.filter { it.id != first.id }.shuffled()
        val stressCount = plan.stressCount.coerceIn(0, 3)
        val stressPicked = (listOf(first) + rest.take(2)).take(stressCount)
        val takenSeats = backendAll.map { it.seat }.toSet()
        val freeSeats = poolPassengers.filter { it.seat !in takenSeats }
        val stressAll = stressPicked.mapIndexed { i, sc ->
            val base = if (sc.urgent) 35f else 50f
            val seat = (freeSeats.getOrNull(i) ?: poolPassengers[(i + 3) % poolPassengers.size]).seat
            TripIncident(
                key = "st-$i", kind = IncidentKind.STRESS, title = sc.title, block = sc.block, blockLabel = sc.blockLabel,
                urgent = sc.urgent, seat = seat, vestibule = sc.at == "vestibule", mood = sc.mood,
                lookVariant = sc.looks.randomOrNull(), stress = sc,
                spawnAt = 0f, remaining = base * patience, total = base * patience,
            )
        }

        val main = backendAll.filter { !it.fromInspection }.take(plan.backendCount.coerceIn(0, 3))
        val extra = backendAll.filter { it.fromInspection }
        val ordered = listOfNotNull(
            main.getOrNull(0), stressAll.getOrNull(0),
            main.getOrNull(1), stressAll.getOrNull(1),
            main.getOrNull(2), stressAll.getOrNull(2),
        ) + extra
        val spawnSlots = listOf(0.07f, 0.2f, 0.34f, 0.48f, 0.6f, 0.72f, 0.82f, 0.9f)
        val finalIncidents = ordered.mapIndexed { i, inc -> inc.copy(spawnAt = spawnSlots.getOrElse(i) { 0.92f }) }

        val lookBySeat = stressAll.filter { it.lookVariant != null && !it.vestibule }.associate { it.seat to it.lookVariant!! }
        val crew = s.passengers.map { p -> lookBySeat[p.seat]?.let { p.copy(variant = it, kid = false, phone = false) } ?: p }

        _state.update {
            it.copy(
                stage = ShiftStage.TRIP, incidents = finalIncidents, passengers = crew,
                tripProgress = 0f, tripStationName = STATIONS.first().name, tripMoving = false,
                tripSpeed = 1, tripPaused = false, activeIncidentKey = null, dialog = DialogUiState(),
            )
        }
        startTripLoop(cls)
    }

    private fun startTripLoop(cls: ArtCarClass) {
        tripJob?.cancel()
        val passed = mutableSetOf(0)
        var stationUntil: Long? = null
        tripJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val cur = _state.value
                if (cur.stage != ShiftStage.TRIP) break
                if (cur.tripPaused || cur.activeIncidentKey != null) continue
                val now = System.currentTimeMillis()
                if (stationUntil != null && now >= stationUntil!! && cur.tripStationName != STATIONS.last().name) {
                    stationUntil = null
                }
                val atStation = stationUntil != null && now < stationUntil!!
                var newProgress = cur.tripProgress
                var stationToast: String? = null
                var newStationName = cur.tripStationName
                if (!atStation) {
                    val tripSeconds = cur.plan?.tripSeconds ?: 140
                    newProgress = (cur.tripProgress + (TICK_MS / 1000f / tripSeconds) * cur.tripSpeed).coerceAtMost(1f)
                    STATIONS.forEachIndexed { idx, st ->
                        if (idx > 0 && idx !in passed && newProgress >= st.at) {
                            passed.add(idx)
                            val last = idx == STATIONS.size - 1
                            stationUntil = if (last) Long.MAX_VALUE else now + (DWELL_MS / cur.tripSpeed).coerceAtLeast(2600L / cur.tripSpeed)
                            newStationName = st.name
                            stationToast = if (last) "Прибытие: ${st.name}" else "Стоянка: ${st.name}"
                        }
                    }
                }
                var incidentToast: String? = null
                var changed = false
                val updatedIncidents = cur.incidents.map { inc ->
                    when (inc.status) {
                        IncidentStatus.PENDING -> if (newProgress >= inc.spawnAt) {
                            changed = true
                            incidentToast = if (inc.urgent) {
                                "Срочный вызов: ${if (inc.vestibule) "тамбур" else "место ${inc.seat + 1}"}"
                            } else {
                                "Пассажир зовёт: место ${inc.seat + 1}"
                            }
                            inc.copy(status = IncidentStatus.ACTIVE, remaining = inc.total)
                        } else inc
                        IncidentStatus.ACTIVE -> {
                            val r = inc.remaining - TICK_MS / 1000f
                            changed = true
                            if (r <= 0f) {
                                incidentToast = "Пассажир на месте ${inc.seat + 1} не дождался проводника"
                                inc.copy(status = IncidentStatus.MISSED, remaining = 0f)
                            } else inc.copy(remaining = r)
                        }
                        else -> inc
                    }
                }
                _state.update {
                    it.copy(
                        tripProgress = newProgress,
                        tripStationName = newStationName,
                        tripMoving = !atStation && newProgress < 1f,
                        incidents = if (changed) updatedIncidents else it.incidents,
                        tripToast = stationToast ?: incidentToast ?: it.tripToast,
                    )
                }
                val after = _state.value
                if (after.tripProgress >= 1f) {
                    val open = after.incidents.any { it.status == IncidentStatus.PENDING || it.status == IncidentStatus.ACTIVE }
                    if (!open) {
                        delay(1200)
                        finishTrip()
                        break
                    }
                }
            }
        }
    }

    fun clearTripToast() = _state.update { it.copy(tripToast = null) }
    fun toggleTripSpeed() {
        val order = listOf(1, 2, 4)
        _state.update { it.copy(tripSpeed = order[(order.indexOf(it.tripSpeed) + 1) % order.size]) }
    }
    fun pauseTrip() = _state.update { it.copy(tripPaused = true) }
    fun resumeTrip() = _state.update { it.copy(tripPaused = false) }

    fun onSignalTap(key: String) {
        val s = _state.value
        if (s.stage != ShiftStage.TRIP) return
        val inc = s.incidents.find { it.key == key } ?: return
        if (inc.status != IncidentStatus.ACTIVE) return
        _state.update {
            it.copy(
                incidents = it.incidents.map { i -> if (i.key == key) i.copy(status = IncidentStatus.TALKING) else i },
                activeIncidentKey = key,
            )
        }
        when (inc.kind) {
            IncidentKind.BACKEND -> _state.update { it.copy(pendingPlayScenarioId = inc.scenarioId) }
            IncidentKind.STRESS -> {
                val stress = inc.stress ?: return
                val variant = inc.lookVariant ?: s.passengers.find { it.seat == inc.seat }?.variant ?: 0
                val speaker = DialogSpeaker(
                    kind = "passenger",
                    name = if (inc.vestibule) "Пассажир в тамбуре" else "Пассажир, место ${inc.seat + 1}",
                    role = "Вагон ${s.artCls.car} · ${s.artCls.title}",
                    variant = variant, mood = inc.mood,
                )
                startDialog(stress.script, speaker) { outcome -> onLocalIncidentDone(key, outcome) }
            }
        }
    }

    fun onPlayOpened() = _state.update { it.copy(pendingPlayScenarioId = null) }

    fun onReturnedFromPlay() {
        val key = _state.value.activeIncidentKey ?: return
        val inc = _state.value.incidents.find { it.key == key } ?: return
        if (inc.kind != IncidentKind.BACKEND || inc.status == IncidentStatus.DONE) return
        _state.update {
            it.copy(
                incidents = it.incidents.map { i -> if (i.key == key) i.copy(status = IncidentStatus.DONE, resultVerdict = "Пройдено") else i },
                activeIncidentKey = null,
            )
        }
    }

    private fun onLocalIncidentDone(key: String, outcome: DialogOutcome) {
        _state.update {
            it.copy(
                incidents = it.incidents.map { i ->
                    if (i.key == key) i.copy(status = IncidentStatus.DONE, resultVerdict = outcome.verdict, resultLog = outcome.log) else i
                },
            )
        }
    }

    fun dismissTripDialog() {
        _state.update { it.copy(dialog = DialogUiState(), activeIncidentKey = null) }
    }

    fun disconnectedIncident() {
        val key = _state.value.activeIncidentKey ?: return
        _state.update {
            it.copy(
                incidents = it.incidents.map { i -> if (i.key == key) i.copy(status = IncidentStatus.DONE, resultVerdict = "Нет связи с сервером") else i },
                activeIncidentKey = null,
            )
        }
    }

    // ---------------------------------------------------------------------
    // Итог смены
    // ---------------------------------------------------------------------

    private fun finishTrip() {
        val s = _state.value
        val plan = s.plan ?: return
        val summary = summarize(plan.carClass, s.medLog, s.inspectionPoints, s.incidents)
        val rings = ringsFor(summary, s.incidents)
        val score = Math.round(((rings.procedure + rings.reaction + rings.quality) / 3) * 100)
        s.chapter?.let { recordChapter(appContext, it.id, summary.admitted, score) }
        _state.update { it.copy(stage = ShiftStage.SUMMARY, summary = summary, rings = rings, story = readStory(appContext)) }
    }

    fun nextChapterAfterSummary() {
        val s = _state.value
        val chapter = s.chapter ?: return
        val idx = CHAPTERS.indexOf(chapter)
        val admitted = s.summary?.admitted == true
        val target = if (admitted) CHAPTERS.getOrNull(idx + 1) ?: chapter else chapter
        begin(target.plan, target)
    }

    // ---------------------------------------------------------------------
    // Локальный движок диалога (заступ / стрессовая ситуация рейса)
    // ---------------------------------------------------------------------

    private fun startDialog(script: DialogScript, mainSpeaker: DialogSpeaker, onDone: (DialogOutcome) -> Unit) {
        dialogScript = script
        dialogLog.clear()
        dialogSeq = 0
        mainDialogSpeaker = mainSpeaker
        dialogOnDone = onDone
        _state.update { it.copy(dialog = DialogUiState(active = true, safety = 60, loyalty = 60)) }
        enterDialogNode(script.startId, emptyList())
    }

    private fun nextMsgId(): String {
        dialogSeq += 1
        return "d$dialogSeq"
    }

    private fun roleMeta(role: String, node: DialogNode): DialogSpeaker? {
        node.speakerOverride?.let { return it }
        return when (role) {
            "passenger" -> mainDialogSpeaker
            "neighbor" -> DialogSpeaker(kind = "passenger", name = "Пассажир рядом", role = "", variant = (mainDialogSpeaker.variant ?: 0) + 13)
            "chief" -> DialogSpeaker(kind = "person", outfit = "chief", name = "Начальник поезда", role = "По рации")
            "guard" -> DialogSpeaker(kind = "person", outfit = "guard", name = "Охрана поезда", role = "Сопровождение состава")
            "police" -> DialogSpeaker(kind = "person", outfit = "police", name = "Транспортная полиция", role = "Наряд на станции")
            "medic" -> DialogSpeaker(kind = "person", outfit = "medic", name = "Медицинская помощь", role = "Бригада на станции")
            else -> null // "narrator" — сцена, без портрета
        }
    }

    private fun nodeMessage(node: DialogNode): DialogMessage {
        if (node.speaker == "narrator") {
            return DialogMessage(id = nextMsgId(), from = DialogFrom.NOTE, text = listOfNotNull(node.context, node.text).joinToString(" "))
        }
        val meta = roleMeta(node.speaker, node)
        if (meta != null) {
            _state.update { it.copy(dialog = it.dialog.copy(title = meta.name, role = meta.role, outfit = meta.outfit, variant = meta.variant)) }
        }
        val label = if (node.speaker != "passenger") meta?.name else null
        return DialogMessage(id = nextMsgId(), from = DialogFrom.NPC, text = node.text, context = node.context, speakerLabel = label)
    }

    private fun enterDialogNode(nodeId: String, extra: List<DialogMessage>) {
        val script = dialogScript ?: return
        val node = script.nodes.getValue(nodeId)
        dialogNodeId = nodeId
        val msgs = extra + nodeMessage(node)
        if (node.end || node.choices.isEmpty()) {
            _state.update {
                it.copy(dialog = it.dialog.copy(messages = it.dialog.messages + msgs, choices = null, busy = false, final = true, timerRemaining = null, timerTotal = null))
            }
            finishDialog()
            return
        }
        _state.update {
            it.copy(
                dialog = it.dialog.copy(
                    messages = it.dialog.messages + msgs, choices = node.choices.shuffled(), busy = false,
                    timerRemaining = node.timerSeconds, timerTotal = node.timerSeconds,
                ),
            )
        }
        startDialogTimer(node.timerSeconds)
    }

    private fun startDialogTimer(seconds: Int?) {
        dialogTimerJob?.cancel()
        if (seconds == null) return
        dialogTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                _state.update { it.copy(dialog = it.dialog.copy(timerRemaining = remaining)) }
            }
            onDialogTimeout()
        }
    }

    fun chooseDialog(choiceId: String) {
        val script = dialogScript ?: return
        val nodeId = dialogNodeId ?: return
        val node = script.nodes.getValue(nodeId)
        val choice = node.choices.find { it.id == choiceId } ?: return
        applyDialogChoice(node, choice, timedOut = false)
    }

    private fun onDialogTimeout() {
        val script = dialogScript ?: return
        val nodeId = dialogNodeId ?: return
        val node = script.nodes.getValue(nodeId)
        if (node.choices.isEmpty()) return
        val worst = node.choices.minByOrNull { it.safety + it.loyalty } ?: return
        applyDialogChoice(node, worst, timedOut = true)
    }

    private fun applyDialogChoice(node: DialogNode, choice: DialogChoice, timedOut: Boolean) {
        dialogTimerJob?.cancel()
        val best = node.choices.maxOf { it.safety + it.loyalty } == (choice.safety + choice.loyalty)
        dialogLog.add(
            DialogLogEntry(
                step = node.id, choiceId = choice.id, safety = choice.safety, loyalty = choice.loyalty,
                best = best && !timedOut, critical = choice.critical, note = choice.note, question = node.text,
            ),
        )
        val meMsg = if (timedOut) {
            DialogMessage(id = nextMsgId(), from = DialogFrom.NOTE, text = "Время вышло — ситуация развивается без вас", bad = true)
        } else {
            DialogMessage(id = nextMsgId(), from = DialogFrom.ME, text = choice.text)
        }
        _state.update {
            it.copy(
                dialog = it.dialog.copy(
                    messages = it.dialog.messages + meMsg, choices = null, busy = true,
                    safety = clampScale(it.dialog.safety + choice.safety), loyalty = clampScale(it.dialog.loyalty + choice.loyalty),
                    timerRemaining = null, timerTotal = null,
                ),
            )
        }
        viewModelScope.launch {
            delay(750)
            val notes = mutableListOf<DialogMessage>()
            if (choice.reply != null) notes.add(DialogMessage(id = nextMsgId(), from = DialogFrom.NPC, text = choice.reply))
            notes.add(DialogMessage(id = nextMsgId(), from = DialogFrom.NOTE, safetyDelta = choice.safety, loyaltyDelta = choice.loyalty, bad = choice.safety < 0))
            if (choice.next != null) {
                enterDialogNode(choice.next, notes)
            } else {
                _state.update { it.copy(dialog = it.dialog.copy(messages = it.dialog.messages + notes, busy = false, final = true)) }
                finishDialog()
            }
        }
    }

    private fun finishDialog() {
        val log = dialogLog.toList()
        val outcome = DialogOutcome(safety = log.sumOf { it.safety }, loyalty = log.sumOf { it.loyalty }, verdict = localVerdict(log), log = log)
        dialogOnDone?.invoke(outcome)
    }
}
