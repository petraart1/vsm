package ru.vsm.mobile.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.vsm.mobile.domain.model.AuthUser

/**
 * Учётная запись поверх анонимной идентификации ([PlayerRepository]) — опциональна, анонимный
 * путь продолжает работать без неё (см. `X-Player-Id` в игровых репозиториях). Успешный
 * [register]/[login] сохраняет токен на устройстве и обновляет [currentUser]; playerId устройства
 * ([PlayerRepository.getOrCreatePlayerId]) при этом не меняется, в том числе после [logout] — он
 * и есть анонимная идентификация, к которой можно позже привязать учётку через [register].
 */
interface AuthRepository {

    /** `null` — анонимная сессия (нет сохранённого токена, либо он ещё не подтверждён логином/регистрацией). */
    val currentUser: Flow<AuthUser?>

    /** Производное от [currentUser] — `true`, если есть распознанная учётная запись. */
    val isAuthorized: Flow<Boolean>

    /**
     * Регистрация новой учётки. Если на устройстве уже накоплен анонимный прогресс (playerId из
     * [PlayerRepository]), он передаётся автоматически и становится id новой учётки — весь
     * прогресс/очки, накопленные анонимно, остаются доступны без переноса данных. При успехе
     * сразу выполняет вход теми же учётными данными (сохраняет токен, как [login]).
     *
     * Возможные ошибки (см. [ru.vsm.mobile.domain.error.DomainError.Api.errorCode]):
     * [ru.vsm.mobile.domain.error.DomainError.LOGIN_ALREADY_TAKEN],
     * [ru.vsm.mobile.domain.error.DomainError.EMAIL_ALREADY_TAKEN],
     * [ru.vsm.mobile.domain.error.DomainError.PLAYER_ALREADY_REGISTERED] (409),
     * [ru.vsm.mobile.domain.error.DomainError.LOGIN_TOO_SHORT],
     * [ru.vsm.mobile.domain.error.DomainError.EMAIL_INVALID],
     * [ru.vsm.mobile.domain.error.DomainError.PASSWORD_TOO_SHORT] (400).
     */
    suspend fun register(login: String, email: String, password: String, displayName: String? = null): Result<AuthUser>

    /**
     * Вход по логину/паролю — сохраняет токен, обновляет [currentUser].
     * Ошибка [ru.vsm.mobile.domain.error.DomainError.INVALID_CREDENTIALS] (401) — неверный логин
     * или пароль (сообщение одно на оба случая, чтобы не подсказывать существование логина).
     */
    suspend fun login(login: String, password: String): Result<AuthUser>

    /** Очищает сохранённый токен ([currentUser] становится `null`). Анонимный playerId устройства не трогается. */
    suspend fun logout()
}
