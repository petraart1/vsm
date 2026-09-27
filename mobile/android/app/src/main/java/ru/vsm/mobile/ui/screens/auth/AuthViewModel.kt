package ru.vsm.mobile.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.repository.AuthRepository

enum class AuthTab { LOGIN, REGISTER }

/** Демо-гражданин для входа через Госуслуги (демо-ЕСИА без реального портала). */
data class EsiaDemoCitizen(val fullName: String, val snils: String, val login: String, val password: String)

val ESIA_DEMO_CITIZENS = listOf(
    EsiaDemoCitizen("Иванов Иван Иванович", "112-233-445 95", "esia.ivanov", "esia-demo-pass"),
    EsiaDemoCitizen("Петрова Мария Сергеевна", "223-344-556 06", "esia.petrova", "esia-demo-pass"),
    EsiaDemoCitizen("Сидоров Пётр Алексеевич", "334-455-667 17", "esia.sidorov", "esia-demo-pass"),
)

data class AuthUiState(
    val tab: AuthTab = AuthTab.LOGIN,
    val login: String = "",
    val password: String = "",
    val displayName: String = "",
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val esiaOpen: Boolean = false,
    val esiaVerifiedName: String? = null,
    val done: Boolean = false,
)

/**
 * Вход / регистрация по логину и паролю, демо-вход через Госуслуги (без реального портала — набор
 * фиксированных тестовых граждан) и анонимное продолжение (playerId устройства уже существует).
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun selectTab(tab: AuthTab) = _state.update { it.copy(tab = tab, error = null) }
    fun setLogin(value: String) = _state.update { it.copy(login = value) }
    fun setPassword(value: String) = _state.update { it.copy(password = value) }
    fun setDisplayName(value: String) = _state.update { it.copy(displayName = value) }
    fun setEmail(value: String) = _state.update { it.copy(email = value) }

    fun submitLogin() {
        val s = _state.value
        if (s.busy || s.login.isBlank() || s.password.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            authRepository.login(s.login.trim(), s.password).onSuccess {
                _state.update { it.copy(busy = false, done = true) }
            }.onFailure { err ->
                _state.update { it.copy(busy = false, error = err.message ?: "Не удалось войти") }
            }
        }
    }

    fun submitRegister() {
        val s = _state.value
        if (s.busy || s.login.isBlank() || s.email.isBlank() || s.password.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            authRepository.register(
                login = s.login.trim(),
                email = s.email.trim(),
                password = s.password,
                displayName = s.displayName.ifBlank { null },
            ).onSuccess {
                _state.update { it.copy(busy = false, done = true) }
            }.onFailure { err ->
                _state.update { it.copy(busy = false, error = err.message ?: "Не удалось зарегистрироваться") }
            }
        }
    }

    fun openEsiaDemo() = _state.update { it.copy(esiaOpen = true) }
    fun closeEsiaDemo() = _state.update { it.copy(esiaOpen = false) }

    /**
     * Демо-вход через Госуслуги: пытается войти под фиксированной тестовой учёткой гражданина,
     * при первом использовании регистрирует её тем же логином/паролем. Реального портала ЕСИА
     * здесь нет — это демонстрационная заглушка для стенда.
     */
    fun confirmEsiaDemo(citizen: EsiaDemoCitizen) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val loginResult = authRepository.login(citizen.login, citizen.password)
            val result = if (loginResult.isSuccess) {
                loginResult
            } else {
                authRepository.register(
                    login = citizen.login,
                    email = "${citizen.login}@esia-demo.local",
                    password = citizen.password,
                    displayName = citizen.fullName,
                )
            }
            result.onSuccess {
                _state.update { it.copy(busy = false, esiaOpen = false, esiaVerifiedName = citizen.fullName) }
            }.onFailure { err ->
                _state.update { it.copy(busy = false, error = err.message ?: "Не удалось подтвердить через Госуслуги") }
            }
        }
    }

    fun dismissEsiaVerified() = _state.update { it.copy(esiaVerifiedName = null, done = true) }
}
