package ru.vsm.mobile.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.vsm.mobile.data.local.FakeOfflineQueueStore
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.data.remote.ws.ProgressWebSocketClient
import ru.vsm.mobile.data.sync.OfflineQueueSyncer
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.error.isConflict
import ru.vsm.mobile.domain.error.isNotFound
import ru.vsm.mobile.domain.error.isProgressAlreadyCompleted
import ru.vsm.mobile.domain.model.ChoiceOutcome
import ru.vsm.mobile.domain.model.ProgressStatus

/** MockWebServer: [ScenarioRepositoryImpl] на реальном Retrofit + kotlinx.serialization, без похода в сеть. */
class ScenarioRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ScenarioRepositoryImpl
    private lateinit var queueStore: FakeOfflineQueueStore

    /**
     * Scope с уже отменённым job — [OfflineQueueSyncer.init] запускает на нём фоновую отправку
     * очереди, но она никогда не выполнится (launch на отменённом scope — no-op), поэтому тесты
     * этого класса проверяют только сам факт постановки в очередь [ScenarioRepositoryImpl], без
     * интерференции с фоновым флашем — он покрыт отдельно в [ru.vsm.mobile.data.sync.OfflineQueueSyncerTest].
     */
    private fun deadScope(): CoroutineScope = CoroutineScope(SupervisorJob()).apply { cancel() }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(ScenarioApi::class.java)
        val wsClient = ProgressWebSocketClient(okhttp3.OkHttpClient(), json, server.url("/").toString())
        val safeApiCall = SafeApiCall(json)
        queueStore = FakeOfflineQueueStore()
        val syncer = OfflineQueueSyncer(queueStore, api, safeApiCall, isOnline = emptyFlow(), scope = deadScope())
        repository = ScenarioRepositoryImpl(api, wsClient, safeApiCall, queueStore, syncer)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `start maps successful response to domain progress`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "progressId": "p1",
                  "scenarioId": "s1",
                  "scenarioCode": "boarding-no-ticket",
                  "status": "IN_PROGRESS",
                  "loyaltyScore": 0,
                  "safetyScore": 0,
                  "currentNode": {
                    "nodeId": "n1",
                    "code": "start",
                    "type": "DIALOGUE",
                    "text": "У турникета пассажир торопится.",
                    "terminal": false,
                    "timerSeconds": null,
                    "deadlineAt": null,
                    "terminalOutcome": null,
                    "outcomeSummary": null,
                    "choices": [ {"id": "c1", "code": "check-ticket", "text": "Проверить билет"} ]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.start("s1", "player-1")

        assertTrue(result.isSuccess)
        val progress = result.getOrThrow()
        assertEquals("p1", progress.progressId)
        assertEquals(ProgressStatus.IN_PROGRESS, progress.status)
        assertEquals(1, progress.currentNode?.choices?.size)
        assertEquals("check-ticket", progress.currentNode?.choices?.first()?.code)

        val recorded = server.takeRequest()
        assertEquals("player-1", recorded.getHeader(ScenarioApi.PLAYER_ID_HEADER))
    }

    @Test
    fun `getProgress maps 404 to not found domain error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error": "progress_not_found", "message": "Прохождение 'p404' не найдено"}""",
            ),
        )

        val result = repository.getProgress("p404", "player-1")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as DomainError.Api
        assertEquals(404, error.statusCode)
        assertEquals(DomainError.PROGRESS_NOT_FOUND, error.errorCode)
        assertTrue(error.isNotFound())
        assertFalse(error.isConflict())
    }

    @Test
    fun `choose maps 409 to progress already completed`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error": "progress_already_completed", "message": "Прохождение уже завершено"}""",
            ),
        )

        val result = repository.choose("p1", "c1", "player-1")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as DomainError.Api
        assertTrue(error.isConflict())
        assertTrue(error.isProgressAlreadyCompleted())
    }

    @Test
    fun `choose maps exam mode response with hidden deltas and scores to null`() = runTest {
        // Пункт экзамена: ChoiceAppliedResponse сериализует loyaltyDelta/safetyDelta/loyaltyScore/
        // safetyScore как явный JSON null (см. javadoc ChoiceAppliedResponse на бэкенде) — навигация
        // (status/finalOutcome/nextNode) раскрывается как обычно.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "progressId": "p1",
                  "appliedChoiceId": "c1",
                  "appliedChoiceCode": "check-ticket",
                  "wasTimeout": false,
                  "carClass": "STANDARD",
                  "loyaltyDelta": null,
                  "safetyDelta": null,
                  "loyaltyScore": null,
                  "safetyScore": null,
                  "status": "COMPLETED",
                  "finalOutcome": "SUCCESS",
                  "nextNode": null
                }
                """.trimIndent(),
            ),
        )

        val result = repository.choose("p1", "c1", "player-1")

        assertTrue(result.isSuccess)
        val choiceResult = result.getOrThrow()
        assertNull(choiceResult.loyaltyDelta)
        assertNull(choiceResult.safetyDelta)
        assertNull(choiceResult.loyaltyScore)
        assertNull(choiceResult.safetyScore)
        assertEquals(ProgressStatus.COMPLETED, choiceResult.status)
    }

    @Test
    fun `chooseOrQueue saves to offline queue on network error instead of failing`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = repository.chooseOrQueue("p1", "c1", "player-1")

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue(outcome is ChoiceOutcome.QueuedOffline)
        outcome as ChoiceOutcome.QueuedOffline
        assertEquals("p1", outcome.progressId)
        assertEquals("c1", outcome.choiceId)

        assertEquals(1, queueStore.pendingCount.value)
        val queued = queueStore.snapshot().single()
        assertEquals("p1", queued.progressId)
        assertEquals("c1", queued.choiceId)
        assertEquals("player-1", queued.playerId)
    }

    @Test
    fun `timeoutOrQueue saves to offline queue with null choiceId on network error`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = repository.timeoutOrQueue("p1", "player-1")

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow() as ChoiceOutcome.QueuedOffline
        assertNull(outcome.choiceId)
        assertEquals(1, queueStore.pendingCount.value)
        assertNull(queueStore.snapshot().single().choiceId)
    }

    @Test
    fun `chooseOrQueue still fails normally on non-network errors`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error": "choice_not_available", "message": "Выбор недоступен"}""",
            ),
        )

        val result = repository.chooseOrQueue("p1", "c1", "player-1")

        assertTrue(result.isFailure)
        assertEquals(0, queueStore.pendingCount.value)
        val error = result.exceptionOrNull() as DomainError.Api
        assertEquals(DomainError.CHOICE_NOT_AVAILABLE, error.errorCode)
    }
}
