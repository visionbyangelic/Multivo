package com.example.multivo.data

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps one live connection to the server and re-connects automatically. */
class RealtimeClient(
    private val api: MultivoApi,
    private val scope: CoroutineScope
) {
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var job: Job? = null
    private var session: DefaultClientWebSocketSession? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                runCatching {
                    api.client.webSocket(api.webSocketUrl()) {
                        session = this
                        _connected.value = true
                        backoff = 1_000L
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            runCatching { MultivoJson.decodeFromString(SocketEvent.serializer(), frame.readText()) }
                                .onSuccess { _events.emit(it) }
                        }
                    }
                }
                session = null
                _connected.value = false
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        session = null
        _connected.value = false
    }

    fun sendTyping(conversationId: String, typing: Boolean) {
        val current = session ?: return
        scope.launch {
            runCatching {
                val event = ClientSocketEvent(if (typing) "typing_start" else "typing_stop", conversationId)
                current.send(Frame.Text(MultivoJson.encodeToString(ClientSocketEvent.serializer(), event)))
            }
        }
    }
}
