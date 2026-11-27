package com.example.multivo

import com.example.multivo.data.LoginRequest
import com.example.multivo.data.MultivoApi
import com.example.multivo.data.MultivoJson
import com.example.multivo.data.RegisterRequest
import com.example.multivo.data.SocketEvent
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Optional check of the app's networking code against a REAL running server.
 * Skipped unless MULTIVO_LIVE_URL is set, e.g.
 *   ./gradlew :server:run            (in one terminal)
 *   MULTIVO_LIVE_URL=http://localhost:8080 ./gradlew :app:testDebugUnitTest --tests '*LiveServerTest*'
 */
class LiveServerTest {

    private val url = System.getenv("MULTIVO_LIVE_URL")

    @Test fun `register, connect, chat and receive a live message`() = runBlocking {
        assumeTrue("MULTIVO_LIVE_URL not set", !url.isNullOrBlank())
        val suffix = System.currentTimeMillis().toString().takeLast(6)

        var tokenA: String? = null
        var tokenB: String? = null
        val a = MultivoApi(serverUrl = { url!! }, token = { tokenA })
        val b = MultivoApi(serverUrl = { url!! }, token = { tokenB })

        val userA = a.register(RegisterRequest("sender$suffix", "Sender Test", "sender$suffix@example.com", "password123", "en"))
        tokenA = userA.token
        b.register(RegisterRequest("reader$suffix", "Reader Test", "reader$suffix@example.com", "password123", "fr"))
        tokenB = b.login(LoginRequest("reader$suffix", "password123")).token
        val readerId = b.me().id

        val request = a.sendContactRequest(readerId)
        b.acceptRequest(b.incomingRequests().first { it.id == request.id }.id)
        assertEquals(listOf(readerId), a.contacts().map { it.id })

        val chat = a.startPrivateChat(readerId)

        val received = withTimeout(10_000) {
            var event: SocketEvent? = null
            b.client.webSocket(b.webSocketUrl()) {
                launch { delay(500); a.sendMessage(chat.id, "Bonjour from the app test", "en") }
                while (event == null) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        val e = MultivoJson.decodeFromString(SocketEvent.serializer(), frame.readText())
                        if (e.type == "new_message") event = e
                    }
                }
            }
            event!!
        }
        assertEquals("Bonjour from the app test", received.message?.originalText)
        assertEquals(1, b.conversations().first { it.id == chat.id }.unreadCount)
        b.markRead(chat.id)
        assertEquals(0, b.conversations().first { it.id == chat.id }.unreadCount)
    }
}
