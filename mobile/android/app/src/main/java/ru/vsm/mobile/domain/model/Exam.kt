package ru.vsm.mobile.domain.model

/** Статус экзамена целиком (не отдельного пункта в его составе — см. [ExamScenarioItem.completed]). */
enum class ExamStatus {
    IN_PROGRESS,
    COMPLETED,
}

/**
 * Итоговая оценка экзамена. Рейтинг безопасности — определяющий фактор: экзамен не может быть
 * оценён выше [SATISFACTORY], если средняя безопасность ниже 60, вне зависимости от лояльности и
 * доли успешных пунктов (точные пороги считает бэкенд).
 */
enum class ExamGrade {
    EXCELLENT,
    GOOD,
    SATISFACTORY,
    UNSATISFACTORY,
}

/**
 * Один пункт экзамена в порядке прохождения. [userProgressId] — `null`, пока игрок ещё не начал
 * этот пункт (см. [ru.vsm.mobile.domain.repository.ExamRepository.startCurrent]).
 * [outcome]/[loyaltyScore]/[safetyScore] заполняются только когда [completed] == true.
 */
data class ExamScenarioItem(
    val sortOrder: Int,
    val scenarioId: String,
    val scenarioCode: String,
    val block: String,
    val title: String,
    val flagship: Boolean,
    val userProgressId: String?,
    val completed: Boolean,
    val outcome: ScenarioOutcome?,
    val loyaltyScore: Int?,
    val safetyScore: Int?,
)

/**
 * Итог завершённого экзамена (средние шкалы и доля [ScenarioOutcome.SUCCESS] по всем пунктам).
 * [weakBlocks] — блоки, где пункт экзамена не завершился успехом, от худшего к менее слабому —
 * подсказка, что подтянуть перед пересдачей.
 */
data class ExamResult(
    val avgLoyaltyScore: Double,
    val avgSafetyScore: Double,
    val successRate: Double,
    val grade: ExamGrade,
    val weakBlocks: List<String>,
)

/**
 * Состояние экзамена целиком: набор сценариев, пройденных подряд без раскрытия шкал по ходу (см.
 * [ChoiceResult] — во время экзамена дельты/шкалы не приходят). [currentIndex] — позиция первого
 * не пройденного пункта в [scenarios]. [result] — `null`, пока [status] не [ExamStatus.COMPLETED].
 */
data class Exam(
    val examId: String,
    val playerId: String,
    val carClass: CarClass,
    val status: ExamStatus,
    val size: Int,
    val currentIndex: Int,
    val startedAt: String,
    val finishedAt: String?,
    val scenarios: List<ExamScenarioItem>,
    val result: ExamResult?,
)
