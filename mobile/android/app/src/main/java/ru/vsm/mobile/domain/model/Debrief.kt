package ru.vsm.mobile.domain.model

/**
 * Один шаг таймлайна разбора прохождения: реплика/ситуация узла -> выбор игрока -> эффект на
 * шкалы -> какие шаги ролевой модели соблюдены/пропущены -> объяснение.
 *
 * @param roleStepsCompleted человекочитаемые подписи соблюдённых шагов ролевой модели
 * @param roleStepsSkipped человекочитаемые подписи пропущенных шагов ролевой модели
 * @param scaleConflict true, если выбор — осознанный компромисс шкал (дельты разного знака)
 * @param hiddenCommunicationEffect true, если решение принято в узле-эскалации, где формулировка
 *   меняет исход "за кадром" (пассажир разговор не слышит), не для пассажира
 */
data class DebriefStep(
    val sequenceIndex: Int,
    val nodeCode: String,
    val nodeText: String,
    val nodeType: NodeType,
    val choiceCode: String,
    val choiceText: String,
    val wasTimeout: Boolean,
    val loyaltyDelta: Int,
    val safetyDelta: Int,
    val roleStepsCompleted: List<String>,
    val roleStepsSkipped: List<String>,
    val scaleConflict: Boolean,
    val explanation: String,
    val hiddenCommunicationEffect: Boolean,
)

/**
 * Блок "Что можно было сделать иначе": ключевая развилка, где игрок принял неоптимальное решение
 * — фактический выбор против лучшей альтернативы в том же узле.
 */
data class KeyMoment(
    val sequenceIndex: Int,
    val nodeText: String,
    val chosenChoiceText: String,
    val chosenLoyaltyDelta: Int,
    val chosenSafetyDelta: Int,
    val betterChoiceText: String,
    val betterLoyaltyDelta: Int,
    val betterSafetyDelta: Int,
    val adviceText: String,
    val betterExplanation: String,
)

/**
 * Разбор одного прохождения сценария.
 *
 * @param interrupted true, если прохождение завершилось не обычным терминальным узлом
 * @param keyMoment `null`, если прохождение идеально — тогда смотреть [summary]
 * @param normReferences ссылки на нормы сделанных выборов, без дублей
 */
data class Debrief(
    val userProgressId: String,
    val scenarioId: String,
    val scenarioCode: String,
    val scenarioTitle: String,
    val scenarioBlock: String,
    val progressStatus: ProgressStatus,
    val outcome: ScenarioOutcome?,
    val verdict: String,
    val interrupted: Boolean,
    val finalLoyaltyScore: Int,
    val finalSafetyScore: Int,
    val timeline: List<DebriefStep>,
    val keyMoment: KeyMoment?,
    val summary: String,
    val normReferences: List<String>,
)
