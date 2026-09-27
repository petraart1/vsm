package ru.vsm.mobile.data.repository

import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.mapper.toDto
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ExamApi
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.Exam
import ru.vsm.mobile.domain.model.ScenarioProgress
import ru.vsm.mobile.domain.repository.ExamRepository

class ExamRepositoryImpl(
    private val api: ExamApi,
    private val safeApiCall: SafeApiCall,
) : ExamRepository {

    override suspend fun start(playerId: String, carClass: CarClass?, size: Int?): Result<Exam> = safeApiCall.call {
        api.create(playerId, carClass?.toDto(), size).toDomain()
    }

    override suspend fun startCurrent(examId: String, playerId: String): Result<ScenarioProgress> = safeApiCall.call {
        api.startCurrent(examId, playerId).toDomain()
    }

    override suspend fun get(examId: String, playerId: String): Result<Exam> = safeApiCall.call {
        api.get(examId, playerId).toDomain()
    }
}
