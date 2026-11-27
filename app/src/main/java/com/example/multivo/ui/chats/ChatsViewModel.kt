package com.example.multivo.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multivo.data.AppContainer
import com.example.multivo.data.ConversationSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatsUiState(
    val loading: Boolean = true,
    val conversations: List<ConversationSummary> = emptyList(),
    val error: String? = null
)

class ChatsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state

    init {
        refresh()
        viewModelScope.launch {
            container.realtime.events.collect { event ->
                if (event.type in setOf("new_message", "notification", "user_online", "user_offline")) refresh(quiet = true)
            }
        }
    }

    fun refresh(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.update { it.copy(loading = true, error = null) }
            runCatching { container.api.conversations() }
                .onSuccess { list -> _state.update { it.copy(loading = false, conversations = list, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}
