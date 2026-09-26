package ru.vsm.mobile.data.repository

import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.FeedbackApi
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Debrief
import ru.vsm.mobile.domain.repository.FeedbackRepository

class FeedbackRepositoryImpl(
    private val api: FeedbackApi,
    private val safeApiCall: SafeApiCall,
) : FeedbackRepository {

    /**
     * Единственный источник `409` на этом эндпоинте — незавершённый экзамен, к которому относится
     * прохождение (см. `ExamService.assertDebriefAllowed` на бэкенде). В отличие от остальных
     * ошибок API этот `409` приходит через `ResponseStatusException` без структурированного тела
     * `{error, message}` — [errorCode] от сервера непредсказуем (обычно HTTP reason phrase, не
     * машиночитаемый код), поэтому здесь любой `409` этого вызова нормализуется в
     * [DomainError.DEBRIEF_UNAVAILABLE_DURING_EXAM] независимо от того, что пришло в теле.
     */
    override suspend fun getDebrief(userProgressId: String): Result<Debrief> = safeApiCall.call {
        api.getDebrief(userProgressId).toDomain()
    }.recoverCatching { error ->
        if (error is DomainError.Api && error.statusCode == 409) {
            throw error.copy(errorCode = DomainError.DEBRIEF_UNAVAILABLE_DURING_EXAM)
        }
        throw error
    }

    override suspend fun getCompetencies(playerId: String): Result<CompetencyAnalytics> = safeApiCall.call {
        api.getCompetencies(playerId).toDomain()
    }
}
