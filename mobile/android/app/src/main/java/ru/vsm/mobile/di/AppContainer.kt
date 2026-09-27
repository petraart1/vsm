package ru.vsm.mobile.di

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import ru.vsm.mobile.BuildConfig
import ru.vsm.mobile.data.local.AuthSessionStore
import ru.vsm.mobile.data.local.DataStoreAuthSessionStore
import ru.vsm.mobile.data.local.DataStoreOfflineQueueStore
import ru.vsm.mobile.data.local.NetworkMonitor
import ru.vsm.mobile.data.local.OfflineQueueStore
import ru.vsm.mobile.data.local.PlayerIdDataStore
import ru.vsm.mobile.data.remote.AuthInterceptor
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.AuthApi
import ru.vsm.mobile.data.remote.api.ExamApi
import ru.vsm.mobile.data.remote.api.FeedbackApi
import ru.vsm.mobile.data.remote.api.GamificationApi
import ru.vsm.mobile.data.remote.api.ScenarioApi
import ru.vsm.mobile.data.remote.ws.ProgressWebSocketClient
import ru.vsm.mobile.data.repository.AuthRepositoryImpl
import ru.vsm.mobile.data.repository.ExamRepositoryImpl
import ru.vsm.mobile.data.repository.FeedbackRepositoryImpl
import ru.vsm.mobile.data.repository.GamificationRepositoryImpl
import ru.vsm.mobile.data.repository.ScenarioRepositoryImpl
import ru.vsm.mobile.data.sync.OfflineQueueSyncer
import ru.vsm.mobile.domain.fake.FakeAuthRepository
import ru.vsm.mobile.domain.fake.FakeExamRepository
import ru.vsm.mobile.domain.fake.FakeFeedbackRepository
import ru.vsm.mobile.domain.fake.FakeGamificationRepository
import ru.vsm.mobile.domain.fake.FakePlayerRepository
import ru.vsm.mobile.domain.fake.FakeScenarioRepository
import ru.vsm.mobile.domain.repository.AuthRepository
import ru.vsm.mobile.domain.repository.ExamRepository
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

    /** Не зависит от [useFakes] — интерцептор просто не находит токен, если сессии ещё нет. */
    private val authSessionStore: AuthSessionStore = DataStoreAuthSessionStore(context.applicationContext, json)

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // WebSocket-соединение живёт, пока экран прохождения открыт — не должно рваться по таймауту чтения.
        .pingInterval(20, TimeUnit.SECONDS)
        // Authorization: Bearer <token>, если есть сохранённая сессия — приоритет над X-Player-Id
        // обеспечивает backend, здесь заголовок X-Player-Id не трогается.
        .addInterceptor(AuthInterceptor(authSessionStore))
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

    /** Живёт весь процесс приложения — держит фоновую отправку офлайн-очереди ([OfflineQueueSyncer]). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val playerRepository: PlayerRepository =
        if (useFakes) FakePlayerRepository() else PlayerIdDataStore(context.applicationContext)

    val scenarioRepository: ScenarioRepository =
        if (useFakes) {
            FakeScenarioRepository()
        } else {
            val scenarioApi = retrofit.create(ScenarioApi::class.java)
            val offlineQueueStore: OfflineQueueStore = DataStoreOfflineQueueStore(context.applicationContext, json)
            val offlineQueueSyncer = OfflineQueueSyncer(
                queueStore = offlineQueueStore,
                api = scenarioApi,
                safeApiCall = safeApiCall,
                isOnline = NetworkMonitor(context.applicationContext).isOnline,
                scope = appScope,
            )
            ScenarioRepositoryImpl(scenarioApi, wsClient, safeApiCall, offlineQueueStore, offlineQueueSyncer)
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

    val authRepository: AuthRepository =
        if (useFakes) {
            FakeAuthRepository()
        } else {
            AuthRepositoryImpl(retrofit.create(AuthApi::class.java), authSessionStore, playerRepository, safeApiCall)
        }

    /** Делегирует прохождение каждого пункта [scenarioRepository] — сам протокол хода по графу не меняется. */
    val examRepository: ExamRepository =
        if (useFakes) {
            FakeExamRepository(scenarioRepository)
        } else {
            ExamRepositoryImpl(retrofit.create(ExamApi::class.java), safeApiCall)
        }
}
