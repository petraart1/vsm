package ru.vsm.mobile.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import ru.vsm.mobile.data.local.FakeAuthSessionStore
import ru.vsm.mobile.data.remote.AuthInterceptor
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.AuthApi
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.error.isUnauthorized
import ru.vsm.mobile.domain.fake.FakePlayerRepository
import ru.vsm.mobile.domain.model.UserRole

/**
 * MockWebServer: [AuthRepositoryImpl] на реальном Retrofit + [AuthInterceptor] + kotlinx.serialization,
 * без похода в сеть (тот же приём, что [ScenarioRepositoryImplTest]).
 */
class AuthRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var sessionStore: FakeAuthSessionStore
    private lateinit var repository: AuthRepositoryImpl
    private lateinit var authApi: AuthApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        sessionStore = FakeAuthSessionStore()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionStore))
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApi = retrofit.create(AuthApi::class.java)
        repository = AuthRepositoryImpl(
            api = authApi,
            sessionStore = sessionStore,
            playerRepository = FakePlayerRepository(fixedPlayerId = DEVICE_PLAYER_ID),
            safeApiCall = SafeApiCall(json),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login success saves token and next request carries Authorization header`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"tok123","profile":{"id":"$DEVICE_PLAYER_ID","login":"bob","email":"bob@vsm.ru","displayName":"Bob","role":"USER","createdAt":"2024-01-01T00:00:00Z"}}""",
            ),
        )

        val result = repository.login("bob", "password123")

        assertTrue(result.isSuccess)
        val user = result.getOrThrow()
        assertEquals("bob", user.login)
        assertEquals(UserRole.USER, user.role)
        assertEquals("tok123", sessionStore.getToken())

        val loginRequest = server.takeRequest()
        assertEquals("/api/auth/login", loginRequest.path)

        // Токен сохранён в sessionStore -> любой следующий запрос через тот же AuthInterceptor уходит с заголовком.
        server.enqueue(MockResponse().setResponseCode(200).setBody(profileJson()))
        authApi.me()
        val meRequest = server.takeRequest()
        assertEquals("Bearer tok123", meRequest.getHeader("Authorization"))
    }

    @Test
    fun `login with wrong credentials returns invalid_credentials domain error`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody("""{"error":"invalid_credentials","message":"invalid_credentials"}"""),
        )

        val result = repository.login("bob", "wrong-password")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as DomainError.Api
        assertEquals(401, error.statusCode)
        assertEquals(DomainError.INVALID_CREDENTIALS, error.errorCode)
        assertTrue(error.isUnauthorized())
        assertNull(sessionStore.getToken())
    }

    @Test
    fun `register sends anonymous device playerId as X-Player-Id header`() = runTest {
        // register (201, без токена) + auto-login (200, с токеном) — см. AuthRepositoryImpl.register.
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"id":"$DEVICE_PLAYER_ID","login":"alice","email":"alice@vsm.ru","displayName":null,"role":"USER","createdAt":"2024-01-01T00:00:00Z"}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"token":"tok456","profile":{"id":"$DEVICE_PLAYER_ID","login":"alice","email":"alice@vsm.ru","displayName":null,"role":"USER","createdAt":"2024-01-01T00:00:00Z"}}""",
            ),
        )

        val result = repository.register("alice", "alice@vsm.ru", "password123")

        assertTrue(result.isSuccess)
        assertEquals("tok456", sessionStore.getToken())

        val registerRequest = server.takeRequest()
        assertEquals("/api/auth/register", registerRequest.path)
        assertEquals(DEVICE_PLAYER_ID, registerRequest.getHeader("X-Player-Id"))

        val loginRequest = server.takeRequest()
        assertEquals("/api/auth/login", loginRequest.path)
    }

    private fun profileJson() =
        """{"id":"$DEVICE_PLAYER_ID","login":"bob","email":"bob@vsm.ru","displayName":"Bob","role":"USER","createdAt":"2024-01-01T00:00:00Z"}"""

    private companion object {
        const val DEVICE_PLAYER_ID = "11111111-1111-1111-1111-111111111111"
    }
}
