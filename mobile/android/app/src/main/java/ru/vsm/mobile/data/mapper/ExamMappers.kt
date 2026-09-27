package ru.vsm.mobile.data.mapper

import ru.vsm.mobile.data.remote.dto.CarClassDto
import ru.vsm.mobile.data.remote.dto.ExamGradeDto
import ru.vsm.mobile.data.remote.dto.ExamResponseDto
import ru.vsm.mobile.data.remote.dto.ExamResultDto
import ru.vsm.mobile.data.remote.dto.ExamScenarioDto
import ru.vsm.mobile.data.remote.dto.ExamStatusDto
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.Exam
import ru.vsm.mobile.domain.model.ExamGrade
import ru.vsm.mobile.domain.model.ExamResult
import ru.vsm.mobile.domain.model.ExamScenarioItem
import ru.vsm.mobile.domain.model.ExamStatus

fun CarClassDto.toDomain(): CarClass = when (this) {
    CarClassDto.STANDARD -> CarClass.STANDARD
    CarClassDto.COMFORT -> CarClass.COMFORT
    CarClassDto.BUSINESS -> CarClass.BUSINESS
    CarClassDto.FIRST -> CarClass.FIRST
}

/** Обратное направление нужно [ru.vsm.mobile.data.repository.ExamRepositoryImpl] для query-параметра `carClass`. */
fun CarClass.toDto(): CarClassDto = when (this) {
    CarClass.STANDARD -> CarClassDto.STANDARD
    CarClass.COMFORT -> CarClassDto.COMFORT
    CarClass.BUSINESS -> CarClassDto.BUSINESS
    CarClass.FIRST -> CarClassDto.FIRST
}

fun ExamStatusDto.toDomain(): ExamStatus = when (this) {
    ExamStatusDto.IN_PROGRESS -> ExamStatus.IN_PROGRESS
    ExamStatusDto.COMPLETED -> ExamStatus.COMPLETED
}

fun ExamGradeDto.toDomain(): ExamGrade = when (this) {
    ExamGradeDto.EXCELLENT -> ExamGrade.EXCELLENT
    ExamGradeDto.GOOD -> ExamGrade.GOOD
    ExamGradeDto.SATISFACTORY -> ExamGrade.SATISFACTORY
    ExamGradeDto.UNSATISFACTORY -> ExamGrade.UNSATISFACTORY
}

fun ExamScenarioDto.toDomain(): ExamScenarioItem = ExamScenarioItem(
    sortOrder = sortOrder,
    scenarioId = scenarioId,
    scenarioCode = scenarioCode,
    block = block,
    title = title,
    flagship = flagship,
    userProgressId = userProgressId,
    completed = completed,
    outcome = outcome?.toDomain(),
    loyaltyScore = loyaltyScore,
    safetyScore = safetyScore,
)

fun ExamResultDto.toDomain(): ExamResult = ExamResult(
    avgLoyaltyScore = avgLoyaltyScore,
    avgSafetyScore = avgSafetyScore,
    successRate = successRate,
    grade = grade.toDomain(),
    weakBlocks = weakBlocks,
)

fun ExamResponseDto.toDomain(): Exam = Exam(
    examId = examId,
    playerId = playerId,
    carClass = carClass.toDomain(),
    status = status.toDomain(),
    size = size,
    currentIndex = currentIndex,
    startedAt = startedAt,
    finishedAt = finishedAt,
    scenarios = scenarios.map { it.toDomain() },
    result = result?.toDomain(),
)
