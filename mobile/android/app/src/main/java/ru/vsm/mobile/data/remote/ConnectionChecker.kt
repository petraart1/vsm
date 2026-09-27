package ru.vsm.mobile.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Проверка «жив ли backend» по произвольному базовому URL — используется экраном настроек
 * (кнопка «Проверить соединение»), а не обычными репозиториями: не проходит через
 * [SafeApiCall]/[DomainError][ru.vsm.mobile.domain.error.DomainError], т.к. результат — не данные
 * для UI-состояния экрана, а диагностика (код/время/текст ошибки) для человека, настраивающего адрес.
 */
class ConnectionChecker(baseOkHttpClient: OkHttpClient) {

    /** Отдельный клиент с короткими таймаутами — не должен наследовать 30с readTimeout основного клиента. */
    private val probeClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    data class Outcome(
        val success: Boolean,
        val statusCode: Int?,
        val elapsedMs: Long,
        val errorMessage: String?,
    )

    /** `GET {baseUrl}actuator/health` — не требует `X-Player-Id`/токена, поднят даже при пустой БД. */
    suspend fun check(baseUrl: String): Outcome = withContext(Dispatchers.IO) {
        val httpUrl = baseUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("actuator/health")
            ?.build()
            ?: return@withContext Outcome(success = false, statusCode = null, elapsedMs = 0, errorMessage = "Некорректный адрес")

        val request = Request.Builder().url(httpUrl).get().build()
        val startedAt = System.currentTimeMillis()
        try {
            probeClient.newCall(request).execute().use { response ->
                val elapsedMs = System.currentTimeMillis() - startedAt
                Outcome(
                    success = response.isSuccessful,
                    statusCode = response.code,
                    elapsedMs = elapsedMs,
                    errorMessage = if (response.isSuccessful) null else "HTTP ${response.code}",
                )
            }
        } catch (e: IOException) {
            Outcome(
                success = false,
                statusCode = null,
                elapsedMs = System.currentTimeMillis() - startedAt,
                errorMessage = e.message ?: e::class.simpleName ?: "network_error",
            )
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 5L
    }
}
