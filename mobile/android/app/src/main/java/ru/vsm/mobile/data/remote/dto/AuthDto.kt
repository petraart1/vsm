package ru.vsm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/** `ru.vsm.backend.auth.domain.UserRole` */
@Serializable
enum class UserRoleDto { USER, ADMIN }

/** Тело `POST /api/auth/register` — `ru.vsm.backend.auth.web.dto.RegisterRequest`. */
@Serializable
data class RegisterRequestDto(
    val login: String,
    val email: String,
    val password: String,
    val displayName: String? = null,
)

/** Тело `POST /api/auth/login` — `ru.vsm.backend.auth.web.dto.LoginRequest`. */
@Serializable
data class LoginRequestDto(val login: String, val password: String)

/** `ru.vsm.backend.auth.web.dto.UserProfileResponse` — ответ регистрации/логина/`/api/auth/me`. */
@Serializable
data class UserProfileResponseDto(
    val id: String,
    val login: String,
    val email: String,
    val displayName: String? = null,
    val role: UserRoleDto,
    val createdAt: String,
)

/** `ru.vsm.backend.auth.web.dto.LoginResponse` — токен для заголовка `Authorization: Bearer <token>` + профиль. */
@Serializable
data class LoginResponseDto(val token: String, val profile: UserProfileResponseDto)

/**
 * Тело `POST /api/auth/esia/callback` — `ru.vsm.backend.auth.web.esia.dto.EsiaCallbackRequest`.
 * `code` — значение параметра `?code=` из редиректа демо-заглушки входа через Госуслуги/ЕСИА
 * (см. `ru.vsm.backend.auth.web.esia.EsiaMockController`: `GET .../authorize` открывается в
 * веб-вьюхе, выбор гражданина -> редирект с кодом -> этот вызов меняет код на тот же формат
 * ответа, что и обычный логин).
 */
@Serializable
data class EsiaCallbackRequestDto(val code: String)
