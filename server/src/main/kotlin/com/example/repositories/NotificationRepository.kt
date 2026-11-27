package com.example.repositories

import com.example.database.Db
import com.example.database.execute
import com.example.database.instant
import com.example.database.queryList
import com.example.database.querySingle
import com.example.database.uuid
import com.example.database.uuidOrNull
import com.example.models.NotificationResponse
import java.sql.ResultSet
import java.util.UUID

class NotificationRepository {

    fun create(userId: UUID, title: String, body: String, type: String, referenceId: UUID?): NotificationResponse =
        Db.withConnection { c ->
            c.querySingle(
                """
                INSERT INTO user_notifications (user_id, title, body, notification_type, reference_id)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, title, body, notification_type, reference_id, is_read, created_at
                """.trimIndent(),
                userId, title.take(150), body, type, referenceId
            ) { it.toNotification() }!!
        }

    fun list(userId: UUID, limit: Int = 50): List<NotificationResponse> = Db.withConnection { c ->
        c.queryList(
            """
            SELECT id, title, body, notification_type, reference_id, is_read, created_at
            FROM user_notifications
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent(),
            userId, limit
        ) { it.toNotification() }
    }

    fun markRead(userId: UUID, notificationId: UUID): Boolean = Db.withConnection { c ->
        c.execute("UPDATE user_notifications SET is_read = TRUE WHERE id = ? AND user_id = ?", notificationId, userId) > 0
    }

    fun markAllRead(userId: UUID) {
        Db.withConnection { c -> c.execute("UPDATE user_notifications SET is_read = TRUE WHERE user_id = ?", userId) }
    }

    private fun ResultSet.toNotification() = NotificationResponse(
        id = uuid("id").toString(),
        title = getString("title"),
        body = getString("body"),
        type = getString("notification_type"),
        referenceId = uuidOrNull("reference_id")?.toString(),
        isRead = getBoolean("is_read"),
        createdAt = instant("created_at")
    )
}
