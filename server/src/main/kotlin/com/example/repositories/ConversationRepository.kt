package com.example.repositories

import com.example.database.Db
import com.example.database.execute
import com.example.database.exists
import com.example.database.instant
import com.example.database.queryList
import com.example.database.querySingle
import com.example.database.uuid
import com.example.database.uuidArray
import com.example.database.uuidOrNull
import com.example.models.ChatMessage
import com.example.models.ConversationSummary
import com.example.models.Participant
import com.example.utils.NotFoundException
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

data class Recipient(val userId: UUID, val preferredLanguage: String)

class ConversationRepository {

    /** Returns the existing private chat between the two users, or creates one. */
    fun createPrivateConversation(currentUserId: UUID, otherUserId: UUID): UUID = Db.transaction { c ->
        require(currentUserId != otherUserId) { "You cannot start a conversation with yourself." }

        if (!c.exists("SELECT 1 FROM app_users WHERE id = ?", otherUserId)) {
            throw NotFoundException("The selected user does not exist.")
        }

        // Serialise concurrent "start chat" taps for the same pair of users.
        val pairKey = listOf(currentUserId, otherUserId).map { it.toString() }.sorted().joinToString(":")
        c.querySingle("SELECT pg_advisory_xact_lock(hashtext(?))", pairKey) { true }

        val existing = c.querySingle(
            """
            SELECT c.id
            FROM conversations c
            JOIN conversation_participants p ON p.conversation_id = c.id
            WHERE c.conversation_type = 'private'
            GROUP BY c.id
            HAVING COUNT(*) = 2
               AND COUNT(*) FILTER (WHERE p.user_id IN (?, ?)) = 2
            LIMIT 1
            """.trimIndent(),
            currentUserId, otherUserId
        ) { it.uuid("id") }
        if (existing != null) return@transaction existing

        val id = c.querySingle(
            """
            INSERT INTO conversations (conversation_type, created_by)
            VALUES ('private', ?)
            RETURNING id
            """.trimIndent(),
            currentUserId
        ) { it.uuid("id") }!!

        addParticipant(c, id, currentUserId)
        addParticipant(c, id, otherUserId)
        id
    }

    fun createGroupConversation(currentUserId: UUID, title: String, participantIds: List<UUID>): UUID =
        Db.transaction { c ->
            val name = title.trim()
            require(name.isNotEmpty() && name.length <= 100) { "Group name must be between 1 and 100 characters." }

            val everyone = (participantIds + currentUserId).distinct()
            require(everyone.size >= 2) { "Pick at least one other person for the group." }

            val found = c.querySingle(
                "SELECT COUNT(*) AS n FROM app_users WHERE id = ANY (?)",
                c.uuidArray(everyone)
            ) { it.getInt("n") } ?: 0
            if (found != everyone.size) throw NotFoundException("One or more selected users do not exist.")

            val id = c.querySingle(
                """
                INSERT INTO conversations (conversation_type, name, created_by)
                VALUES ('group', ?, ?)
                RETURNING id
                """.trimIndent(),
                name, currentUserId
            ) { it.uuid("id") }!!

            everyone.forEach { addParticipant(c, id, it) }
            id
        }

    fun isParticipant(conversationId: UUID, userId: UUID): Boolean = Db.withConnection { c ->
        c.exists(
            "SELECT 1 FROM conversation_participants WHERE conversation_id = ? AND user_id = ?",
            conversationId, userId
        )
    }

    fun participantIds(conversationId: UUID): List<UUID> = Db.withConnection { c ->
        c.queryList(
            "SELECT user_id FROM conversation_participants WHERE conversation_id = ?",
            conversationId
        ) { it.uuid("user_id") }
    }

    fun recipients(conversationId: UUID, senderId: UUID): List<Recipient> = Db.withConnection { c ->
        c.queryList(
            """
            SELECT u.id, u.preferred_language
            FROM conversation_participants p
            JOIN app_users u ON u.id = p.user_id
            WHERE p.conversation_id = ? AND p.user_id <> ?
            """.trimIndent(),
            conversationId, senderId
        ) { Recipient(it.uuid("id"), it.getString("preferred_language")) }
    }

    fun participants(conversationId: UUID): List<Participant> = Db.withConnection { c ->
        participantsFor(c, listOf(conversationId))[conversationId].orEmpty()
    }

    fun listForUser(userId: UUID): List<ConversationSummary> = Db.withConnection { c ->
        loadSummaries(c, userId, conversationId = null)
    }

    fun getForUser(userId: UUID, conversationId: UUID): ConversationSummary? = Db.withConnection { c ->
        loadSummaries(c, userId, conversationId).firstOrNull()
    }

