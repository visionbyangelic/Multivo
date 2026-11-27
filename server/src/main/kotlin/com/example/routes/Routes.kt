package com.example.routes

import com.example.database.DatabaseFactory
import com.example.models.ClientSocketEvent
import com.example.models.CreateConnectionRequest
import com.example.models.CreateGroupConversationRequest
import com.example.models.CreatePrivateConversationRequest
import com.example.models.HealthResponse
import com.example.models.LoginRequest
import com.example.models.MessageResponse
import com.example.models.RegisterRequest
import com.example.models.SendMessageRequest
import com.example.models.SocketEvent
import com.example.models.UpdateProfileRequest
import com.example.repositories.NotificationRepository
import com.example.repositories.UserRepository
import com.example.security.JwtConfig
import com.example.services.AuthService
import com.example.services.ChatService
import com.example.services.ContactService
import com.example.services.Languages
import com.example.services.TranslationProvider
import com.example.services.WebSocketHub
import com.example.services.io
import com.example.utils.NotFoundException
import com.example.utils.currentUserId
import com.example.utils.normalizeLanguage
import com.example.utils.parseUuid
import com.example.utils.uuidParameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import java.util.UUID

const val AUTH_JWT = "auth-jwt"

fun Route.healthRoutes(translator: TranslationProvider) {
    get("/") { call.respondText("Multivo API is running.") }

    get("/health") {
        val dbOk = io { DatabaseFactory.isHealthy() }
        call.respond(
            if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthResponse(
                status = if (dbOk) "ok" else "degraded",
                database = if (dbOk) "connected" else "unavailable",
                translation = if (translator.isEnabled) "enabled" else "disabled"
            )
        )
    }

    get("/languages") { call.respond(Languages.supported) }
}

fun Route.authRoutes(auth: AuthService, users: UserRepository) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            call.respond(HttpStatusCode.Created, io { auth.register(request) })
        }
        post("/login") {
            val request = call.receive<LoginRequest>()
            call.respond(io { auth.login(request.identifier, request.password) })
        }
        authenticate(AUTH_JWT) {
            get("/me") {
                val user = io { users.findById(call.currentUserId()) } ?: throw NotFoundException("Account not found.")
                call.respond(user)
            }
        }
    }
}

fun Route.userRoutes(users: UserRepository) {
    authenticate(AUTH_JWT) {
        route("/users") {
            get("/me") {
                val user = io { users.findById(call.currentUserId()) } ?: throw NotFoundException("Account not found.")
                call.respond(user)
            }
            put("/me") {
                val request = call.receive<UpdateProfileRequest>()
                val fullName = request.fullName.trim()
                require(fullName.length in 2..100) { "Full name must be between 2 and 100 characters." }
                require((request.bio?.length ?: 0) <= 500) { "Bio can be at most 500 characters." }
                val cleaned = request.copy(
                    fullName = fullName,
                    bio = request.bio?.trim()?.ifEmpty { null },
                    profilePicture = request.profilePicture?.trim()?.ifEmpty { null },
                    preferredLanguage = normalizeLanguage(request.preferredLanguage)
                )
                val updated = io { users.updateProfile(call.currentUserId(), cleaned) }
                    ?: throw NotFoundException("Account not found.")
                call.respond(updated)
            }
            get("/search") {
                val query = call.request.queryParameters["q"].orEmpty().trim()
                require(query.length >= 2) { "Type at least 2 characters to search." }
                call.respond(io { users.searchUsers(call.currentUserId(), query) })
            }
            get("/{id}") {
                val user = io { users.findPublic(call.currentUserId(), call.uuidParameter("id")) }
                    ?: throw NotFoundException("User not found.")
                call.respond(user)
            }
        }
    }
}

fun Route.contactRoutes(contacts: ContactService) {
    authenticate(AUTH_JWT) {
        route("/contacts") {
            get { call.respond(contacts.list(call.currentUserId())) }

            delete("/{id}") {
                contacts.remove(call.currentUserId(), call.uuidParameter("id"))
                call.respond(MessageResponse("Contact removed."))
            }

            route("/requests") {
                get { call.respond(contacts.incoming(call.currentUserId())) }
                get("/sent") { call.respond(contacts.outgoing(call.currentUserId())) }
                post {
                    val request = call.receive<CreateConnectionRequest>()
                    val result = contacts.sendRequest(call.currentUserId(), parseUuid(request.receiverId, "user id"))
                    call.respond(HttpStatusCode.Created, result)
                }
                post("/{id}/accept") {
                    call.respond(contacts.respond(call.uuidParameter("id"), call.currentUserId(), accept = true))
                }
                post("/{id}/reject") {
                    call.respond(contacts.respond(call.uuidParameter("id"), call.currentUserId(), accept = false))
                }
            }
        }
    }
}

