package ru.vsm.mobile.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.FeedbackApi
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.error.isConflict
import ru.vsm.mobile.domain.error.isDebriefUnavailableDuringExam

/** MockWebServer: [FeedbackRepositoryImpl] на реальном Retrofit + kotlinx.serialization, без похода в сеть. */
class FeedbackRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: FeedbackRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        repository = FeedbackRepositoryImpl(retrofit.create(FeedbackApi::class.java), SafeApiCall(json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getDebrief maps 409 without structured body to debrief unavailable during exam`() = runTest {
        // Разбор недоступен, пока экзамен не завершён (ExamService.assertDebriefAllowed) — бэкенд
        // бросает ResponseStatusException без {"error", "message"}, как у остальных ошибок API, а
        // стандартное тело Spring Boot ({"timestamp", "status", "error": "Conflict", "path"}).
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"timestamp": "2026-09-26T10:00:00Z", "status": 409, "error": "Conflict", "path": "/api/feedback/debrief/p1"}""",
            ),
        )

        val result = repository.getDebrief("p1")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as DomainError.Api
        assertEquals(409, error.statusCode)
        assertTrue(error.isConflict())
        assertTrue(error.isDebriefUnavailableDuringExam())
        assertEquals(DomainError.DEBRIEF_UNAVAILABLE_DURING_EXAM, error.errorCode)
    }

    @Test
    fun `getDebrief passes through non-409 errors unchanged`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error": "progress_not_found", "message": "Прохождение 'p404' не найдено"}""",
            ),
        )

        val result = repository.getDebrief("p404")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as DomainError.Api
        assertEquals(404, error.statusCode)
        assertEquals("progress_not_found", error.errorCode)
        assertTrue(!error.isDebriefUnavailableDuringExam())
    }
}