    fun insertMessage(
        conversationId: UUID,
        senderId: UUID,
        text: String,
        language: String?,
        audioData: String? = null,
        imageData: String? = null,
        stickerId: String? = null
    ): ChatMessage =
        Db.transaction { c ->
            val id = c.querySingle(
                """
                INSERT INTO messages (conversation_id, sender_id, original_text, original_language,
                                      audio_data, image_data, sticker_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """.trimIndent(),
                conversationId, senderId, text, language, audioData, imageData, stickerId
            ) { it.uuid("id") }!!

            c.execute("UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", conversationId)
            c.execute(
                """
                UPDATE conversation_participants SET last_read_at = CURRENT_TIMESTAMP
                WHERE conversation_id = ? AND user_id = ?
                """.trimIndent(),
                conversationId, senderId
            )
            loadMessage(c, id, viewerId = senderId)!!
        }

    fun messageForViewer(messageId: UUID, viewerId: UUID): ChatMessage? = Db.withConnection { c ->
        loadMessage(c, messageId, viewerId)
    }

    /** Oldest-first page of messages, each with the viewer's translation (if any). */
    fun history(conversationId: UUID, viewerId: UUID, limit: Int, offset: Int): List<ChatMessage> =
        Db.withConnection { c ->
            c.queryList(
                """
                $messageSelect
                WHERE m.conversation_id = ? AND m.is_deleted = FALSE
                  -- A cleared chat hides everything sent up to the clear.
                  AND m.created_at > COALESCE(
                      (SELECT p2.cleared_at FROM conversation_participants p2
                        WHERE p2.conversation_id = m.conversation_id AND p2.user_id = ?),
                      '-infinity'::timestamptz)
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT ? OFFSET ?
                """.trimIndent(),
                viewerId, viewerId, conversationId, limit, offset
            ) { it.toMessage() }.reversed()
        }

    fun markRead(conversationId: UUID, userId: UUID) {
        Db.withConnection { c ->
            c.execute(
                """
                UPDATE conversation_participants SET last_read_at = CURRENT_TIMESTAMP
                WHERE conversation_id = ? AND user_id = ?
                """.trimIndent(),
                conversationId, userId
            )
        }
    }

    /** Hides every message so far from this person's view only — the other
        people in the conversation keep their copy, and anything sent after
        the clear still arrives. */
    fun clearForUser(conversationId: UUID, userId: UUID) {
        Db.withConnection { c ->
            c.execute(
                """
                UPDATE conversation_participants SET cleared_at = CURRENT_TIMESTAMP
                WHERE conversation_id = ? AND user_id = ?
                """.trimIndent(),
                conversationId, userId
            )
        }
    }

    // ---------------------------------------------------------------------

    private val messageSelect = """
        SELECT m.id, m.conversation_id, m.sender_id, su.full_name AS sender_name,
               m.original_text, m.original_language, m.created_at,
               m.audio_data, m.image_data, m.sticker_id,
               mt.translated_text, mt.target_language,
               -- Everyone except the sender whose last_read_at is at or
              -- after this message was sent has read it.
              (SELECT COALESCE(array_agg(p.user_id::text), '{}'::text[])
                 FROM conversation_participants p
                WHERE p.conversation_id = m.conversation_id
                  AND p.user_id <> m.sender_id
                  AND p.last_read_at >= m.created_at) AS read_by
        FROM messages m
        JOIN app_users su ON su.id = m.sender_id
        LEFT JOIN message_translations mt ON mt.message_id = m.id AND mt.recipient_id = ?
    """.trimIndent()

    private fun loadMessage(c: Connection, messageId: UUID, viewerId: UUID): ChatMessage? =
        c.querySingle("$messageSelect WHERE m.id = ?", viewerId, messageId) { it.toMessage() }

