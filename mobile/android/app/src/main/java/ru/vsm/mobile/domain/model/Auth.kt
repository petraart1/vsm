package ru.vsm.mobile.domain.model

/**
 * Учётная запись поверх анонимной идентификации устройства ([ru.vsm.mobile.domain.repository.PlayerRepository])
 * — опциональна, анонимный путь (`playerId`) продолжает работать без неё. [id] — тот же id, что
 * был (или станет) `playerId` устройства: регистрация из уже накопленной анонимной сессии
 * переносит на неё логин/пароль без переноса данных (см. [ru.vsm.mobile.domain.repository.AuthRepository.register]).
 */
data class AuthUser(
    val id: String,
    val login: String,
    val displayName: String?,
    val role: UserRole,
)
