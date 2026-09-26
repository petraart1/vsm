package ru.vsm.mobile.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.ExamApi
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.domain.model.CarClass
import ru.vsm.mobile.domain.model.ExamGrade
import ru.vsm.mobile.domain.model.ExamStatus
import ru.vsm.mobile.domain.model.ScenarioOutcome

/** MockWebServer: [ExamRepositoryImpl] на реальном Retrofit + kotlinx.serialization, без похода в сеть. */
class ExamRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ExamRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        repository = ExamRepositoryImpl(retrofit.create(ExamApi::class.java), SafeApiCall(json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `start maps freshly created exam with unstarted scenarios`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                {
                  "examId": "exam-1",
                  "playerId": "player-1",
                  "carClass": "STANDARD",
                  "status": "IN_PROGRESS",
                  "size": 2,
                  "currentIndex": 0,
                  "startedAt": "2026-09-26T10:00:00Z",
                  "finishedAt": null,
                  "scenarios": [
                    {
                      "sortOrder": 0, "scenarioId": "s1", "scenarioCode": "boarding-no-ticket",
                      "block": "boarding", "title": "Посадка без билета", "flagship": false,
                      "userProgressId": null, "completed": false, "outcome": null,
                      "loyaltyScore": null, "safetyScore": null
                    },
                    {
                      "sortOrder": 1, "scenarioId": "s2", "scenarioCode": "medical-passenger-unwell",
                      "block": "medical", "title": "Пассажиру стало плохо", "flagship": true,
                      "userProgressId": null, "completed": false, "outcome": null,
                      "loyaltyScore": null, "safetyScore": null
                    }
                  ],
                  "result": null
                }
                """.trimIndent(),
            ),
        )

        val result = repository.start("player-1", CarClass.STANDARD, size = 2)

        assertTrue(result.isSuccess)
        val exam = result.getOrThrow()
        assertEquals("exam-1", exam.examId)
        assertEquals(ExamStatus.IN_PROGRESS, exam.status)
        assertEquals(2, exam.scenarios.size)
        assertNull(exam.scenarios.first().userProgressId)
        assertNull(exam.result)

        val recorded = server.takeRequest()
        assertEquals("player-1", recorded.getHeader(ScenarioApi.PLAYER_ID_HEADER))
        assertTrue(recorded.path?.contains("carClass=STANDARD") == true)
        assertTrue(recorded.path?.contains("size=2") == true)
    }

    @Test
    fun `startCurrent maps progress state without carClass-hiding of scores`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "progressId": "p1",
                  "scenarioId": "s1",
                  "scenarioCode": "boarding-no-ticket",
                  "status": "IN_PROGRESS",
                  "loyaltyScore": 0,
                  "safetyScore": 0,
                  "currentNode": null
                }
                """.trimIndent(),
            ),
        )

        val result = repository.startCurrent("exam-1", "player-1")

        assertTrue(result.isSuccess)
        assertEquals("p1", result.getOrThrow().progressId)
    }

    @Test
    fun `get maps completed exam with grade and weak blocks`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "examId": "exam-1",
                  "playerId": "player-1",
                  "carClass": "STANDARD",
                  "status": "COMPLETED",
                  "size": 1,
                  "currentIndex": 1,
                  "startedAt": "2026-09-26T10:00:00Z",
                  "finishedAt": "2026-09-26T10:05:00Z",
                  "scenarios": [
                    {
                      "sortOrder": 0, "scenarioId": "s1", "scenarioCode": "boarding-no-ticket",
                      "block": "boarding", "title": "Посадка без билета", "flagship": false,
                      "userProgressId": "p1", "completed": true, "outcome": "FAILURE",
                      "loyaltyScore": 10, "safetyScore": 20
                    }
                  ],
                  "result": {
                    "avgLoyaltyScore": 10.0,
                    "avgSafetyScore": 20.0,
                    "successRate": 0.0,
                    "grade": "UNSATISFACTORY",
                    "weakBlocks": ["boarding"]
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.get("exam-1", "player-1")

        assertTrue(result.isSuccess)
        val exam = result.getOrThrow()
        assertEquals(ExamStatus.COMPLETED, exam.status)
        val examResult = exam.result
        assertTrue(examResult != null)
        assertEquals(ExamGrade.UNSATISFACTORY, examResult!!.grade)
        assertEquals(listOf("boarding"), examResult.weakBlocks)
        assertEquals(ScenarioOutcome.FAILURE, exam.scenarios.first().outcome)
    }
}
