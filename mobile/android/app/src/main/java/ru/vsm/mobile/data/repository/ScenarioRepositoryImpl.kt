package ru.vsm.mobile.data.repository

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import ru.vsm.mobile.data.local.OfflineQueueEntry
import ru.vsm.mobile.data.local.OfflineQueueStore
import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.data.remote.ws.ProgressWebSocketClient
import ru.vsm.mobile.data.sync.OfflineQueueSyncer
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.ChoiceOutcome
import ru.vsm.mobile.domain.model.ChoiceResult
import ru.vsm.mobile.domain.model.LiveProgressEvent
import ru.vsm.mobile.domain.model.ScenarioProgress
import ru.vsm.mobile.domain.model.ScenarioSummary
import ru.vsm.mobile.domain.repository.ScenarioRepository

class ScenarioRepositoryImpl(
    private val api: ScenarioApi,
    private val wsClient: ProgressWebSocketClient,
    private val safeApiCall: SafeApiCall,
    private val offlineQueueStore: OfflineQueueStore,
    private val offlineQueueSyncer: OfflineQueueSyncer,
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

    override fun liveEvents(progressId: String, playerId: String, token: String?): Flow<LiveProgressEvent> =
        wsClient.observe(progressId, playerId, token)

    override suspend fun chooseOrQueue(progressId: String, choiceId: String, playerId: String): Result<ChoiceOutcome> =
        applyOrQueue(progressId, playerId, choiceId) { api.choose(progressId, choiceId, playerId).toDomain() }

    override suspend fun timeoutOrQueue(progressId: String, playerId: String): Result<ChoiceOutcome> =
        applyOrQueue(progressId, playerId, choiceId = null) { api.timeout(progressId, playerId).toDomain() }

    override fun pendingOfflineCount(): Flow<Int> = offlineQueueStore.pendingCount

    private suspend fun applyOrQueue(
        progressId: String,
        playerId: String,
        choiceId: String?,
        block: suspend () -> ChoiceResult,
    ): Result<ChoiceOutcome> {
        val result = safeApiCall.call(block)
        return result.fold(
            onSuccess = { Result.success(ChoiceOutcome.Applied(it)) },
            onFailure = { error ->
                if (error is DomainError.Network) {
                    val queuedAt = Instant.now().toString()
                    offlineQueueStore.enqueue(
                        OfflineQueueEntry(
                            id = UUID.randomUUID().toString(),
                            progressId = progressId,
                            playerId = playerId,
                            choiceId = choiceId,
                            queuedAt = queuedAt,
                        ),
                    )
                    // Опортунистическая попытка — если сеть уже вернулась к моменту постановки в
                    // очередь, элемент не будет ждать следующего события ConnectivityManager.
                    offlineQueueSyncer.requestFlush()
                    Result.success(ChoiceOutcome.QueuedOffline(progressId, choiceId, queuedAt))
                } else {
                    Result.failure(error)
                }
            },
        )
    }
}
