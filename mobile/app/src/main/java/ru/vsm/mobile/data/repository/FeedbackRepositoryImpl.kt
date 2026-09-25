package ru.vsm.mobile.data.repository

import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.FeedbackApi
import ru.vsm.mobile.domain.model.CompetencyAnalytics
import ru.vsm.mobile.domain.model.Debrief
import ru.vsm.mobile.domain.repository.FeedbackRepository

class FeedbackRepositoryImpl(
    private val api: FeedbackApi,
    private val safeApiCall: SafeApiCall,
) : FeedbackRepository {

    override suspend fun getDebrief(userProgressId: String): Result<Debrief> = safeApiCall.call {
        api.getDebrief(userProgressId).toDomain()
    }

    override suspend fun getCompetencies(playerId: String): Result<CompetencyAnalytics> = safeApiCall.call {
        api.getCompetencies(playerId).toDomain()
    }
}
