package ru.vsm.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.vsm.mobile.data.local.AuthSessionStore
import ru.vsm.mobile.data.mapper.toDomain
import ru.vsm.mobile.data.mapper.toSession
import ru.vsm.mobile.data.remote.SafeApiCall
import ru.vsm.mobile.data.remote.api.AuthApi
import ru.vsm.mobile.data.remote.dto.LoginRequestDto
import ru.vsm.mobile.data.remote.dto.RegisterRequestDto
import ru.vsm.mobile.domain.model.AuthUser
import ru.vsm.mobile.domain.repository.AuthRepository
import ru.vsm.mobile.domain.repository.PlayerRepository

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val sessionStore: AuthSessionStore,
    private val playerRepository: PlayerRepository,
    private val safeApiCall: SafeApiCall,
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = sessionStore.session.map { it?.toDomain() }
    override val isAuthorized: Flow<Boolean> = currentUser.map { it != null }

    override suspend fun register(login: String, email: String, password: String, displayName: String?): Result<AuthUser> {
        val anonymousPlayerId = playerRepository.getOrCreatePlayerId()
        val registerResult = safeApiCall.call {
            api.register(RegisterRequestDto(login = login, email = email, password = password, displayName = displayName), anonymousPlayerId)
        }
        // register не выдаёт токен (см. ru.vsm.backend.auth.web.dto.UserProfileResponse) — сразу
        // логинимся теми же данными, чтобы currentUser обновился и токен сохранился, как после login.
        return registerResult.fold(
            onSuccess = { login(login, password) },
            onFailure = { Result.failure(it) },
        )
    }

    override suspend fun login(login: String, password: String): Result<AuthUser> {
        val result = safeApiCall.call { api.login(LoginRequestDto(login, password)) }
        return result.fold(
            onSuccess = { response ->
                sessionStore.save(response.profile.toSession(response.token))
                Result.success(response.profile.toDomain())
            },
            onFailure = { Result.failure(it) },
        )
    }

    override suspend fun logout() {
        sessionStore.clear()
    }
}
