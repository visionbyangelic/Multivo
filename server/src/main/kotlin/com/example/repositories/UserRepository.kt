package com.example.repositories

import com.example.database.Db
import com.example.database.execute
import com.example.database.instantOrNull
import com.example.database.queryList
import com.example.database.querySingle
import com.example.database.uuid
import com.example.models.PublicUser
import com.example.models.UpdateProfileRequest
import com.example.models.UserProfile
import java.sql.ResultSet
import java.util.UUID

data class StoredUser(val profile: UserProfile, val passwordHash: String)

class UserRepository {

    private val profileColumns = """
        id, username, full_name, email, bio, profile_picture,
        preferred_language, is_online, last_seen
    """.trimIndent()

    fun createUser(
        username: String,
        fullName: String,
        email: String,
        passwordHash: String,
        preferredLanguage: String
    ): UserProfile = Db.withConnection { c ->
        c.querySingle(
            """
            INSERT INTO app_users (username, full_name, email, password_hash, preferred_language)
            VALUES (?, ?, ?, ?, ?)
            RETURNING $profileColumns
            """.trimIndent(),
            username, fullName, email, passwordHash, preferredLanguage
        ) { it.toProfile() } ?: error("User registration failed")
    }

    fun findByIdentifier(identifier: String): StoredUser? = Db.withConnection { c ->
        c.querySingle(
            """
            SELECT $profileColumns, password_hash
            FROM app_users
            WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?)
            LIMIT 1
            """.trimIndent(),
            identifier, identifier
        ) { StoredUser(it.toProfile(), it.getString("password_hash")) }
    }

    fun findById(id: UUID): UserProfile? = Db.withConnection { c ->
        c.querySingle("SELECT $profileColumns FROM app_users WHERE id = ?", id) { it.toProfile() }
    }

    fun findPublic(viewerId: UUID, userId: UUID): PublicUser? = Db.withConnection { c ->
        c.querySingle(
            """
            SELECT u.id, u.username, u.full_name, u.profile_picture, u.preferred_language, u.is_online,
                   ${relationshipSql("u")} AS relationship
            FROM app_users u
            WHERE u.id = ?
            """.trimIndent(),
            viewerId, viewerId, viewerId, userId
        ) { it.toPublicUser() }
    }

    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserProfile? = Db.withConnection { c ->
        c.querySingle(
            """
            UPDATE app_users
            SET full_name = ?, bio = ?, profile_picture = ?, preferred_language = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            RETURNING $profileColumns
            """.trimIndent(),
            request.fullName, request.bio, request.profilePicture, request.preferredLanguage, userId
        ) { it.toProfile() }
    }

    fun searchUsers(viewerId: UUID, query: String): List<PublicUser> = Db.withConnection { c ->
        val escaped = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val pattern = "%$escaped%"
        c.queryList(
            """
            SELECT u.id, u.username, u.full_name, u.profile_picture, u.preferred_language, u.is_online,
                   ${relationshipSql("u")} AS relationship
            FROM app_users u
            WHERE u.id <> ?
              AND (u.username ILIKE ? OR u.full_name ILIKE ? OR LOWER(u.email) = LOWER(?))
            ORDER BY u.is_online DESC, u.username ASC
            LIMIT 30
            """.trimIndent(),
            viewerId, viewerId, viewerId, viewerId, pattern, pattern, query.trim()
        ) { it.toPublicUser() }
    }

    fun setPresence(userId: UUID, online: Boolean) {
        Db.withConnection { c ->
            c.execute(
                """
                UPDATE app_users
                SET is_online = ?, last_seen = CURRENT_TIMESTAMP
                WHERE id = ?
                """.trimIndent(),
                online, userId
            )
        }
    }

    /** Everyone who should hear about this user's presence: contacts + chat partners. */
    fun relatedUserIds(userId: UUID): List<UUID> = Db.withConnection { c ->
        c.queryList(
            """
            SELECT contact_user_id AS id FROM contacts WHERE user_id = ?
            UNION
            SELECT p2.user_id AS id
            FROM conversation_participants p1
            JOIN conversation_participants p2 ON p2.conversation_id = p1.conversation_id
            WHERE p1.user_id = ? AND p2.user_id <> ?
            """.trimIndent(),
            userId, userId, userId
        ) { it.uuid("id") }
    }

    companion object {
        /** Needs the viewer id bound three times. */
        fun relationshipSql(alias: String) = """
            CASE
                WHEN EXISTS (SELECT 1 FROM contacts ct WHERE ct.user_id = ? AND ct.contact_user_id = $alias.id)
                    THEN 'contact'
                WHEN EXISTS (SELECT 1 FROM connection_requests r
                             WHERE r.sender_id = ? AND r.receiver_id = $alias.id AND r.status = 'pending')
                    THEN 'request_sent'
                WHEN EXISTS (SELECT 1 FROM connection_requests r
                             WHERE r.sender_id = $alias.id AND r.receiver_id = ? AND r.status = 'pending')
                    THEN 'request_received'
                ELSE 'none'
            END
        """.trimIndent()

        fun ResultSet.toProfile() = UserProfile(
            id = uuid("id").toString(),
            username = getString("username"),
            fullName = getString("full_name"),
            email = getString("email"),
            bio = getString("bio"),
            profilePicture = getString("profile_picture"),
            preferredLanguage = getString("preferred_language"),
            isOnline = getBoolean("is_online"),
            lastSeen = instantOrNull("last_seen")
        )

        fun ResultSet.toPublicUser(prefix: String = "") = PublicUser(
            id = uuid("${prefix}id").toString(),
            username = getString("${prefix}username"),
            fullName = getString("${prefix}full_name"),
            profilePicture = getString("${prefix}profile_picture"),
            preferredLanguage = getString("${prefix}preferred_language"),
            isOnline = getBoolean("${prefix}is_online"),
            relationship = if (prefix.isEmpty()) getString("relationship") else "none"
        )
    }
}