    private fun loadSummaries(c: Connection, userId: UUID, conversationId: UUID?): List<ConversationSummary> {
        val filter = if (conversationId != null) "AND c.id = ?" else ""
        val params = buildList<Any?> {
            add(userId)
            if (conversationId != null) add(conversationId)
        }.toTypedArray()

        data class Row(
            val id: UUID,
            val isGroup: Boolean,
            val name: String?,
            val createdBy: UUID?,
            val createdAt: String,
            val updatedAt: String,
            val unread: Int,
            val lastMessage: ChatMessage?
        )

        val rows = c.queryList(
            """
            SELECT c.id, c.conversation_type, c.name, c.created_by, c.created_at, c.updated_at,
                   (SELECT COUNT(*) FROM messages um
                     WHERE um.conversation_id = c.id
                       AND um.sender_id <> me.user_id
                       AND um.is_deleted = FALSE
                       AND um.created_at > COALESCE(me.cleared_at, '-infinity'::timestamptz)
                       AND um.created_at > COALESCE(me.last_read_at, me.joined_at)) AS unread,
                   lm.id AS lm_id, lm.sender_id AS lm_sender_id, lu.full_name AS lm_sender_name,
                   lm.original_text AS lm_text, lm.original_language AS lm_language,
                   lm.audio_data AS lm_audio, lm.image_data AS lm_image, lm.sticker_id AS lm_sticker,
                   lm.created_at AS lm_created_at,
                   lt.translated_text AS lm_translated, lt.target_language AS lm_target
            FROM conversation_participants me
            JOIN conversations c ON c.id = me.conversation_id
            LEFT JOIN LATERAL (
                SELECT * FROM messages m
                WHERE m.conversation_id = c.id AND m.is_deleted = FALSE
                  AND m.created_at > COALESCE(me.cleared_at, '-infinity'::timestamptz)
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT 1
            ) lm ON TRUE
            LEFT JOIN app_users lu ON lu.id = lm.sender_id
            LEFT JOIN message_translations lt ON lt.message_id = lm.id AND lt.recipient_id = me.user_id
            WHERE me.user_id = ? $filter
            ORDER BY c.updated_at DESC
            """.trimIndent(),
            *params
        ) { rs ->
            val id = rs.uuid("id")
            Row(
                id = id,
                isGroup = rs.getString("conversation_type") == "group",
                name = rs.getString("name"),
                createdBy = rs.uuidOrNull("created_by"),
                createdAt = rs.instant("created_at"),
                updatedAt = rs.instant("updated_at"),
                unread = rs.getInt("unread"),
                lastMessage = rs.uuidOrNull("lm_id")?.let { lmId ->
                    ChatMessage(
                        id = lmId.toString(),
                        conversationId = id.toString(),
                        senderId = rs.uuid("lm_sender_id").toString(),
                        senderName = rs.getString("lm_sender_name"),
                        originalText = rs.getString("lm_text"),
                        originalLanguage = rs.getString("lm_language"),
                        translatedText = rs.getString("lm_translated"),
                        targetLanguage = rs.getString("lm_target"),
                        createdAt = rs.instant("lm_created_at"),
                        audioData = rs.getString("lm_audio"),
                        imageData = rs.getString("lm_image"),
                        stickerId = rs.getString("lm_sticker")
                    )
                }
            )
        }

        if (rows.isEmpty()) return emptyList()
        val participants = participantsFor(c, rows.map { it.id })

        return rows.map { row ->
            val people = participants[row.id].orEmpty()
            val title = when {
                row.isGroup -> row.name ?: "Group"
                else -> people.firstOrNull { it.userId != userId.toString() }?.fullName ?: "Conversation"
            }
            ConversationSummary(
                id = row.id.toString(),
                title = title,
                isGroup = row.isGroup,
                createdBy = row.createdBy?.toString(),
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                participants = people,
                lastMessage = row.lastMessage,
                unreadCount = row.unread
            )
        }
    }

    private fun participantsFor(c: Connection, conversationIds: List<UUID>): Map<UUID, List<Participant>> =
        c.queryList(
            """
            SELECT p.conversation_id, u.id, u.username, u.full_name, u.preferred_language, u.is_online, u.profile_picture
            FROM conversation_participants p
            JOIN app_users u ON u.id = p.user_id
            WHERE p.conversation_id = ANY (?)
            ORDER BY p.joined_at, u.full_name
            """.trimIndent(),
            c.uuidArray(conversationIds)
        ) { rs ->
            rs.uuid("conversation_id") to Participant(
                userId = rs.uuid("id").toString(),
                username = rs.getString("username"),
                fullName = rs.getString("full_name"),
                preferredLanguage = rs.getString("preferred_language"),
                isOnline = rs.getBoolean("is_online"),
                profilePicture = rs.getString("profile_picture")
            )
        }.groupBy({ it.first }, { it.second })

    private fun addParticipant(c: Connection, conversationId: UUID, userId: UUID) {
        c.execute(
            """
            INSERT INTO conversation_participants (conversation_id, user_id)
            VALUES (?, ?)
            ON CONFLICT (conversation_id, user_id) DO NOTHING
            """.trimIndent(),
            conversationId, userId
        )
    }

    private fun ResultSet.toMessage() = ChatMessage(
        id = uuid("id").toString(),
        conversationId = uuid("conversation_id").toString(),
        senderId = uuid("sender_id").toString(),
        senderName = getString("sender_name"),
        originalText = getString("original_text"),
        originalLanguage = getString("original_language"),
        translatedText = getString("translated_text"),
        targetLanguage = getString("target_language"),
        createdAt = instant("created_at"),
        audioData = getString("audio_data"),
        imageData = getString("image_data"),
        stickerId = getString("sticker_id"),
        readBy = readBy()
    )

    /** The read_by column is a Postgres text[] — an empty array, never null. */
    private fun ResultSet.readBy(): List<String> {
        val array = getArray("read_by") ?: return emptyList()
        return (array.array as? Array<*>)?.filterIsInstance<String>() ?: emptyList()
    }
}
