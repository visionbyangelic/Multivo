package com.example.services

import com.example.models.ChatMessage
import com.example.models.ConversationSummary
import com.example.models.Participant
import com.example.models.SocketEvent
import com.example.repositories.ConversationRepository
import com.example.repositories.NotificationRepository
import com.example.repositories.TranslationRepository
import com.example.utils.ForbiddenException
import com.example.utils.NotFoundException
import com.example.utils.normalizeLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.UUID

suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

class ChatService(
    private val translator: TranslationProvider,
    private val conversations: ConversationRepository = ConversationRepository(),
    private val translations: TranslationRepository = TranslationRepository(),
    private val notifications: NotificationRepository = NotificationRepository()
) {
    private val log = LoggerFactory.getLogger(ChatService::class.java)

    suspend fun list(userId: UUID): List<ConversationSummary> = io { conversations.listForUser(userId) }

    suspend fun get(userId: UUID, conversationId: UUID): ConversationSummary =
        io { conversations.getForUser(userId, conversationId) }
            ?: throw ForbiddenException("You are not part of this conversation.")

    suspend fun startPrivate(userId: UUID, otherUserId: UUID): ConversationSummary {
        val id = io { conversations.createPrivateConversation(userId, otherUserId) }
        return get(userId, id)
    }

    suspend fun createGroup(userId: UUID, title: String, participantIds: List<UUID>): ConversationSummary {
        val id = io { conversations.createGroupConversation(userId, title, participantIds) }
        val summary = get(userId, id)
        participantIds.distinct().filter { it != userId }.forEach { memberId ->
            val notification = io {
                notifications.create(
                    memberId,
                    "Added to a group",
                    "You were added to \"${summary.title}\".",
                    "group_added",
                    id
                )
            }
            WebSocketHub.sendToUser(memberId, SocketEvent(type = "notification", notification = notification))
        }
        return summary
    }

    suspend fun participants(userId: UUID, conversationId: UUID): List<Participant> {
        requireParticipant(conversationId, userId)
        return io { conversations.participants(conversationId) }
    }

    suspend fun history(userId: UUID, conversationId: UUID, limit: Int, offset: Int): List<ChatMessage> {
        require(limit in 1..100) { "Limit must be between 1 and 100." }
        require(offset >= 0) { "Offset cannot be negative." }
        requireParticipant(conversationId, userId)
        return io { conversations.history(conversationId, userId, limit, offset) }
    }

    suspend fun markRead(userId: UUID, conversationId: UUID) {
        requireParticipant(conversationId, userId)
        io { conversations.markRead(conversationId, userId) }
    }

    /**
     * Saves a message, translates its text into every recipient's preferred
     * language and pushes it to everyone in the conversation in real time.
     *
     * A message needs text or media — a voice note, a photo or a sticker
     * can travel without any text at all. Media is never translated (there
     * is nothing to translate), and it is capped at about 1.5 MB decoded so
     * one upload cannot fill the free database.
     */
    suspend fun sendMessage(
        senderId: UUID,
        conversationId: UUID,
        text: String,
        language: String?,
        audioData: String? = null,
        imageData: String? = null,
        stickerId: String? = null
    ): ChatMessage {
        val body = text.trim()
        require(body.length <= 10_000) { "Message must be at most 10,000 characters." }
        require(body.isNotEmpty() || audioData != null || imageData != null || stickerId != null) {
            "Message needs text, a voice note, a photo or a sticker."
        }
        require((audioData?.length ?: 0) <= 2_000_000) { "Voice note is too large." }
        require((imageData?.length ?: 0) <= 2_000_000) { "Photo is too large." }
        val sourceLanguage = language?.takeIf { it.isNotBlank() }?.let(::normalizeLanguage)

        requireParticipant(conversationId, senderId)

        val saved = io {
            conversations.insertMessage(conversationId, senderId, body, sourceLanguage, audioData, imageData, stickerId)
        }
        val messageId = UUID.fromString(saved.id)
        val recipients = io { conversations.recipients(conversationId, senderId) }

        if (translator.isEnabled && body.isNotEmpty()) {
            translateForRecipients(messageId, body, sourceLanguage, recipients)
        }

        // Each person receives the message with their own translation.
        recipients.forEach { recipient ->
            val view = io { conversations.messageForViewer(messageId, recipient.userId) } ?: return@forEach
            WebSocketHub.sendToUser(
                recipient.userId,
                SocketEvent(type = "new_message", conversationId = conversationId.toString(), message = view)
            )
        }
        val senderView = io { conversations.messageForViewer(messageId, senderId) } ?: saved
        WebSocketHub.sendToUser(
            senderId,
            SocketEvent(type = "new_message", conversationId = conversationId.toString(), message = senderView)
        )
        return senderView
    }

    suspend fun relayTyping(userId: UUID, conversationId: UUID, type: String) {
        if (!io { conversations.isParticipant(conversationId, userId) }) return
        val others = io { conversations.participantIds(conversationId) }.filter { it != userId }
        WebSocketHub.sendToUsers(
            others,
            SocketEvent(type = type, conversationId = conversationId.toString(), userId = userId.toString())
        )
    }

    private suspend fun translateForRecipients(
        messageId: UUID,
        text: String,
        sourceLanguage: String?,
        recipients: List<com.example.repositories.Recipient>
    ) {
        var detected: String? = sourceLanguage
        val cache = mutableMapOf<String, String?>()

        for (recipient in recipients) {
            val target = recipient.preferredLanguage
            if (detected != null && sameLanguage(detected, target)) continue

            val translated = if (cache.containsKey(target)) {
                cache[target]
            } else {
                val result = runCatching { io { translator.translate(text, target, sourceLanguage) } }
                    .onFailure { log.warn("Translation to '$target' failed: ${it.message}") }
                    .getOrNull()
                if (result != null && detected == null && result.detectedSourceLanguage != null) {
                    detected = result.detectedSourceLanguage
                    io { translations.saveDetectedSourceLanguage(messageId, result.detectedSourceLanguage) }
                }
                val value = result?.translatedText?.takeUnless { detected != null && sameLanguage(detected!!, target) }
                cache[target] = value
                value
            }

            if (translated != null) {
                io { translations.saveTranslation(messageId, recipient.userId, translated, target) }
            }
        }
    }

    private fun sameLanguage(a: String, b: String) =
        a.substringBefore('-').equals(b.substringBefore('-'), ignoreCase = true)

    private suspend fun requireParticipant(conversationId: UUID, userId: UUID) {
        if (!io { conversations.isParticipant(conversationId, userId) }) {
            throw ForbiddenException("You are not part of this conversation.")
        }
    }
}