fun Route.conversationRoutes(chat: ChatService) {
    authenticate(AUTH_JWT) {
        route("/conversations") {
            get { call.respond(chat.list(call.currentUserId())) }

            post("/private") {
                val request = call.receive<CreatePrivateConversationRequest>()
                val conversation = chat.startPrivate(call.currentUserId(), parseUuid(request.userId, "user id"))
                call.respond(HttpStatusCode.Created, conversation)
            }

            post("/groups") {
                val request = call.receive<CreateGroupConversationRequest>()
                val conversation = chat.createGroup(
                    call.currentUserId(),
                    request.title,
                    request.participantIds.map { parseUuid(it, "participant id") }
                )
                call.respond(HttpStatusCode.Created, conversation)
            }

            get("/{id}") { call.respond(chat.get(call.currentUserId(), call.uuidParameter("id"))) }

            get("/{id}/participants") {
                call.respond(chat.participants(call.currentUserId(), call.uuidParameter("id")))
            }

            get("/{id}/messages") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                call.respond(chat.history(call.currentUserId(), call.uuidParameter("id"), limit, offset))
            }

            post("/{id}/messages") {
                val request = call.receive<SendMessageRequest>()
                val message = chat.sendMessage(
                    call.currentUserId(),
                    call.uuidParameter("id"),
                    request.text,
                    request.originalLanguage
                )
                call.respond(HttpStatusCode.Created, message)
            }

            post("/{id}/read") {
                chat.markRead(call.currentUserId(), call.uuidParameter("id"))
                call.respond(MessageResponse("Marked as read."))
            }
        }
    }
}

fun Route.notificationRoutes(notifications: NotificationRepository) {
    authenticate(AUTH_JWT) {
        route("/notifications") {
            get { call.respond(io { notifications.list(call.currentUserId()) }) }
            post("/read-all") {
                io { notifications.markAllRead(call.currentUserId()) }
                call.respond(MessageResponse("All notifications marked as read."))
            }
            post("/{id}/read") {
                val ok = io { notifications.markRead(call.currentUserId(), call.uuidParameter("id")) }
                if (!ok) throw NotFoundException("Notification not found.")
                call.respond(MessageResponse("Notification marked as read."))
            }
        }
    }
}

/**
 * One socket per signed-in device: `ws://HOST:8080/ws?token=JWT`
 * (an `Authorization: Bearer JWT` header also works).
 *
 * Client can send: {"type":"typing_start","conversationId":"..."} / typing_stop.
 * Server sends: new_message, typing_start, typing_stop, user_online, user_offline, notification.
 */
fun Route.webSocketRoutes(jwt: JwtConfig, users: UserRepository, chat: ChatService) {
    val json = Json { ignoreUnknownKeys = true }

    webSocket("/ws") {
        val token = call.request.queryParameters["token"]
            ?: call.request.headers["Authorization"]?.removePrefix("Bearer")?.trim()
        val userId: UUID? = token?.let(jwt::userIdFrom)

        if (userId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid or missing token"))
            return@webSocket
        }

        val cameOnline = WebSocketHub.connect(userId, this)
        try {
            if (cameOnline) {
                io { users.setPresence(userId, true) }
                val related = io { users.relatedUserIds(userId) }
                WebSocketHub.sendToUsers(related, SocketEvent(type = "user_online", userId = userId.toString()))
            }

            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val event = runCatching { json.decodeFromString(ClientSocketEvent.serializer(), frame.readText()) }
                    .getOrNull() ?: continue
                val conversationId = event.conversationId
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: continue

                when (event.type) {
                    "typing_start", "typing_stop" -> chat.relayTyping(userId, conversationId, event.type)
                    "read" -> runCatching { chat.markRead(userId, conversationId) }
                }
            }
        } finally {
            val wentOffline = WebSocketHub.disconnect(userId, this)
            if (wentOffline) {
                runCatching {
                    io { users.setPresence(userId, false) }
                    val related = io { users.relatedUserIds(userId) }
                    WebSocketHub.sendToUsers(related, SocketEvent(type = "user_offline", userId = userId.toString()))
                }
            }
        }
    }
}

