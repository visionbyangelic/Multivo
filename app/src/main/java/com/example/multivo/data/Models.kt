package com.example.multivo.data

import kotlinx.serialization.Serializable

// These mirror the server's JSON exactly (see server/.../models/Models.kt).

@Serializable data class ApiError(val error: String)
@Serializable data class MessageResponse(val message: String)
@Serializable data class Language(val code: String, val name: String)

@Serializable
data class RegisterRequest(
    val username: String,
    val fullName: String,
    val email: String,
    val password: String,
    val preferredLanguage: String
)

@Serializable data class LoginRequest(val identifier: String, val password: String)

@Serializable data class AuthResponse(val message: String, val token: String, val user: UserProfile)

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

@Serializable
data class PublicUser(
    val id: String,
    val username: String,
    val fullName: String,
    val profilePicture: String? = null,
    val preferredLanguage: String,
    val isOnline: Boolean = false,
    val relationship: String = "none"
)

@Serializable data class CreateConnectionRequest(val receiverId: String)

@Serializable
data class ConnectionRequest(
    val id: String,
    val sender: PublicUser,
    val receiver: PublicUser,
    val status: String,
    val createdAt: String
)

@Serializable data class CreatePrivateConversationRequest(val userId: String)
@Serializable data class CreateGroupConversationRequest(val title: String, val participantIds: List<String>)
@Serializable data class SendMessageRequest(val text: String, val originalLanguage: String? = null)

@Serializable
data class Participant(
    val userId: String,
    val username: String,
    val fullName: String,
    val preferredLanguage: String,
    val isOnline: Boolean
)

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val originalText: String,
    val originalLanguage: String? = null,
    val translatedText: String? = null,
    val targetLanguage: String? = null,
    val createdAt: String
)

@Serializable
data class ConversationSummary(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val createdBy: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val participants: List<Participant> = emptyList(),
    val lastMessage: ChatMessage? = null,
    val unreadCount: Int = 0
)

@Serializable
data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val referenceId: String? = null,
    val isRead: Boolean,
    val createdAt: String
)

@Serializable
data class SocketEvent(
    val type: String,
    val conversationId: String? = null,
    val userId: String? = null,
    val message: ChatMessage? = null,
    val notification: NotificationItem? = null,
    val timestamp: Long = 0
)

@Serializable data class ClientSocketEvent(val type: String, val conversationId: String? = null)
