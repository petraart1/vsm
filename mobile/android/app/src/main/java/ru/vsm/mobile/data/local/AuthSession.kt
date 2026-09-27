package ru.vsm.mobile.data.local

import kotlinx.serialization.Serializable

/**
 * Локальный снимок сессии учётной записи — хранится вместе с токеном, чтобы
 * [ru.vsm.mobile.data.repository.AuthRepositoryImpl.currentUser] не требовал сетевого похода на
 * `/api/auth/me` при каждом запуске приложения. [role] — строкой (`"USER"`/`"ADMIN"`), чтобы этот
 * файл (`data/local`) не зависел от DTO уровня `data/remote`.
 */
@Serializable
data class AuthSession(
    val token: String,
    val userId: String,
    val login: String,
    val displayName: String?,
    val role: String,
)
