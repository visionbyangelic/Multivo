package com.example.models

import kotlinx.serialization.Serializable

// ---------- Errors / misc ----------

@Serializable
data class ApiError(val error: String)

@Serializable
data class MessageResponse(val message: String)

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val translation: String
)

@Serializable
data class Language(val code: String, val name: String)

// ---------- Auth ----------

@Serializable
data class RegisterRequest(
    val username: String,
    val fullName: String,
    val email: String,
    val password: String,
    val preferredLanguage: String = "en"
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val message: String,
    val token: String,
    val user: UserProfile
)

// ---------- Users ----------

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val preferredLanguage: String,
    val isOnline: Boolean = false,
    val lastSeen: String? = null
)

@Serializable
data class UpdateProfileRequest(
    val fullName: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val preferredLanguage: String
)

/** What another user can see about someone. */
@Serializable
data class PublicUser(
    val id: String,
    val username: String,
    val fullName: String,
    val profilePicture: String? = null,
    val preferredLanguage: String,
    val isOnline: Boolean = false,
    /** contact | request_sent | request_received | none */
    val relationship: String = "none"
)

// ---------- Contacts ----------

@Serializable
data class CreateConnectionRequest(val receiverId: String)

@Serializable
data class ConnectionRequestResponse(
    val id: String,
    val sender: PublicUser,
    val receiver: PublicUser,
    val status: String,
    val createdAt: String
)

// ---------- Conversations & messages ----------

@Serializable
data class CreatePrivateConversationRequest(val userId: String)

@Serializable
data class CreateGroupConversationRequest(
    val title: String,
    val participantIds: List<String>
)

@Serializable
data class SendMessageRequest(
    val text: String,
    val originalLanguage: String? = null
)

@Serializable
data class Participant(
    val userId: String,
    val username: String,
    val fullName: String,
    val preferredLanguage: String,
    val isOnline: Boolean,
    // Added 27 Nov 2026: the chat list needs the other person's picture,
    // and the Android app reads it the same way. Null when unset.
    val profilePicture: String? = null
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val originalText: String,
    val originalLanguage: String? = null,
    /** Translation into the *viewer's* language, if one was made. */
    val translatedText: String? = null,
    val targetLanguage: String? = null,
    val createdAt: String
)

@Serializable
data class ConversationSummary(
    val id: String,
    /** Group name, or the other person's name for private chats. */
    val title: String,
    val isGroup: Boolean,
    val createdBy: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val participants: List<Participant> = emptyList(),
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0
)

// ---------- Notifications ----------

@Serializable
data class NotificationResponse(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val referenceId: String? = null,
    val isRead: Boolean,
    val createdAt: String
)

// ---------- Real-time ----------

/** Server -> client WebSocket event. */
@Serializable
data class SocketEvent(
    /** new_message | typing_start | typing_stop | user_online | user_offline | notification */
    val type: String,
    val conversationId: String? = null,
    val userId: String? = null,
    val message: ChatMessage? = null,
    val notification: NotificationResponse? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/** Client -> server WebSocket event. */
@Serializable
data class ClientSocketEvent(
    val type: String,
    val conversationId: String? = null
)
