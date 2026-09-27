package ru.vsm.mobile.ui.screens.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vsm.mobile.domain.repository.AuthRepository

enum class AuthTab { LOGIN, REGISTER }

/** Своя схема приложения для перехвата редиректа демо-ЕСИА (см. [AuthRepository.esiaAuthorizeUrl]). */
const val ESIA_REDIRECT_URI = "vsm://esia-callback"

data class AuthUiState(
    val tab: AuthTab = AuthTab.LOGIN,
    val login: String = "",
    val password: String = "",
    val displayName: String = "",
    val email: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    /** Открыта веб-вьюха демо-Госуслуг — [esiaAuthorizeUrl] загружен в неё. */
    val esiaOpen: Boolean = false,
    val esiaAuthorizeUrl: String? = null,
    /** Идёт обмен кода редиректа на сессию ([AuthRepository.loginWithEsia]). */
    val esiaExchanging: Boolean = false,
    val done: Boolean = false,
)

/**
 * Вход / регистрация по логину и паролю, демо-вход через Госуслуги (веб-вьюха на
 * [AuthRepository.esiaAuthorizeUrl], редирект перехватывается по схеме [ESIA_REDIRECT_URI] и
 * обменивается на сессию через [AuthRepository.loginWithEsia]) и анонимное продолжение (playerId
 * устройства уже существует).
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

    /** Открывает веб-вьюху демо-Госуслуг на URL, который отдаёт [AuthRepository.esiaAuthorizeUrl]. */
    fun openEsia() {
        _state.update {
            it.copy(esiaOpen = true, error = null, esiaAuthorizeUrl = authRepository.esiaAuthorizeUrl(ESIA_REDIRECT_URI))
        }
    }

    fun closeEsia() = _state.update { it.copy(esiaOpen = false, esiaAuthorizeUrl = null) }

    /**
     * Веб-вьюха вызывает при переходе на [ESIA_REDIRECT_URI] — извлекает `code` из редиректа и
     * обменивает его на сессию. Возвращает `true`, если url был перехвачен (страница демо-Госуслуг
     * дальше не грузится в веб-вьюхе).
     */
    fun onEsiaRedirect(url: String): Boolean {
        if (!url.startsWith(ESIA_REDIRECT_URI)) return false
        val code = runCatching { Uri.parse(url).getQueryParameter("code") }.getOrNull()
        if (code.isNullOrBlank()) {
            _state.update { it.copy(esiaOpen = false, esiaAuthorizeUrl = null, error = "Госуслуги не вернули код авторизации.") }
            return true
        }
        _state.update { it.copy(esiaExchanging = true, error = null) }
        viewModelScope.launch {
            authRepository.loginWithEsia(code).onSuccess {
                _state.update { it.copy(esiaExchanging = false, esiaOpen = false, esiaAuthorizeUrl = null, done = true) }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        esiaExchanging = false,
                        esiaOpen = false,
                        esiaAuthorizeUrl = null,
                        error = err.message ?: "Не удалось подтвердить через Госуслуги",
                    )
                }
            }
        }
        return true
    }
}
