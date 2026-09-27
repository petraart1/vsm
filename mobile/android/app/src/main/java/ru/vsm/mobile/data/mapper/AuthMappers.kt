package ru.vsm.mobile.data.mapper

import ru.vsm.mobile.data.local.AuthSession
import ru.vsm.mobile.data.remote.dto.UserProfileResponseDto
import ru.vsm.mobile.data.remote.dto.UserRoleDto
import ru.vsm.mobile.domain.model.AuthUser
import ru.vsm.mobile.domain.model.UserRole

fun UserProfileResponseDto.toDomain(): AuthUser = AuthUser(
    id = id,
    login = login,
    displayName = displayName,
    role = role.toDomain(),
)

fun UserRoleDto.toDomain(): UserRole = when (this) {
    UserRoleDto.USER -> UserRole.USER
    UserRoleDto.ADMIN -> UserRole.ADMIN
}

fun AuthSession.toDomain(): AuthUser = AuthUser(
    id = userId,
    login = login,
    displayName = displayName,
    role = if (role == UserRole.ADMIN.name) UserRole.ADMIN else UserRole.USER,
)

fun UserProfileResponseDto.toSession(token: String): AuthSession = AuthSession(
    token = token,
    userId = id,
    login = login,
    displayName = displayName,
    role = role.name,
)
