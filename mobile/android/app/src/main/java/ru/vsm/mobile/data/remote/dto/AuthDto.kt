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
