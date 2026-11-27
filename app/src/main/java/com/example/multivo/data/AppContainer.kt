package com.example.multivo.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserProfile) : AuthState
}

/** Simple manual dependency container shared by all screens. */
class AppContainer(context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sessionStore = SessionStore(context.applicationContext)

    @Volatile
    private var token: String? = null

    val api = MultivoApi(serverUrl = { sessionStore.serverUrl() }, token = { token })
    val realtime = RealtimeClient(api, scope)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState

    init {
        scope.launch {
            val saved = sessionStore.load()
            if (saved == null) {
                _authState.value = AuthState.SignedOut
            } else {
                token = saved.token
                _authState.value = AuthState.SignedIn(saved.user)
                realtime.start()
                // Refresh the profile in the background; sign out if the token expired.
                runCatching { api.me() }
                    .onSuccess { updateUser(it) }
                    .onFailure { if ((it as? ApiException)?.status == 401) signOut() }
            }
        }
    }

    val currentUser: UserProfile? get() = (authState.value as? AuthState.SignedIn)?.user

    suspend fun signIn(response: AuthResponse) {
        token = response.token
        sessionStore.save(Session(response.token, response.user))
        _authState.value = AuthState.SignedIn(response.user)
        realtime.start()
    }

    suspend fun updateUser(user: UserProfile) {
        val current = token ?: return
        sessionStore.save(Session(current, user))
        _authState.value = AuthState.SignedIn(user)
    }

    suspend fun signOut() {
        realtime.stop()
        token = null
        sessionStore.clear()
        _authState.value = AuthState.SignedOut
    }
}
