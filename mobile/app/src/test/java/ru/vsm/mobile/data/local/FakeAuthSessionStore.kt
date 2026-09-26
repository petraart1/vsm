package ru.vsm.mobile.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [AuthSessionStore] для тестов — без DataStore/Context. */
class FakeAuthSessionStore : AuthSessionStore {

    private val state = MutableStateFlow<AuthSession?>(null)

    /** Ковариантно переопределяет `Flow<AuthSession?>` из интерфейса — тесты читают `.value` синхронно. */
    override val session: StateFlow<AuthSession?> get() = state

    override suspend fun getToken(): String? = state.value?.token

    override suspend fun save(session: AuthSession) {
        state.value = session
    }

    override suspend fun clear() {
        state.value = null
    }
}
