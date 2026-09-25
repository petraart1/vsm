package ru.vsm.mobile.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.data.remote.ws.ProgressWebSocketClient
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.error.isConflict
import ru.vsm.mobile.domain.error.isNotFound
import ru.vsm.mobile.domain.error.isProgressAlreadyCompleted
import ru.vsm.mobile.domain.model.ProgressStatus

/** MockWebServer: [ScenarioRepositoryImpl] на реальном Retrofit + kotlinx.serialization, без похода в сеть. */
class ScenarioRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ScenarioRepositoryImpl

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
        repository = ScenarioRepositoryImpl(api, wsClient, SafeApiCall(json))
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
}
