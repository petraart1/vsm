package ru.vsm.mobile.ui.screens.shift

import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.ui.art.CAR_CLASSES
import ru.vsm.mobile.ui.art.CarClass
import ru.vsm.mobile.ui.art.Passenger
import ru.vsm.mobile.ui.art.seatX
import ru.vsm.mobile.ui.art.worldWidth
import ru.vsm.mobile.domain.model.CarClass as DomainCarClass

/**
 * Модель смены проводника, перенесённая построчно из веб-версии: вагоны по классам (`CarScene.kt`
 * уже несёт таблицу `CAR_CLASSES`), заступ, приёмка, инциденты рейса, итоговая оценка.
 */

/** Класс вагона для сцены (`ui/art/CarScene.kt`) по игровому классу обслуживания. */
fun artClassOf(cls: DomainCarClass): CarClass = CAR_CLASSES.getValue(cls.name)

val CLASS_ORDER: List<DomainCarClass> = listOf(DomainCarClass.STANDARD, DomainCarClass.COMFORT, DomainCarClass.BUSINESS, DomainCarClass.FIRST)

/** Детерминированный линейный конгруэнтный генератор — одна смена = один seed. */
class ShiftRng(seed: Long) {
    private var s: Long = (seed % 2147483647L).let { if (it <= 0) it + 2147483646L else it }

    fun next(): Double {
        s = (s * 16807L) % 2147483647L
        return (s - 1).toDouble() / 2147483646.0
    }

    fun nextInt(bound: Int): Int = (next() * bound).toInt().coerceIn(0, bound - 1)
    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]
}

data class Station(val at: Float, val name: String)

val STATIONS = listOf(
    Station(0f, "Москва"),
    Station(0.36f, "Тверь"),
    Station(0.7f, "Великий Новгород"),
    Station(1f, "Санкт-Петербург"),
)

fun seatPassengers(cls: CarClass, rand: ShiftRng): List<Passenger> {
    val list = mutableListOf<Passenger>()
    for (i in 0 until cls.seats) {
        if (rand.next() < cls.occupancy) {
            list.add(Passenger(seat = i, variant = rand.nextInt(40), kid = rand.next() < 0.08, phone = rand.next() < 0.3))
        }
    }
    return list
}

// ---------------------------------------------------------------------------
// Приёмка вагона
// ---------------------------------------------------------------------------

data class InspectionPoint(
    val key: String,
    val title: String,
    val ok: String,
    val fault: String,
    val safety: Int,
    val situationRef: Int? = null,
)

val INSPECTION_POINTS = listOf(
    InspectionPoint("extinguisher", "Огнетушитель", "Огнетушитель на месте, пломба цела.", "Крепление пустое: огнетушителя нет.", 12),
    InspectionPoint("firstaid", "Аптечка", "Аптечка укомплектована.", "В аптечке нет бинтов и антисептика.", 8),
    InspectionPoint("hammer", "Аварийный молоток", "Молоток на месте у окна.", "Аварийного молотка нет у окна.", 10),
    InspectionPoint("callbtn", "Кнопка вызова", "Кнопка вызова работает.", "Кнопка вызова не загорается.", 6, situationRef = 16),
    InspectionPoint("socket", "Розетка", "Розетка работает.", "Розетка у кресла не даёт питания.", 4, situationRef = 16),
    InspectionPoint("table", "Столик", "Столик чистый.", "Столик после прошлого рейса не убран.", 3, situationRef = 27),
)

/** Иконка точки осмотра для [ru.vsm.mobile.ui.art.Hotspot.icon] (набор понимает `HotspotButton`). */
private val INSPECT_ICON = mapOf(
    "extinguisher" to "alert",
    "firstaid" to "check",
    "hammer" to "alert",
    "callbtn" to "hand",
    "socket" to "alert",
    "table" to "check",
)

data class InspectionState(
    val point: InspectionPoint,
    val x: Float,
    val faulty: Boolean,
    val checked: Boolean = false,
) {
    val icon: String get() = INSPECT_ICON.getValue(point.key)
}

fun planInspection(cls: CarClass, rand: ShiftRng): List<InspectionState> {
    val faults = mutableSetOf<Int>()
    while (faults.size < 3) faults.add(rand.nextInt(INSPECTION_POINTS.size))
    val w = worldWidth(cls)
    fun midSeat(i: Int) = seatX(cls, i.coerceAtMost(cls.seats - 1))
    val positions = mapOf(
        "extinguisher" to 62f,
        "firstaid" to (w - 70f),
        "hammer" to (midSeat((cls.seats * 0.5f).toInt()) - cls.spacing * 0.35f),
        "callbtn" to midSeat((cls.seats * 0.25f).toInt()),
        "socket" to midSeat((cls.seats * 0.7f).toInt()),
        "table" to midSeat((cls.seats * 0.85f).toInt()),
    )
    return INSPECTION_POINTS.mapIndexed { i, p ->
        InspectionState(point = p, x = positions.getValue(p.key), faulty = faults.contains(i))
    }
}

// ---------------------------------------------------------------------------
// Инциденты рейса — из каталога backend (см. [planIncidents]) и локальные стрессовые ситуации.
// ---------------------------------------------------------------------------

enum class IncidentKind { BACKEND, STRESS }
enum class IncidentStatus { PENDING, ACTIVE, TALKING, DONE, MISSED }

