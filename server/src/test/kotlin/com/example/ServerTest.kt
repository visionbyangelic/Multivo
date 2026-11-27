package com.example

import com.example.models.ApiError
import com.example.models.AuthResponse
import com.example.models.ChatMessage
import com.example.models.ConnectionRequestResponse
import com.example.models.ConversationSummary
import com.example.models.CreateConnectionRequest
import com.example.models.CreateGroupConversationRequest
import com.example.models.CreatePrivateConversationRequest
import com.example.models.HealthResponse
import com.example.models.LoginRequest
import com.example.models.NotificationResponse
import com.example.models.PublicUser
import com.example.models.RegisterRequest
import com.example.models.SendMessageRequest
import com.example.models.SocketEvent
import com.example.models.UpdateProfileRequest
import com.example.models.UserProfile
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerTest {

    @BeforeTest
    fun cleanDatabase() = TestDatabase.reset()

    private fun app(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application { module(TestDatabase.config(), FakeTranslator) }
        block()
    }

    @Test
    fun `root and health respond`() = app {
        val client = jsonClient()
        assertEquals(HttpStatusCode.OK, client.get("/").status)
        val health = client.get("/health").body<HealthResponse>()
        assertEquals("ok", health.status)
        assertEquals("connected", health.database)
        assertEquals("enabled", health.translation)
    }

    @Test
    fun `register validates input and rejects duplicates`() = app {
        val client = jsonClient()

        val weak = client.post("/auth/register") {
            json(RegisterRequest("alice", "Alice", "alice@example.com", "short", "en"))
        }
        assertEquals(HttpStatusCode.BadRequest, weak.status)
        assertEquals("Password must contain at least 8 characters.", weak.body<ApiError>().error)

        val ok = client.register("alice", "en")
        assertEquals("alice", ok.user.username)
        assertTrue(ok.token.isNotBlank())

        val duplicate = client.post("/auth/register") {
            json(RegisterRequest("alice", "Alice Again", "other@example.com", "password123", "en"))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
    }

    @Test
    fun `login works with username or email and rejects a wrong password`() = app {
        val client = jsonClient()
        client.register("alice", "en")

        val byEmail = client.post("/auth/login") { json(LoginRequest("ALICE@example.com", "password123")) }
        assertEquals(HttpStatusCode.OK, byEmail.status)

        val byName = client.post("/auth/login") { json(LoginRequest("alice", "password123")) }
        val token = byName.body<AuthResponse>().token
        val me = client.get("/auth/me") { bearerAuth(token) }.body<UserProfile>()
        assertEquals("alice@example.com", me.email)

        val wrong = client.post("/auth/login") { json(LoginRequest("alice", "nope-nope")) }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
    }

    @Test
    fun `protected routes need a valid token`() = app {
        val client = jsonClient()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/conversations").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/conversations") { bearerAuth("garbage") }.status)
    }

    @Test
    fun `contact request, accept, chat and translation end to end`() = app {
        val client = jsonClient()
        val alice = client.register("alice", "en")
        val bob = client.register("bob", "fr")
        val carol = client.register("carol", "es")

        // Alice finds Bob and sends a request.
        val found = client.get("/users/search?q=bob") { bearerAuth(alice.token) }.body<List<PublicUser>>()
        assertEquals(listOf("bob"), found.map { it.username })
        assertEquals("none", found.single().relationship)

        val request = client.post("/contacts/requests") {
            json(CreateConnectionRequest(bob.user.id), alice.token)
        }
        assertEquals(HttpStatusCode.Created, request.status)
        val requestId = request.body<ConnectionRequestResponse>().id

        // Sending twice is refused.
        val again = client.post("/contacts/requests") { json(CreateConnectionRequest(bob.user.id), alice.token) }
        assertEquals(HttpStatusCode.Conflict, again.status)

        // Bob sees it, gets a notification, and accepts.
        val incoming = client.get("/contacts/requests") { bearerAuth(bob.token) }
            .body<List<ConnectionRequestResponse>>()
        assertEquals(listOf(requestId), incoming.map { it.id })
        val bobNotes = client.get("/notifications") { bearerAuth(bob.token) }.body<List<NotificationResponse>>()
        assertEquals("contact_request", bobNotes.first().type)

        val accepted = client.post("/contacts/requests/$requestId/accept") { bearerAuth(bob.token) }
        assertEquals("accepted", accepted.body<ConnectionRequestResponse>().status)

        val aliceContacts = client.get("/contacts") { bearerAuth(alice.token) }.body<List<PublicUser>>()
        assertEquals(listOf(bob.user.id), aliceContacts.map { it.id })

        // Alice starts a chat; doing it again returns the same chat.
        val chat = client.post("/conversations/private") {
            json(CreatePrivateConversationRequest(bob.user.id), alice.token)
        }.body<ConversationSummary>()
        val chatAgain = client.post("/conversations/private") {
            json(CreatePrivateConversationRequest(alice.user.id), bob.token)
        }.body<ConversationSummary>()
        assertEquals(chat.id, chatAgain.id)
        assertEquals("Bob Tester", chat.title)
        assertEquals("Alice Tester", chatAgain.title)

        // Alice writes in English; Bob reads it in French.
        val sent = client.post("/conversations/${chat.id}/messages") {
            json(SendMessageRequest("Hello Bob", "en"), alice.token)
        }
        assertEquals(HttpStatusCode.Created, sent.status)
        assertNull(sent.body<ChatMessage>().translatedText)

        val bobView = client.get("/conversations/${chat.id}/messages") { bearerAuth(bob.token) }
            .body<List<ChatMessage>>()
        assertEquals("Hello Bob", bobView.single().originalText)
        assertEquals("[fr] Hello Bob", bobView.single().translatedText)
        assertEquals("fr", bobView.single().targetLanguage)

        val aliceView = client.get("/conversations/${chat.id}/messages") { bearerAuth(alice.token) }
            .body<List<ChatMessage>>()
        assertNull(aliceView.single().translatedText)

        // Unread count for Bob goes 1 -> 0 after reading.
        val bobList = client.get("/conversations") { bearerAuth(bob.token) }.body<List<ConversationSummary>>()
        assertEquals(1, bobList.single().unreadCount)
        assertEquals("[fr] Hello Bob", bobList.single().lastMessage?.translatedText)
        client.post("/conversations/${chat.id}/read") { bearerAuth(bob.token) }
        val bobListAfter = client.get("/conversations") { bearerAuth(bob.token) }.body<List<ConversationSummary>>()
        assertEquals(0, bobListAfter.single().unreadCount)

        // Carol is not in the chat.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.get("/conversations/${chat.id}/messages") { bearerAuth(carol.token) }.status
        )
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/conversations/${chat.id}/messages") {
                json(SendMessageRequest("let me in"), carol.token)
            }.status
        )
    }

    @Test
    fun `group messages are translated for each member`() = app {
        val client = jsonClient()
        val alice = client.register("alice", "en")
        val bob = client.register("bob", "fr")
        val carol = client.register("carol", "es")
        val dave = client.register("dave", "en")

        val group = client.post("/conversations/groups") {
            json(
                CreateGroupConversationRequest("Study group", listOf(bob.user.id, carol.user.id, dave.user.id)),
                alice.token
            )
        }.body<ConversationSummary>()
        assertTrue(group.isGroup)
        assertEquals(4, group.participants.size)

        client.post("/conversations/${group.id}/messages") { json(SendMessageRequest("See you at 5"), alice.token) }

        suspend fun viewOf(token: String) = client.get("/conversations/${group.id}/messages") { bearerAuth(token) }
            .body<List<ChatMessage>>().single()

        assertEquals("[fr] See you at 5", viewOf(bob.token).translatedText)
        assertEquals("[es] See you at 5", viewOf(carol.token).translatedText)
        assertNull(viewOf(dave.token).translatedText) // same language as the sender
        assertEquals("en", viewOf(dave.token).originalLanguage) // detected and saved

        val notes = client.get("/notifications") { bearerAuth(carol.token) }.body<List<NotificationResponse>>()
        assertEquals("group_added", notes.first().type)
    }

    @Test
    fun `changing preferred language changes future translations`() = app {
        val client = jsonClient()
        val alice = client.register("alice", "en")
        val bob = client.register("bob", "fr")

        val updated = client.put("/users/me") {
            json(UpdateProfileRequest("Bob Builder", "Hi!", null, "de"), bob.token)
        }.body<UserProfile>()
        assertEquals("de", updated.preferredLanguage)
        assertEquals("Bob Builder", updated.fullName)

        val chat = client.post("/conversations/private") {
            json(CreatePrivateConversationRequest(bob.user.id), alice.token)
        }.body<ConversationSummary>()
        client.post("/conversations/${chat.id}/messages") { json(SendMessageRequest("Good morning"), alice.token) }

        val view = client.get("/conversations/${chat.id}/messages") { bearerAuth(bob.token) }
            .body<List<ChatMessage>>().single()
        assertEquals("[de] Good morning", view.translatedText)
    }

    @Test
    fun `websocket delivers new messages with the receiver's translation`() = app {
        val client = jsonClient()
        val alice = client.register("alice", "en")
        val bob = client.register("bob", "fr")
        val chat = client.post("/conversations/private") {
            json(CreatePrivateConversationRequest(bob.user.id), alice.token)
        }.body<ConversationSummary>()

        client.webSocket("/ws?token=${bob.token}") {
            // Give the server a moment to register the socket, then send.
            kotlinx.coroutines.delay(300)
            client.post("/conversations/${chat.id}/messages") { json(SendMessageRequest("Ping"), alice.token) }

            val event = withTimeout(5_000) {
                var found: SocketEvent? = null
                while (found == null) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        val parsed = AppJson.decodeFromString(SocketEvent.serializer(), frame.readText())
                        if (parsed.type == "new_message") found = parsed
                    }
                }
                found
            }
            assertEquals(chat.id, event.conversationId)
            assertEquals("[fr] Ping", event.message?.translatedText)
        }
    }

    @Test
    fun `unknown routes and bad bodies return json errors`() = app {
        val client = jsonClient()
        val missing = client.get("/nope")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertTrue(missing.bodyAsText().contains("Route not found"))

        val bad = client.post("/auth/login") { json(mapOf("wrong" to "shape")) }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
    }
}
