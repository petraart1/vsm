package ru.vsm.mobile.di

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import ru.vsm.mobile.BuildConfig
import ru.vsm.mobile.data.local.PlayerIdDataStore
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.FeedbackApi
import ru.vsm.mobile.data.remote.api.GamificationApi
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.data.remote.ws.ProgressWebSocketClient
import ru.vsm.mobile.data.repository.FeedbackRepositoryImpl
import ru.vsm.mobile.data.repository.GamificationRepositoryImpl
import ru.vsm.mobile.data.repository.ScenarioRepositoryImpl
import ru.vsm.mobile.domain.fake.FakeFeedbackRepository
import ru.vsm.mobile.domain.fake.FakeGamificationRepository
import ru.vsm.mobile.domain.fake.FakePlayerRepository
import ru.vsm.mobile.domain.fake.FakeScenarioRepository
import ru.vsm.mobile.domain.repository.FeedbackRepository
import ru.vsm.mobile.domain.repository.GamificationRepository
import ru.vsm.mobile.domain.repository.PlayerRepository
import ru.vsm.mobile.domain.repository.ScenarioRepository

/**
 * Ручной DI-контейнер (без Hilt — проще сборка для хакатона). Один экземпляр на процесс
 * приложения, создаётся в `Application`/`MainActivity` и прокидывается вниз по дереву Compose.
 *
 * @param useFakes true — репозитории отдают in-memory фейки без похода в сеть (см. `domain/fake`),
 *   удобно для разработки UI без поднятого backend. По умолчанию `false` — реальный REST/WS-клиент
 *   на [BuildConfig.BACKEND_BASE_URL] (эмулятор -> `http://10.0.2.2:8080/`).
 */
class AppContainer(
    context: Context,
    useFakes: Boolean = false,
    baseUrl: String = BuildConfig.BACKEND_BASE_URL,
) {
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // WebSocket-соединение живёт, пока экран прохождения открыт — не должно рваться по таймауту чтения.
        .pingInterval(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val safeApiCall = SafeApiCall(json)
    private val wsClient = ProgressWebSocketClient(okHttpClient, json, baseUrl)

    val playerRepository: PlayerRepository =
        if (useFakes) FakePlayerRepository() else PlayerIdDataStore(context.applicationContext)

    val scenarioRepository: ScenarioRepository =
        if (useFakes) {
            FakeScenarioRepository()
        } else {
            ScenarioRepositoryImpl(retrofit.create(ScenarioApi::class.java), wsClient, safeApiCall)
        }

    val feedbackRepository: FeedbackRepository =
        if (useFakes) {
            FakeFeedbackRepository()
        } else {
            FeedbackRepositoryImpl(retrofit.create(FeedbackApi::class.java), safeApiCall)
        }

    val gamificationRepository: GamificationRepository =
        if (useFakes) {
            FakeGamificationRepository()
        } else {
            GamificationRepositoryImpl(retrofit.create(GamificationApi::class.java), safeApiCall)
        }
}
