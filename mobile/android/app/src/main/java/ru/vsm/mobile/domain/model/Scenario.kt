package ru.vsm.mobile.domain.model

/** Элемент каталога сценариев (для списка/детали) — без графа узлов. */
data class ScenarioSummary(
    val id: String,
    val code: String,
    val situationRef: Int?,
    val block: String,
    val title: String,
    val description: String,
    val flagship: Boolean,
)

/** Вариант ответа, как его видит игрок ДО выбора — без раскрытия эффекта на шкалы. */
data class ChoiceOption(
    val id: String,
    val code: String,
    val text: String,
)

/**
 * Текущий (или только что показанный) узел графа для игрока.
 *
 * [deadlineAt] — момент времени в формате ISO-8601 (сервер — источник истины), после которого
 * выбор считается просроченным; `null`, если узел без таймера. [terminalOutcome]/[outcomeSummary]
 * заполнены только когда [terminal] == true, [choices] в этом случае пустой список.
 */
data class ScenarioNode(
    val nodeId: String,
    val code: String,
    val type: NodeType,
    val text: String,
    val terminal: Boolean,
    val timerSeconds: Int?,
    val deadlineAt: String?,
    val terminalOutcome: ScenarioOutcome?,
    val outcomeSummary: String?,
    val choices: List<ChoiceOption>,
)

/** Состояние прохождения: id, шкалы, текущий узел (`null`, если прохождение уже завершено). */
data class ScenarioProgress(
    val progressId: String,
    val scenarioId: String,
    val scenarioCode: String,
    val status: ProgressStatus,
    val loyaltyScore: Int,
    val safetyScore: Int,
    val currentNode: ScenarioNode?,
)

/**
 * Результат применения выбора (через REST или через серверный таймаут): раскрываются дельты и
 * новые значения шкал — кроме как в режиме экзамена. [nextNode] — `null`, если прохождение
 * завершилось этим выбором. [finalOutcome] заполнен только при `status == COMPLETED`.
 *
 * [loyaltyDelta]/[safetyDelta]/[loyaltyScore]/[safetyScore] — `null`, если это прохождение —
 * пункт экзамена (см. [ru.vsm.mobile.domain.repository.ExamRepository]): экзамен не должен
 * подсказывать игроку качество решения по ходу. Поля навигации (`status`/`finalOutcome`/
 * `nextNode`) при этом заполняются как обычно.
 */
data class ChoiceResult(
    val progressId: String,
    val appliedChoiceId: String,
    val appliedChoiceCode: String,
    val wasTimeout: Boolean,
    val loyaltyDelta: Int?,
    val safetyDelta: Int?,
    val loyaltyScore: Int?,
    val safetyScore: Int?,
    val status: ProgressStatus,
    val finalOutcome: ScenarioOutcome?,
    val nextNode: ScenarioNode?,
)