data class TripIncident(
    val key: String,
    val kind: IncidentKind,
    val title: String,
    val block: String,
    val blockLabel: String,
    val urgent: Boolean,
    val seat: Int,
    val vestibule: Boolean = false,
    val mood: String? = null,
    val lookVariant: Int? = null,
    val scenarioId: String? = null,
    val stress: StressScenario? = null,
    val spawnAt: Float,
    val status: IncidentStatus = IncidentStatus.PENDING,
    val remaining: Float = 0f,
    val total: Float = 0f,
    val fromInspection: Boolean = false,
    val resultVerdict: String? = null,
    val resultLog: List<DialogLogEntry> = emptyList(),
)

val URGENT_BLOCKS = setOf("medical", "safety")

/** Выбирает до 3 сценариев каталога: один флагманский + два из других блоков, плюс всплывшие после приёмки. */
fun planIncidentSummaries(pool: List<ScenarioSummary>, hasPassengers: Boolean, rand: ShiftRng, extraRefs: List<Int>): List<ScenarioSummary> {
    if (pool.isEmpty() || !hasPassengers) return emptyList()
    val chosen = mutableListOf<ScenarioSummary>()
    val flagships = pool.filter { it.flagship }
    if (flagships.isNotEmpty()) chosen.add(rand.pick(flagships))
    var guard = 0
    while (chosen.size < 3 && guard < 200) {
        guard += 1
        val s = rand.pick(pool)
        if (chosen.none { it.id == s.id || it.block == s.block }) chosen.add(s)
    }
    extraRefs.forEach { ref ->
        val s = pool.firstOrNull { it.situationRef == ref }
        if (s != null && chosen.none { it.id == s.id }) chosen.add(s)
    }
    return chosen
}

// ---------------------------------------------------------------------------
// Итог смены
// ---------------------------------------------------------------------------

const val MISSED_PENALTY = 15
const val CRITICAL_VERDICT = "Критическая ошибка безопасности"

data class ShiftSummary(
    val safety: Int,
    val loyalty: Int,
    val admitted: Boolean,
    val upgrade: CarClass?,
    val critical: Boolean,
    val honest: Boolean,
    val medCorrect: Int,
    val medTotal: Int,
    val inspectionFound: Int,
    val inspectionFaults: Int,
    val inspectionMissed: List<InspectionState>,
    val incidentsDone: Int,
    val incidentsMissed: Int,
    val incidentsTotal: Int,
)

fun summarize(cls: DomainCarClass, medLog: List<DialogLogEntry>, inspection: List<InspectionState>, incidents: List<TripIncident>): ShiftSummary {
    val medSafety = medLog.sumOf { it.safety }
    val medLoyalty = medLog.sumOf { it.loyalty }
    val faults = inspection.filter { it.faulty }
    val found = faults.filter { it.checked }
    val inspectionSafety = found.sumOf { it.point.safety } - (faults.size - found.size) * 6
    val done = incidents.filter { it.status == IncidentStatus.DONE }
    val missed = incidents.filter { it.status == IncidentStatus.MISSED }
    val incSafety = done.sumOf { safetyOf(it) } - missed.size * MISSED_PENALTY
    val incLoyalty = done.sumOf { loyaltyOf(it) } - missed.size * MISSED_PENALTY
    val critical = done.any { it.resultVerdict == CRITICAL_VERDICT }
    val honestStep = medLog.firstOrNull { it.step == "health" }
    val honest = honestStep?.best ?: true

    val safety = medSafety + inspectionSafety + incSafety
    val loyalty = medLoyalty + incLoyalty
    val admitted = !critical && missed.isEmpty() && safety >= 0 && honest && found.size * 2 >= faults.size
    val idx = CLASS_ORDER.indexOf(cls)
    val upgrade = if (
        admitted && honest && found.size == faults.size &&
        done.all { it.resultVerdict == "Хорошо справились" } && idx < CLASS_ORDER.size - 1
    ) {
        artClassOf(CLASS_ORDER[idx + 1])
    } else null

    return ShiftSummary(
        safety = safety,
        loyalty = loyalty,
        admitted = admitted,
        upgrade = upgrade,
        critical = critical,
        honest = honest,
        medCorrect = medLog.count { it.best },
        medTotal = medLog.size,
        inspectionFound = found.size,
        inspectionFaults = faults.size,
        inspectionMissed = faults.filter { !it.checked },
        incidentsDone = done.size,
        incidentsMissed = missed.size,
        incidentsTotal = incidents.size,
    )
}

private fun safetyOf(i: TripIncident) = i.resultLog.sumOf { it.safety }
private fun loyaltyOf(i: TripIncident) = i.resultLog.sumOf { it.loyalty }

data class ShiftRings(val procedure: Float, val reaction: Float, val quality: Float)

fun ringsFor(summary: ShiftSummary, incidents: List<TripIncident>): ShiftRings {
    val procTotal = summary.medTotal + summary.inspectionFaults
    val procedure = if (procTotal > 0) (summary.medCorrect + summary.inspectionFound).toFloat() / procTotal else 0f
    val reaction = if (summary.incidentsTotal > 0) summary.incidentsDone.toFloat() / summary.incidentsTotal else 0f
    val q = incidents.map { i ->
        if (i.status != IncidentStatus.DONE) 0f
        else when (i.resultVerdict) {
            "Хорошо справились" -> 1f
            CRITICAL_VERDICT -> 0f
            else -> 0.5f
        }
    }
    val quality = if (q.isNotEmpty()) q.sum() / q.size else 0f
    return ShiftRings(procedure, reaction, quality)
}
