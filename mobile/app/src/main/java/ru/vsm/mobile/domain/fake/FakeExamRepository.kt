package ru.vsm.mobile.domain.fake

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.Exam
import ru.vsm.mobile.domain.model.ExamGrade
import ru.vsm.mobile.domain.model.ExamResult
import ru.vsm.mobile.domain.model.ExamScenarioItem
import ru.vsm.mobile.domain.model.ExamStatus
import ru.vsm.mobile.domain.model.ProgressStatus
import ru.vsm.mobile.domain.model.ScenarioOutcome
import ru.vsm.mobile.domain.model.ScenarioProgress
import ru.vsm.mobile.domain.repository.ExamRepository
import ru.vsm.mobile.domain.repository.ScenarioRepository

/**
 * Фейковая реализация для UI-слоя до готовности сетевого data-слоя. Держит только список пунктов
 * экзамена и их агрегированное состояние — само прохождение каждого пункта делегирует уже
 * существующему [scenarioRepository] (обычно [FakeScenarioRepository]), как и в реальном клиенте
 * ([startCurrent]/дальнейшие выборы идут через [ScenarioRepository]).
 *
 * Ограничения фейка (не часть контракта [ExamRepository], только реализм демо-данных):
 * - размер экзамена ограничен числом сценариев, которые знает [scenarioRepository] (у
 *   [FakeScenarioRepository] их два) — запрошенный `size` больше этого числа тихо уменьшается;
 * - [ScenarioProgress] не хранит исход завершённого прохождения, поэтому [ScenarioOutcome] пункта
 *   здесь — эвристика по итоговому рейтингу безопасности, а не решение терминального узла графа,
 *   как на бэкенде.
 */
class FakeExamRepository(
    private val scenarioRepository: ScenarioRepository,
) : ExamRepository {

    private class ExamState(var exam: Exam)

    private val examsById = mutableMapOf<String, ExamState>()

    override suspend fun start(playerId: String, carClass: CarClass?, size: Int?): Result<Exam> {
        delay(200)
        val available = scenarioRepository.list().getOrElse { return Result.failure(it) }
        if (available.isEmpty()) {
            return apiError(400, DomainError.INVALID_ARGUMENT, "Нет доступных сценариев для экзамена")
        }
        val targetSize = (size ?: DEFAULT_SIZE).coerceIn(1, available.size)
        val items = available.take(targetSize).mapIndexed { index, summary ->
            ExamScenarioItem(
                sortOrder = index,
                scenarioId = summary.id,
                scenarioCode = summary.code,
                block = summary.block,
                title = summary.title,
                flagship = summary.flagship,
                userProgressId = null,
                completed = false,
                outcome = null,
                loyaltyScore = null,
                safetyScore = null,
            )
        }
        val exam = Exam(
            examId = UUID.randomUUID().toString(),
            playerId = playerId,
            carClass = carClass ?: CarClass.STANDARD,
            status = ExamStatus.IN_PROGRESS,
            size = items.size,
            currentIndex = 0,
            startedAt = Instant.now().toString(),
            finishedAt = null,
            scenarios = items,
            result = null,
        )
        examsById[exam.examId] = ExamState(exam)
        return Result.success(exam)
    }

    override suspend fun startCurrent(examId: String, playerId: String): Result<ScenarioProgress> {
        delay(150)
        val state = examsById[examId]
            ?: return apiError(404, DomainError.EXAM_NOT_FOUND, "Экзамен '$examId' не найден")
        refresh(state, playerId)
        val exam = state.exam
        if (exam.status == ExamStatus.COMPLETED) {
            return apiError(409, DomainError.EXAM_ALREADY_FINISHED, "Экзамен '$examId' уже завершён")
        }
        val current = exam.scenarios[exam.currentIndex]
        val existingProgressId = current.userProgressId
        if (existingProgressId != null) {
            return scenarioRepository.getProgress(existingProgressId, playerId)
        }
        return scenarioRepository.start(current.scenarioId, playerId).onSuccess { progress ->
            val updatedItems = exam.scenarios.toMutableList()
            updatedItems[exam.currentIndex] = current.copy(userProgressId = progress.progressId)
            state.exam = exam.copy(scenarios = updatedItems)
        }
    }

    override suspend fun get(examId: String, playerId: String): Result<Exam> {
        delay(120)
        val state = examsById[examId]
            ?: return apiError(404, DomainError.EXAM_NOT_FOUND, "Экзамен '$examId' не найден")
        refresh(state, playerId)
        return Result.success(state.exam)
    }

    /** Опрашивает незавершённые (но уже начатые) пункты и продвигает [ExamState.exam] по мере их завершения. */
    private suspend fun refresh(state: ExamState, playerId: String) {
        val exam = state.exam
        if (exam.status == ExamStatus.COMPLETED) return

        val updatedItems = exam.scenarios.map { item ->
            if (item.completed || item.userProgressId == null) return@map item
            val progress = scenarioRepository.getProgress(item.userProgressId, playerId).getOrNull()
            if (progress == null || progress.status != ProgressStatus.COMPLETED) return@map item
            item.copy(
                completed = true,
                outcome = heuristicOutcome(progress.safetyScore),
                loyaltyScore = progress.loyaltyScore,
                safetyScore = progress.safetyScore,
            )
        }

        val nextIndex = updatedItems.indexOfFirst { !it.completed }.let { if (it == -1) updatedItems.size else it }
        val allDone = nextIndex >= updatedItems.size
        state.exam = exam.copy(
            scenarios = updatedItems,
            currentIndex = nextIndex,
            status = if (allDone) ExamStatus.COMPLETED else ExamStatus.IN_PROGRESS,
            finishedAt = if (allDone) Instant.now().toString() else exam.finishedAt,
            result = if (allDone) buildResult(updatedItems) else null,
        )
    }

    private fun buildResult(items: List<ExamScenarioItem>): ExamResult {
        val avgLoyalty = items.mapNotNull { it.loyaltyScore }.average()
        val avgSafety = items.mapNotNull { it.safetyScore }.average()
        val successRate = items.count { it.outcome == ScenarioOutcome.SUCCESS }.toDouble() / items.size
        val weakBlocks = items.filter { it.outcome != ScenarioOutcome.SUCCESS }.map { it.block }.distinct()
        return ExamResult(avgLoyalty, avgSafety, successRate, computeGrade(avgLoyalty, avgSafety, successRate), weakBlocks)
    }

    /** Те же пороги, что и на бэкенде (см. javadoc серверного `ExamGrade`) — держим фейк реалистичным. */
    private fun computeGrade(avgLoyalty: Double, avgSafety: Double, successRate: Double): ExamGrade = when {
        successRate >= 0.8 && avgSafety >= 85 && avgLoyalty >= 70 -> ExamGrade.EXCELLENT
        successRate >= 0.6 && avgSafety >= 70 && avgLoyalty >= 55 -> ExamGrade.GOOD
        avgSafety >= 60 && (successRate >= 0.3 || avgLoyalty >= 40) -> ExamGrade.SATISFACTORY
        else -> ExamGrade.UNSATISFACTORY
    }

    private fun heuristicOutcome(safetyScore: Int): ScenarioOutcome = when {
        safetyScore >= 50 -> ScenarioOutcome.SUCCESS
        safetyScore >= 20 -> ScenarioOutcome.PARTIAL
        else -> ScenarioOutcome.FAILURE
    }

    private fun <T> apiError(statusCode: Int, errorCode: String, message: String): Result<T> =
        Result.failure(DomainError.Api(statusCode, errorCode, message))

    private companion object {
        const val DEFAULT_SIZE = 10
    }
}
