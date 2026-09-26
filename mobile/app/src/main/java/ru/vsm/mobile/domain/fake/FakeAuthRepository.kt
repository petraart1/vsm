package ru.vsm.mobile.domain.fake

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.vsm.mobile.domain.error.DomainError
import ru.vsm.mobile.domain.model.AuthUser
import ru.vsm.mobile.domain.model.UserRole
import ru.vsm.mobile.domain.repository.AuthRepository

/**
 * В памяти процесса — не переживает перезапуск приложения (в отличие от реальной реализации на
 * DataStore). Логин/пароль не проверяются, кроме зарезервированного логина [FAIL_LOGIN] — им можно
 * проверить экраны ошибок без сети.
 */
class FakeAuthRepository : AuthRepository {

    private val user = MutableStateFlow<AuthUser?>(null)

    override val currentUser: Flow<AuthUser?> = user
    override val isAuthorized: Flow<Boolean> = user.map { it != null }

    override suspend fun register(login: String, email: String, password: String, displayName: String?): Result<AuthUser> {
        if (login == FAIL_LOGIN) {
            return Result.failure(DomainError.Api(409, DomainError.LOGIN_ALREADY_TAKEN, "Логин уже занят"))
        }
        return login(login, password).map { it.copy(displayName = displayName ?: it.displayName) }
            .onSuccess { user.value = it }
    }

    override suspend fun login(login: String, password: String): Result<AuthUser> {
        if (login == FAIL_LOGIN) {
            return Result.failure(DomainError.Api(401, DomainError.INVALID_CREDENTIALS, "Неверный логин или пароль"))
        }
        val fake = AuthUser(id = "fake-$login", login = login, displayName = login, role = UserRole.USER)
        user.value = fake
        return Result.success(fake)
    }

    override suspend fun logout() {
        user.value = null
    }

    companion object {
        /** Логин, при котором [register]/[login] всегда возвращают ошибку — для проверки UI без сети. */
        const val FAIL_LOGIN = "fail"
    }
}
