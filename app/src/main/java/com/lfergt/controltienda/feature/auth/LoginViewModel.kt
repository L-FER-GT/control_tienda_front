package com.lfergt.controltienda.feature.auth

import androidx.lifecycle.viewModelScope
import com.lfergt.controltienda.domain.error.DomainError
import com.lfergt.controltienda.domain.error.userMessage
import com.lfergt.controltienda.domain.port.AuthRepository
import com.lfergt.controltienda.domain.port.UserRepository
import com.lfergt.controltienda.ui.common.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val registering: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : BaseViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val loggedInChannel = Channel<Unit>(Channel.CONFLATED)
    val loggedIn = loggedInChannel.receiveAsFlow()

    fun onName(v: String) = _state.update { it.copy(name = v, fieldErrors = it.fieldErrors - "name") }
    fun onEmail(v: String) = _state.update { it.copy(email = v, fieldErrors = it.fieldErrors - "email") }
    fun onPassword(v: String) = _state.update { it.copy(password = v, fieldErrors = it.fieldErrors - "password") }
    fun toggleMode() = _state.update { it.copy(registering = !it.registering, fieldErrors = emptyMap()) }

    private fun validate(s: LoginUiState): Map<String, String> = buildMap {
        if (s.registering && s.name.isBlank()) put("name", "Ingresa tu nombre")
        if (!s.email.contains("@") || !s.email.contains(".")) put("email", "Correo no válido")
        if (s.password.length < 6) put("password", "Mínimo 6 caracteres")
    }

    fun submit() {
        val s = _state.value
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            return
        }
        authenticate(displayName = if (s.registering) s.name.trim() else null) {
            if (s.registering) auth.registerWithEmail(s.name, s.email, s.password)
            else auth.signInWithEmail(s.email, s.password)
        }
    }

    fun signInWithGoogle(idToken: String) = authenticate(displayName = null) { auth.signInWithGoogle(idToken) }

    fun onGoogleError(message: String) = message(message)

    fun forgotPassword() {
        val email = _state.value.email
        if (!email.contains("@")) {
            _state.update { it.copy(fieldErrors = mapOf("email" to "Escribe tu correo para enviarte el enlace")) }
            return
        }
        launchSafe {
            auth.sendPasswordReset(email)
            message("Te enviamos un correo para restablecer tu contraseña.")
        }
    }

    /** Inicia sesión y luego asegura el perfil con el código de 10 dígitos. */
    private fun authenticate(displayName: String?, action: suspend () -> Unit) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                action()
                val profile = users.ensureProfile(displayName)
                if (profile.disabled) {
                    auth.signOut()
                    throw DomainError.Auth("Tu cuenta está deshabilitada.")
                }
                auth.refreshSession()
                loggedInChannel.send(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e is DomainError.Validation && e.field != null) {
                    _state.update { it.copy(fieldErrors = mapOf(e.field!! to e.userMessage())) }
                } else {
                    message(e.userMessage())
                }
                // Si el perfil no se pudo crear (sin internet), no se deja la sesión a medias.
                if (e !is DomainError.Auth && e !is DomainError.Validation) runCatching { auth.signOut() }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
