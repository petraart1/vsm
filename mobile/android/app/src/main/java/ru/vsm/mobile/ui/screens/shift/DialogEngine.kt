package ru.vsm.mobile.ui.screens.shift

/**
 * Формат локального графа диалога (заступ на смену, стрессовые ситуации рейса) — перенос формата
 * `useLocalDialog.js`/`stressScenarios.js`: узел с репликой и вариантами ответа, у варианта —
 * дельты шкал, реплика в ответ, разбор (`note`), пометка критической ошибки и ссылка на
 * следующий узел (`null` — граф завершён, показывается кнопка продолжения).
 */

/** Кто говорит реплику узла: passenger|neighbor|chief|guard|police|medic|narrator. */
data class DialogSpeaker(
    val kind: String, // "person" | "passenger"
    val name: String,
    val role: String,
    val outfit: String? = null,
    val variant: Int? = null,
    val mood: String? = null,
)

data class DialogChoice(
    val id: String,
    val text: String,
    val safety: Int,
    val loyalty: Int,
    val note: String,
    val reply: String? = null,
    val critical: Boolean = false,
    val next: String? = null,
)

data class DialogNode(
    val id: String,
    val speaker: String,
    val text: String,
    val context: String? = null,
    val timerSeconds: Int? = null,
    val choices: List<DialogChoice> = emptyList(),
    val end: Boolean = false,
    /** Переопределение шапки диалога для этого узла (заступ — фиксированные медик/начальник поезда). */
    val speakerOverride: DialogSpeaker? = null,
)

data class DialogScript(val startId: String, val nodes: Map<String, DialogNode>)

/** Собрать линейный (без ветвления) граф из последовательности шагов — заступ на смену. */
fun linearScript(steps: List<DialogNode>): DialogScript {
    val chained = steps.mapIndexed { i, step ->
        val nextId = steps.getOrNull(i + 1)?.id
        step.copy(choices = step.choices.map { it.copy(next = nextId) })
    }
    return DialogScript(startId = chained.first().id, nodes = chained.associateBy { it.id })
}

data class DialogLogEntry(
    val step: String,
    val choiceId: String,
    val safety: Int,
    val loyalty: Int,
    val best: Boolean,
    val critical: Boolean,
    val note: String,
    val question: String,
)

enum class DialogFrom { NPC, ME, NOTE }

data class DialogMessage(
    val id: String,
    val from: DialogFrom,
    val text: String? = null,
    val context: String? = null,
    val speakerLabel: String? = null,
    val safetyDelta: Int? = null,
    val loyaltyDelta: Int? = null,
    val bad: Boolean = false,
)

data class DialogUiState(
    val active: Boolean = false,
    val title: String = "",
    val role: String = "",
    val outfit: String? = null,
    val variant: Int? = null,
    val messages: List<DialogMessage> = emptyList(),
    val choices: List<DialogChoice>? = null,
    val busy: Boolean = false,
    val safety: Int = 60,
    val loyalty: Int = 60,
    val timerRemaining: Int? = null,
    val timerTotal: Int? = null,
    val final: Boolean = false,
)

data class DialogOutcome(val safety: Int, val loyalty: Int, val verdict: String, val log: List<DialogLogEntry>)

/** Итог локального диалога — перенос `localVerdict` из `stressScenarios.js`. */
fun localVerdict(log: List<DialogLogEntry>): String {
    if (log.any { it.critical }) return CRITICAL_VERDICT
    val safety = log.sumOf { it.safety }
    val loyalty = log.sumOf { it.loyalty }
    val allBest = log.isNotEmpty() && log.all { it.best }
    return if (allBest || (safety >= 20 && loyalty >= 0)) "Хорошо справились" else "Есть над чем поработать"
}

private fun clamp(v: Int) = v.coerceIn(0, 100)
fun clampScale(v: Int) = clamp(v)
