package com.example.services

import com.example.models.SocketEvent
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Keeps track of every open WebSocket, grouped by user. */
object WebSocketHub {

    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    private val json = Json { encodeDefaults = true }

    /** @return true when this is the user's first open connection (they just came online). */
    fun connect(userId: UUID, session: WebSocketSession): Boolean = synchronized(sessions) {
        val set = sessions.getOrPut(userId) { ConcurrentHashMap.newKeySet() }
        val first = set.isEmpty()
        set.add(session)
        first
    }

    /** @return true when the user has no connections left (they just went offline). */
    fun disconnect(userId: UUID, session: WebSocketSession): Boolean = synchronized(sessions) {
        val set = sessions[userId] ?: return true
        set.remove(session)
        if (set.isEmpty()) {
            sessions.remove(userId)
            true
        } else {
            false
        }
    }

    fun isOnline(userId: UUID): Boolean = !sessions[userId].isNullOrEmpty()

    suspend fun sendToUser(userId: UUID, event: SocketEvent) {
        val text = json.encodeToString(SocketEvent.serializer(), event)
        sessions[userId]?.toList()?.forEach { session ->
            runCatching { session.send(Frame.Text(text)) }
        }
    }

    suspend fun sendToUsers(userIds: Collection<UUID>, event: SocketEvent) {
        userIds.distinct().forEach { sendToUser(it, event) }
    }
}
