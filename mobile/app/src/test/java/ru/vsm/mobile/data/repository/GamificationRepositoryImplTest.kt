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
import ru.vsm.mobile.data.remote.api.GamificationApi
import ru.vsm.mobile.domain.error.DomainError

/** MockWebServer: [GamificationRepositoryImpl] на реальном Retrofit + kotlinx.serialization. */
class GamificationRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GamificationRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        repository = GamificationRepositoryImpl(retrofit.create(GamificationApi::class.java), SafeApiCall(json))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getProfile maps empty profile without error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "playerId": "player-1",
                  "displayName": "Проводник",
                  "totalScore": 0,
                  "scenariosCompleted": 0,
                  "totalScenariosAvailable": 51,
                  "blockProgress": [],
                  "recentAchievements": [],
                  "leaderboardRank": null
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getProfile("player-1")

        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals("player-1", profile.playerId)
        assertEquals(0, profile.scenariosCompleted)
        assertNull(profile.leaderboardRank)
    }

    @Test
    fun `getLeaderboard maps top and null me`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "top": [
                    {"rank": 1, "playerId": "p1", "displayName": "Лидер", "totalScore": 500, "scenariosCompleted": 30}
                  ],
                  "me": null
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getLeaderboard(limit = 20, playerId = null)

        assertTrue(result.isSuccess)
        val leaderboard = result.getOrThrow()
        assertEquals(1, leaderboard.top.size)
        assertEquals("Лидер", leaderboard.top.first().displayName)
        assertNull(leaderboard.me)
    }

    @Test
    fun `markAllRead maps 500 to unexpected api error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        val result = repository.markAllRead("player-1")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as DomainError.Api
        assertEquals(500, error.statusCode)
    }
}
