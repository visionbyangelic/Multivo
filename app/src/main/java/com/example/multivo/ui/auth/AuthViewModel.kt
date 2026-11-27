package com.example.multivo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multivo.data.AppContainer
import com.example.multivo.data.Languages
import com.example.multivo.data.LoginRequest
import com.example.multivo.data.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Shared by the Sign in and Create account screens. */
data class AuthUiState(
    val identifier: String = "",
    val username: String = "",
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    /** Language messages are translated into. Picked from the phone automatically. */
    val language: String = Languages.fromPhone(Locale.getDefault().language),
    val changingLanguage: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun update(transform: (AuthUiState) -> AuthUiState) = _state.update { transform(it).copy(error = null) }

    /** Called when moving between Sign in and Create account: never carry a password across. */
    fun clearForScreenChange() = _state.update { it.copy(password = "", error = null, loading = false) }

    fun signIn() {
        val s = _state.value
        validateSignIn(s)?.let { message -> _state.update { it.copy(error = message) }; return }
        send { container.api.login(LoginRequest(s.identifier.trim(), s.password)) }
    }

    fun signUp() {
        val s = _state.value
        validateSignUp(s)?.let { message -> _state.update { it.copy(error = message) }; return }
        send {
            container.api.register(
                RegisterRequest(s.username.trim(), s.fullName.trim(), s.email.trim(), s.password, s.language)
            )
        }
    }

    private fun send(call: suspend () -> com.example.multivo.data.AuthResponse) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { call() }
                .onSuccess { response -> container.signIn(response) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Something went wrong.") } }
            _state.update { it.copy(loading = false) }
        }
    }

    companion object {
        fun validateSignIn(s: AuthUiState): String? = when {
            s.identifier.isBlank() -> "Enter your username or email."
            s.password.isBlank() -> "Enter your password."
            else -> null
        }

        fun validateSignUp(s: AuthUiState): String? = when {
            s.fullName.trim().length < 2 -> "Enter your full name."
            !s.username.trim().matches(Regex("^[a-zA-Z0-9_]{3,30}$")) ->
                "Username: 3-30 letters, numbers or underscores."
            !s.email.contains('@') -> "Enter a valid email address."
            s.password.length < 8 -> "Password must be at least 8 characters."
            else -> null
        }
    }
}
