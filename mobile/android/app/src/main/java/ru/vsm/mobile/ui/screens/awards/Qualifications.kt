package ru.vsm.mobile.ui.screens.awards

import java.util.Locale
import ru.vsm.mobile.domain.model.BlockProgress
import ru.vsm.mobile.domain.model.ScenarioSummary

/**
 * Учебные модули и официальные награды — перенос `progress.js`/`awards.js` с сайта, адаптированный
 * под то, что реально доступно клиенту: профиль отдаёт агрегаты по блоку ([BlockProgress] — число
 * пройденных ситуаций и сумму очков), не историю каждого прохождения, поэтому требование
 * «нет критических ошибок» здесь не проверяется отдельно — только средние шкалы и покрытие блока.
 */

data class ModuleMeta(val code: String, val title: String)

/** Модуль соответствует блоку ситуаций каталога — тот же список, что и в `dataset/scenarios`. */
val MODULES: Map<String, ModuleMeta> = linkedMapOf(
    "boarding" to ModuleMeta("01", "Посадка и контроль проездных документов"),
    "baggage" to ModuleMeta("02", "Перевозка багажа, ручной клади и животных"),
    "safety" to ModuleMeta("03", "Обеспечение общественного порядка на борту"),
    "seating" to ModuleMeta("04", "Размещение пассажиров и смена класса обслуживания"),
    "catering" to ModuleMeta("05", "Сервис питания и платные услуги"),
    "medical" to ModuleMeta("06", "Действия при медицинских и экстренных ситуациях"),
    "lost_found" to ModuleMeta("07", "Работа с находками, утерями и обращениями"),
    "conflict" to ModuleMeta("08", "Урегулирование конфликтных ситуаций в поезде"),
    "comfort" to ModuleMeta("09", "Комфорт пассажиров и бытовые обращения"),
    "misc" to ModuleMeta("10", "Нестандартные запросы пассажиров"),
)

/** Глиф медали модуля — набор гравировок медали ýже, чем иконки на сайте, берём ближайший по смыслу. */
val MODULE_GLYPH: Map<String, String> = mapOf(
    "boarding" to "clipboard",
    "baggage" to "medal",
    "safety" to "shield",
    "seating" to "medal",
    "catering" to "sparkle",
    "medical" to "check",
    "lost_found" to "flag",
    "conflict" to "bolt",
    "comfort" to "sparkle",
    "misc" to "medal",
)

private const val MIN_AVG_SAFETY = 10
private const val MIN_AVG_LOYALTY = 5

data class QualificationRequirement(val label: String, val detail: String, val met: Boolean)

data class QualificationUi(
    val block: String,
    val code: String,
    val title: String,
    val status: String, // certified | in_training | not_started
    val completed: Int,
    val total: Int,
    val requirements: List<QualificationRequirement>,
)

/** Квалификации по блокам ситуаций из каталога сценариев и агрегатов профиля игрока. */
fun buildQualifications(scenarios: List<ScenarioSummary>, blockProgress: List<BlockProgress>): List<QualificationUi> {
    val totalsByBlock = scenarios.groupingBy { it.block }.eachCount()
    val progressByBlock = blockProgress.associateBy { it.block }
    val ordered = MODULES.keys.filter { totalsByBlock.containsKey(it) } +
        totalsByBlock.keys.filter { it !in MODULES }

    return ordered.map { block ->
        val meta = MODULES[block] ?: ModuleMeta("--", block.replaceFirstChar { c -> c.uppercase() })
        val total = totalsByBlock[block] ?: 0
        val bp = progressByBlock[block]
        val done = bp?.scenariosCompleted?.coerceAtMost(total) ?: 0
        val avgSafety = if (bp != null && done > 0) bp.safetyPoints.toDouble() / done else null
        val avgLoyalty = if (bp != null && done > 0) bp.loyaltyPoints.toDouble() / done else null

        val requirements = listOf(
            QualificationRequirement(
                label = "Пройдены все ситуации модуля",
                detail = "$done из $total",
                met = total > 0 && done >= total,
            ),
            QualificationRequirement(
                label = "Средний рейтинг безопасности не ниже $MIN_AVG_SAFETY",
                detail = avgSafety?.let(::formatAvg) ?: "нет данных",
                met = avgSafety != null && avgSafety >= MIN_AVG_SAFETY,
            ),
            QualificationRequirement(
                label = "Средняя лояльность пассажиров не ниже $MIN_AVG_LOYALTY",
                detail = avgLoyalty?.let(::formatAvg) ?: "нет данных",
                met = avgLoyalty != null && avgLoyalty >= MIN_AVG_LOYALTY,
            ),
        )

        val certified = requirements.all { it.met }
        val status = if (certified) "certified" else if (done > 0) "in_training" else "not_started"
        QualificationUi(block, meta.code, meta.title, status, done, total, requirements)
    }
}

/** Среднее с одним знаком после запятой: округление до целого показало бы «сейчас 10» при 9,5 < 10. */
private fun formatAvg(n: Double): String {
    val rounded = Math.round(n * 10) / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else String.format(Locale("ru"), "%.1f", rounded).replace('.', ',')
}

// ---------------------------------------------------------------------------
// Официальные награды — доступны только подтверждённым (через демо-ЕСИА) учётным записям.
// ---------------------------------------------------------------------------

data class OfficialAwardUi(val id: String, val title: String, val note: String, val glyph: String, val condition: Boolean)

fun officialAwards(admittedShifts: Int, hasUpgrade: Boolean, anyModuleCertified: Boolean): List<OfficialAwardUi> = listOf(
    OfficialAwardUi("official:admission", "Допуск к самостоятельной работе", "Смена с подтверждённым допуском", "train", admittedShifts > 0),
    OfficialAwardUi("official:upgrade", "Рекомендация к повышению класса", "Смена без единой ошибки", "sparkle", hasUpgrade),
    OfficialAwardUi("official:five", "Пять смен без замечаний", "Стабильный результат в рейсах", "flag", admittedShifts >= 5),
    OfficialAwardUi("official:certificate", "Свидетельство по модулю", "Квалификация по учебному модулю", "clipboard", anyModuleCertified),
)
