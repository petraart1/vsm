package ru.vsm.mobile.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Хранилище сессии учётной записи (JWT-токен + мини-профиль, см. [AuthSession]). Используется
 * [ru.vsm.mobile.data.repository.AuthRepositoryImpl] и [ru.vsm.mobile.data.remote.AuthInterceptor].
 */
interface AuthSessionStore {

    val session: Flow<AuthSession?>

    /** Синхронно (для интерцептора OkHttp) — только токен, без остального профиля. */
    suspend fun getToken(): String?

    suspend fun save(session: AuthSession)

    /** Выход — не трогает playerId устройства ([ru.vsm.mobile.domain.repository.PlayerRepository]). */
    suspend fun clear()
}
