package ru.vsm.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.data.remote.ws.ProgressWebSocketClient
import ru.vsm.mobile.domain.model.ChoiceResult
import ru.vsm.mobile.domain.model.LiveProgressEvent
import ru.vsm.mobile.domain.model.ScenarioProgress
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.domain.repository.ScenarioRepository

class ScenarioRepositoryImpl(
    private val api: ScenarioApi,
    private val wsClient: ProgressWebSocketClient,
    private val safeApiCall: SafeApiCall,
) : ScenarioRepository {

    override suspend fun list(block: String?): Result<List<ScenarioSummary>> = safeApiCall.call {
        api.listScenarios(block).map { it.toDomain() }
    }

    override suspend fun start(scenarioId: String, playerId: String): Result<ScenarioProgress> = safeApiCall.call {
        api.start(scenarioId, playerId).toDomain()
    }

    override suspend fun getProgress(progressId: String, playerId: String): Result<ScenarioProgress> = safeApiCall.call {
        api.getProgress(progressId, playerId).toDomain()
    }

    override suspend fun choose(progressId: String, choiceId: String, playerId: String): Result<ChoiceResult> = safeApiCall.call {
        api.choose(progressId, choiceId, playerId).toDomain()
    }

    override suspend fun timeout(progressId: String, playerId: String): Result<ChoiceResult> = safeApiCall.call {
        api.timeout(progressId, playerId).toDomain()
    }

    override fun liveEvents(progressId: String, playerId: String): Flow<LiveProgressEvent> =
        wsClient.observe(progressId, playerId)
}
