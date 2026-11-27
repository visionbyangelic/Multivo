package com.example.multivo.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiException(message: String, val status: Int = 0) : Exception(message)

val MultivoJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Talks to the Ktor server. [serverUrl] and [token] are looked up for every call
 * so changing the server address or signing in takes effect immediately.
 */
class MultivoApi(
    private val serverUrl: suspend () -> String,
    private val token: () -> String?,
    engine: HttpClientEngine = OkHttp.create()
) {
    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(MultivoJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        install(WebSockets) { pingIntervalMillis = 20_000 }
    }

    // ---------- Auth ----------
    suspend fun register(request: RegisterRequest): AuthResponse = send(HttpMethod.Post, "/auth/register", request)
    suspend fun login(request: LoginRequest): AuthResponse = send(HttpMethod.Post, "/auth/login", request)
    suspend fun me(): UserProfile = send(HttpMethod.Get, "/users/me")
    suspend fun updateProfile(request: UpdateProfileRequest): UserProfile = send(HttpMethod.Put, "/users/me", request)

    // ---------- People ----------
    suspend fun searchUsers(query: String): List<PublicUser> =
        send(HttpMethod.Get, "/users/search?q=${query.encodeURLParameter()}")

    suspend fun contacts(): List<PublicUser> = send(HttpMethod.Get, "/contacts")
    suspend fun incomingRequests(): List<ConnectionRequest> = send(HttpMethod.Get, "/contacts/requests")
    suspend fun sendContactRequest(userId: String): ConnectionRequest =
        send(HttpMethod.Post, "/contacts/requests", CreateConnectionRequest(userId))
    suspend fun acceptRequest(id: String): ConnectionRequest = send(HttpMethod.Post, "/contacts/requests/$id/accept")
    suspend fun rejectRequest(id: String): ConnectionRequest = send(HttpMethod.Post, "/contacts/requests/$id/reject")
    suspend fun removeContact(userId: String): MessageResponse = send(HttpMethod.Delete, "/contacts/$userId")

    // ---------- Chats ----------
    suspend fun conversations(): List<ConversationSummary> = send(HttpMethod.Get, "/conversations")
    suspend fun conversation(id: String): ConversationSummary = send(HttpMethod.Get, "/conversations/$id")
    suspend fun startPrivateChat(userId: String): ConversationSummary =
        send(HttpMethod.Post, "/conversations/private", CreatePrivateConversationRequest(userId))
    suspend fun createGroup(title: String, participantIds: List<String>): ConversationSummary =
        send(HttpMethod.Post, "/conversations/groups", CreateGroupConversationRequest(title, participantIds))
    suspend fun messages(conversationId: String, limit: Int = 100): List<ChatMessage> =
        send(HttpMethod.Get, "/conversations/$conversationId/messages?limit=$limit")
    suspend fun sendMessage(conversationId: String, text: String, language: String?): ChatMessage =
        send(HttpMethod.Post, "/conversations/$conversationId/messages", SendMessageRequest(text, language))
    suspend fun markRead(conversationId: String): MessageResponse =
        send(HttpMethod.Post, "/conversations/$conversationId/read")

    // ---------- Notifications ----------
    suspend fun notifications(): List<NotificationItem> = send(HttpMethod.Get, "/notifications")
    suspend fun markAllNotificationsRead(): MessageResponse = send(HttpMethod.Post, "/notifications/read-all")

    suspend fun webSocketUrl(): String {
        val base = serverUrl().replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        return "$base/ws?token=${token().orEmpty().encodeURLParameter()}"
    }

    // ---------------------------------------------------------------------

    private suspend inline fun <reified T> send(method: HttpMethod, path: String, body: Any? = null): T {
        val response = execute(method, path) {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        if (!response.status.isSuccess()) {
            val message = runCatching { response.body<ApiError>().error }.getOrNull()
                ?: "Server error (${response.status.value})."
            throw ApiException(message, response.status.value)
        }
        return response.body()
    }

    private suspend fun execute(method: HttpMethod, path: String, block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val url = serverUrl().trimEnd('/') + path
        return try {
            client.request(url) {
                this.method = method
                token()?.let { bearerAuth(it) }
                block()
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Can't connect right now. Check your internet connection and try again.")
        }
    }
}
