package com.example.repositories

import com.example.database.Db
import com.example.database.execute
import com.example.database.exists
import com.example.database.instant
import com.example.database.queryList
import com.example.database.querySingle
import com.example.database.uuid
import com.example.models.ConnectionRequestResponse
import com.example.models.PublicUser
import com.example.repositories.UserRepository.Companion.toPublicUser
import com.example.utils.ConflictException
import com.example.utils.NotFoundException
import java.sql.Connection
import java.util.UUID

class ContactRepository {

    private val requestSelect = """
        SELECT r.id, r.status, r.created_at,
               s.id AS s_id, s.username AS s_username, s.full_name AS s_full_name,
               s.profile_picture AS s_profile_picture, s.preferred_language AS s_preferred_language,
               s.is_online AS s_is_online,
               v.id AS v_id, v.username AS v_username, v.full_name AS v_full_name,
               v.profile_picture AS v_profile_picture, v.preferred_language AS v_preferred_language,
               v.is_online AS v_is_online
        FROM connection_requests r
        JOIN app_users s ON s.id = r.sender_id
        JOIN app_users v ON v.id = r.receiver_id
    """.trimIndent()

    /**
     * Sends a contact request. If the other person already asked us, the two
     * requests "meet" and both users become contacts straight away.
     */
    fun sendRequest(senderId: UUID, receiverId: UUID): ConnectionRequestResponse = Db.transaction { c ->
        require(senderId != receiverId) { "You cannot send a request to yourself." }

        if (!c.exists("SELECT 1 FROM app_users WHERE id = ?", receiverId)) {
            throw NotFoundException("That user does not exist.")
        }
        if (c.exists("SELECT 1 FROM contacts WHERE user_id = ? AND contact_user_id = ?", senderId, receiverId)) {
            throw ConflictException("You are already connected.")
        }

        val reverseId = c.querySingle(
            """
            SELECT id FROM connection_requests
            WHERE sender_id = ? AND receiver_id = ? AND status = 'pending'
            """.trimIndent(),
            receiverId, senderId
        ) { it.uuid("id") }

        if (reverseId != null) {
            c.execute(
                "UPDATE connection_requests SET status = 'accepted', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                reverseId
            )
            addContacts(c, senderId, receiverId)
            return@transaction loadRequest(c, reverseId)!!
        }

        val id = c.querySingle(
            """
            INSERT INTO connection_requests (sender_id, receiver_id)
            VALUES (?, ?)
            ON CONFLICT (sender_id, receiver_id) DO UPDATE
                SET status = 'pending', updated_at = CURRENT_TIMESTAMP
                WHERE connection_requests.status <> 'pending'
            RETURNING id
            """.trimIndent(),
            senderId, receiverId
        ) { it.uuid("id") } ?: throw ConflictException("You already sent a request to this user.")

        loadRequest(c, id)!!
    }

    fun incomingRequests(userId: UUID): List<ConnectionRequestResponse> = Db.withConnection { c ->
        c.queryList(
            "$requestSelect WHERE r.receiver_id = ? AND r.status = 'pending' ORDER BY r.created_at DESC",
            userId
        ) { it.toRequest() }
    }

    fun outgoingRequests(userId: UUID): List<ConnectionRequestResponse> = Db.withConnection { c ->
        c.queryList(
            "$requestSelect WHERE r.sender_id = ? AND r.status = 'pending' ORDER BY r.created_at DESC",
            userId
        ) { it.toRequest() }
    }

    /** Accepts or rejects a pending request addressed to [userId]. */
    fun respond(requestId: UUID, userId: UUID, accept: Boolean): ConnectionRequestResponse = Db.transaction { c ->
        val senderId = c.querySingle(
            """
            UPDATE connection_requests
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND receiver_id = ? AND status = 'pending'
            RETURNING sender_id
            """.trimIndent(),
            if (accept) "accepted" else "rejected", requestId, userId
        ) { it.uuid("sender_id") } ?: throw NotFoundException("This request no longer exists.")

        if (accept) addContacts(c, senderId, userId)
        loadRequest(c, requestId)!!
    }

    fun contacts(userId: UUID): List<PublicUser> = Db.withConnection { c ->
        c.queryList(
            """
            SELECT u.id, u.username, u.full_name, u.profile_picture, u.preferred_language, u.is_online,
                   'contact' AS relationship
            FROM contacts ct
            JOIN app_users u ON u.id = ct.contact_user_id
            WHERE ct.user_id = ?
            ORDER BY u.full_name
            """.trimIndent(),
            userId
        ) { it.toPublicUser() }
    }

    fun areContacts(a: UUID, b: UUID): Boolean = Db.withConnection { c ->
        c.exists("SELECT 1 FROM contacts WHERE user_id = ? AND contact_user_id = ?", a, b)
    }

    fun removeContact(userId: UUID, contactId: UUID): Boolean = Db.withConnection { c ->
        c.execute(
            """
            DELETE FROM contacts
            WHERE (user_id = ? AND contact_user_id = ?) OR (user_id = ? AND contact_user_id = ?)
            """.trimIndent(),
            userId, contactId, contactId, userId
        ) > 0
    }

    private fun addContacts(c: Connection, a: UUID, b: UUID) {
        c.execute(
            """
            INSERT INTO contacts (user_id, contact_user_id)
            VALUES (?, ?), (?, ?)
            ON CONFLICT (user_id, contact_user_id) DO NOTHING
            """.trimIndent(),
            a, b, b, a
        )
    }

    private fun loadRequest(c: Connection, id: UUID): ConnectionRequestResponse? =
        c.querySingle("$requestSelect WHERE r.id = ?", id) { it.toRequest() }

    private fun java.sql.ResultSet.toRequest() = ConnectionRequestResponse(
        id = uuid("id").toString(),
        sender = toPublicUser("s_"),
        receiver = toPublicUser("v_"),
        status = getString("status"),
        createdAt = instant("created_at")
    )
}
