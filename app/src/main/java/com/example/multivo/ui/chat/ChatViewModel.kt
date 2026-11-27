package com.example.multivo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multivo.data.AppContainer
import com.example.multivo.data.ChatMessage
import com.example.multivo.data.ConversationSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversation: ConversationSummary? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val loading: Boolean = true,
    val sending: Boolean = false,
    val typingNames: List<String> = emptyList(),
    val showOriginal: Set<String> = emptySet(),
    val error: String? = null
)

class ChatViewModel(private val container: AppContainer, private val conversationId: String) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    val myId: String get() = container.currentUser?.id.orEmpty()

    private var typingSent = false
    private var typingStopJob: Job? = null
    private val typingTimeouts = mutableMapOf<String, Job>()

    init {
        load()
        viewModelScope.launch {
            container.realtime.events.collect { event ->
                if (event.conversationId != conversationId && event.type != "user_online" && event.type != "user_offline") {
                    return@collect
                }
                when (event.type) {
                    "new_message" -> event.message?.let { msg ->
                        _state.update { s ->
                            if (s.messages.any { it.id == msg.id }) s.copy(messages = s.messages.map { if (it.id == msg.id) msg else it })
                            else s.copy(messages = s.messages + msg)
                        }
                        removeTyping(msg.senderId)
                        if (msg.senderId != myId) runCatching { container.api.markRead(conversationId) }
                    }
                    "typing_start" -> event.userId?.let { userId ->
                        setTyping(userId, true)
                        typingTimeouts[userId]?.cancel()
                        typingTimeouts[userId] = viewModelScope.launch { delay(6_000); removeTyping(userId) }
                    }
                    "typing_stop" -> event.userId?.let(::removeTyping)
                    "user_online", "user_offline" -> event.userId?.let { userId ->
                        _state.update { s ->
                            val c = s.conversation ?: return@update s
                            s.copy(conversation = c.copy(participants = c.participants.map {
                                if (it.userId == userId) it.copy(isOnline = event.type == "user_online") else it
                            }))
                        }
                    }
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val conversation = container.api.conversation(conversationId)
                val messages = container.api.messages(conversationId)
                conversation to messages
            }.onSuccess { (conversation, messages) ->
                _state.update { it.copy(conversation = conversation, messages = messages, loading = false) }
                runCatching { container.api.markRead(conversationId) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun onDraft(text: String) {
        _state.update { it.copy(draft = text) }
        if (text.isNotBlank() && !typingSent) {
            typingSent = true
            container.realtime.sendTyping(conversationId, true)
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(2_500)
            stopTyping()
        }
    }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, draft = "") }
            stopTyping()
            runCatching {
                container.api.sendMessage(conversationId, text, container.currentUser?.preferredLanguage)
            }.onSuccess { msg ->
                _state.update { s ->
                    if (s.messages.any { it.id == msg.id }) s else s.copy(messages = s.messages + msg)
                }
            }.onFailure { e ->
                _state.update { it.copy(draft = text, error = e.message) }
            }
            _state.update { it.copy(sending = false) }
        }
    }

    fun toggleOriginal(messageId: String) = _state.update { s ->
        s.copy(showOriginal = if (messageId in s.showOriginal) s.showOriginal - messageId else s.showOriginal + messageId)
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun stopTyping() {
        if (typingSent) {
            typingSent = false
            container.realtime.sendTyping(conversationId, false)
        }
    }

    private fun setTyping(userId: String, typing: Boolean) {
        val name = _state.value.conversation?.participants?.firstOrNull { it.userId == userId }?.fullName
            ?.substringBefore(' ') ?: return
        _state.update { s ->
            val names = if (typing) (s.typingNames + name).distinct() else s.typingNames - name
            s.copy(typingNames = names)
        }
    }

    private fun removeTyping(userId: String) = setTyping(userId, false)

    override fun onCleared() {
        stopTyping()
        super.onCleared()
    }
}
