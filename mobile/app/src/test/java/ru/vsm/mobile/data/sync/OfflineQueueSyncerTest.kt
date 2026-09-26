package ru.vsm.mobile.data.sync

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.vsm.mobile.data.local.FakeOfflineQueueStore
import ru.vsm.mobile.data.local.OfflineQueueEntry
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.domain.error.DomainError

/** [OfflineQueueSyncer]: маппинг ответов backend и порядок отправки накопленной очереди. */
class OfflineQueueSyncerTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ScenarioApi
    private lateinit var safeApiCall: SafeApiCall

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        api = retrofit.create(ScenarioApi::class.java)
        safeApiCall = SafeApiCall(json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Отменённый scope — фоновый `init`-запуск синхронизатора (`Channel`/`isOnline`) не выполняется. */
    private fun deadScope(): CoroutineScope = CoroutineScope(SupervisorJob()).apply { cancel() }

    private fun choiceAppliedBody(progressId: String, choiceCode: String) = """
        {
          "progressId": "$progressId",
          "appliedChoiceId": "c1",
          "appliedChoiceCode": "$choiceCode",
          "wasTimeout": false,
          "loyaltyDelta": 1,
          "safetyDelta": 1,
          "loyaltyScore": 1,
          "safetyScore": 1,
          "status": "IN_PROGRESS",
          "finalOutcome": null,
          "nextNode": null
        }
    """.trimIndent()

    @Test
    fun `sendOne maps network failure without touching the queue`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val syncer = OfflineQueueSyncer(FakeOfflineQueueStore(), api, safeApiCall, isOnline = emptyFlow(), scope = deadScope())

        val outcome = syncer.sendOne(OfflineQueueEntry("1", "p1", "player-1", "c1", "2026-01-01T00:00:00Z"))

        assertTrue(outcome is QueueSendOutcome.NetworkFailure)
    }

    @Test
    fun `sendOne maps 409 progress already completed to AlreadyApplied`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error": "progress_already_completed", "message": "Прохождение уже завершено"}""",
            ),
        )
        val syncer = OfflineQueueSyncer(FakeOfflineQueueStore(), api, safeApiCall, isOnline = emptyFlow(), scope = deadScope())

        val outcome = syncer.sendOne(OfflineQueueEntry("1", "p1", "player-1", "c1", "2026-01-01T00:00:00Z"))

        assertTrue(outcome is QueueSendOutcome.AlreadyApplied)
    }

    @Test
    fun `sendOne maps other 4xx to Rejected`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error": "choice_not_available", "message": "Выбор недоступен"}""",
            ),
        )
        val syncer = OfflineQueueSyncer(FakeOfflineQueueStore(), api, safeApiCall, isOnline = emptyFlow(), scope = deadScope())

        val outcome = syncer.sendOne(OfflineQueueEntry("1", "p1", "player-1", "c1", "2026-01-01T00:00:00Z"))

        assertTrue(outcome is QueueSendOutcome.Rejected)
        assertEquals(DomainError.CHOICE_NOT_AVAILABLE, (outcome as QueueSendOutcome.Rejected).error.errorCode)
    }

    @Test
    fun `flushes queued entries in order and removes delivered and 409 entries`() = runTest {
        val queue = FakeOfflineQueueStore()
        queue.enqueue(OfflineQueueEntry("1", "p1", "player-1", "c1", "2026-01-01T00:00:00Z"))
        queue.enqueue(OfflineQueueEntry("2", "p2", "player-1", "c2", "2026-01-01T00:00:01Z"))
        queue.enqueue(OfflineQueueEntry("3", "p3", "player-1", null, "2026-01-01T00:00:02Z"))

        // Первый — успешно применён, второй — уже применён раньше (409), третий (timeout) — тоже успех.
        server.enqueue(MockResponse().setResponseCode(200).setBody(choiceAppliedBody("p1", "c1")))
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error": "progress_already_completed", "message": "Прохождение уже завершено"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(choiceAppliedBody("p3", "timeout-default")))

        val syncer = OfflineQueueSyncer(queue, api, safeApiCall, isOnline = emptyFlow(), scope = deadScope())
        val emptied = syncer.flushQueueOnce()

        assertTrue(emptied)
        assertEquals(0, queue.pendingCount.value)
        assertTrue(queue.snapshot().isEmpty())

        val firstRequest = server.takeRequest()
        assertTrue(firstRequest.path!!.contains("/progress/p1/choices/c1"))
        val secondRequest = server.takeRequest()
        assertTrue(secondRequest.path!!.contains("/progress/p2/choices/c2"))
        val thirdRequest = server.takeRequest()
        assertTrue(thirdRequest.path!!.contains("/progress/p3/timeout"))
    }
}
