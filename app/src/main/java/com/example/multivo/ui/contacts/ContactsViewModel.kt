package com.example.multivo.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multivo.data.AppContainer
import com.example.multivo.data.ConnectionRequest
import com.example.multivo.data.PublicUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<PublicUser> = emptyList(),
    val requests: List<ConnectionRequest> = emptyList(),
    val contacts: List<PublicUser> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null
)

class ContactsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state

    private val _openChat = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openChat: SharedFlow<String> = _openChat

    private var searchJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            container.realtime.events.collect { if (it.type == "notification") refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val requests = container.api.incomingRequests()
                val contacts = container.api.contacts()
                requests to contacts
            }.onSuccess { (requests, contacts) ->
                _state.update { it.copy(requests = requests, contacts = contacts, loading = false) }
            }.onFailure { e -> _state.update { it.copy(loading = false, message = e.message) } }
        }
    }

    fun onQuery(query: String) {
        _state.update { it.copy(query = query, message = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // wait until the user stops typing
            _state.update { it.copy(searching = true) }
            runCatching { container.api.searchUsers(query.trim()) }
                .onSuccess { list -> _state.update { it.copy(results = list, searching = false) } }
                .onFailure { e -> _state.update { it.copy(searching = false, message = e.message) } }
        }
    }

    fun connect(user: PublicUser) = action {
        val request = container.api.sendContactRequest(user.id)
        val newRelation = if (request.status == "accepted") "contact" else "request_sent"
        _state.update { s -> s.copy(results = s.results.map { if (it.id == user.id) it.copy(relationship = newRelation) else it }) }
        if (newRelation == "contact") refresh()
        if (newRelation == "contact") "You and ${user.fullName} are now connected." else "Request sent to ${user.fullName}."
    }

    fun accept(request: ConnectionRequest) = action {
        container.api.acceptRequest(request.id)
        refresh()
        "You and ${request.sender.fullName} are now connected."
    }

    fun reject(request: ConnectionRequest) = action {
        container.api.rejectRequest(request.id)
        refresh()
        null
    }

    fun message(user: PublicUser) = action {
        val conversation = container.api.startPrivateChat(user.id)
        _openChat.emit(conversation.id)
        null
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun action(block: suspend () -> String?) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { msg -> if (msg != null) _state.update { it.copy(message = msg) } }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }
}
