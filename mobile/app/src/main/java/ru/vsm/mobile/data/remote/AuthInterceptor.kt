package ru.vsm.mobile.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import ru.vsm.mobile.data.local.AuthSessionStore

/**
 * Добавляет `Authorization: Bearer <token>` ко всем запросам, если на устройстве есть сохранённый
 * токен учётной записи (см. раздел «Авторизация» в README backend). `X-Player-Id`, если он
 * присутствует в запросе (см. `ScenarioApi`/`AuthApi.register`), не трогается — backend сам
 * приоритезирует токен над ним, здесь дублировать эту логику не нужно.
 *
 * `runBlocking` безопасен: интерцептор OkHttp всегда выполняется на отдельном (не главном) потоке
 * пула диспетчера запросов, а чтение из локального DataStore быстрое.
 */
class AuthInterceptor(private val sessionStore: AuthSessionStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionStore.getToken() }
        val original = chain.request()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder().addHeader("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}
