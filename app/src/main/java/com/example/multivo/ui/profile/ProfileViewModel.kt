package com.example.multivo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multivo.data.AppContainer
import com.example.multivo.data.UpdateProfileRequest
import com.example.multivo.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserProfile? = null,
    val fullName: String = "",
    val bio: String = "",
    val language: String = "en",
    val saving: Boolean = false,
    val message: String? = null,
    val connected: Boolean = false
) {
    val dirty: Boolean
        get() = user != null && (fullName != user.fullName || bio != user.bio.orEmpty() || language != user.preferredLanguage)
}

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        container.currentUser?.let(::load)
        viewModelScope.launch { container.realtime.connected.collect { c -> _state.update { it.copy(connected = c) } } }
    }

    private fun load(user: UserProfile) = _state.update {
        it.copy(user = user, fullName = user.fullName, bio = user.bio.orEmpty(), language = user.preferredLanguage)
    }

    fun onName(v: String) = _state.update { it.copy(fullName = v) }
    fun onBio(v: String) = _state.update { it.copy(bio = v.take(500)) }
    fun onLanguage(v: String) = _state.update { it.copy(language = v) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val s = _state.value
        if (s.fullName.trim().length < 2) {
            _state.update { it.copy(message = "Enter your full name.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                container.api.updateProfile(
                    UpdateProfileRequest(s.fullName.trim(), s.bio.trim().ifEmpty { null }, s.user?.profilePicture, s.language)
                )
            }.onSuccess { user ->
                container.updateUser(user)
                load(user)
                _state.update { it.copy(message = "Profile saved.") }
            }.onFailure { e ->
                _state.update { it.copy(message = e.message) }
            }
            _state.update { it.copy(saving = false) }
        }
    }

    fun signOut() {
        viewModelScope.launch { container.signOut() }
    }
}
