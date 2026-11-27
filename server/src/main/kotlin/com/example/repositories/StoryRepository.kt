package com.example.repositories

import com.example.database.Db
import com.example.database.execute
import com.example.database.instant
import com.example.database.queryList
import com.example.database.querySingle
import com.example.database.uuid
import com.example.models.Story
import java.sql.ResultSet
import java.util.UUID

/** Stories (Moments): a photo and/or caption in your language, gone after 24h. */
class StoryRepository {

    fun create(userId: UUID, caption: String?, imageData: String?, language: String?): Story? =
        Db.withConnection { c ->
            c.querySingle(
                """
                INSERT INTO stories (user_id, caption, image_data, caption_language)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """.trimIndent(),
                userId, caption, imageData, language
            ) { it.uuid("id") }?.let { id -> get(id) }
        }

    /** The feed: my stories and the stories of everyone I chat with, newest first. */
    fun feedFor(userId: UUID): List<Story> = Db.withConnection { c ->
        c.queryList(
            """
            $storySelect
            WHERE s.expires_at > CURRENT_TIMESTAMP
              AND s.user_id IN (
                  SELECT DISTINCT p2.user_id
                    FROM conversation_participants p1
                    JOIN conversation_participants p2
                      ON p2.conversation_id = p1.conversation_id
                   WHERE p1.user_id = ?
              )
            ORDER BY s.created_at DESC
            """.trimIndent(),
            userId
        ) { it.toStory() }
    }

    fun delete(userId: UUID, storyId: UUID): Boolean = Db.withConnection { c ->
        c.execute("DELETE FROM stories WHERE id = ? AND user_id = ?", storyId, userId) > 0
    }

    private fun get(storyId: UUID): Story? = Db.withConnection { c ->
        c.querySingle("$storySelect WHERE s.id = ?", storyId) { it.toStory() }
    }

    private val storySelect = """
        SELECT s.id, s.user_id, u.full_name, u.profile_picture,
               s.caption, s.image_data, s.caption_language, s.created_at, s.expires_at
        FROM stories s
        JOIN app_users u ON u.id = s.user_id
    """.trimIndent()

    private fun ResultSet.toStory() = Story(
        id = uuid("id").toString(),
        userId = uuid("user_id").toString(),
        fullName = getString("full_name"),
        profilePicture = getString("profile_picture"),
        caption = getString("caption"),
        imageData = getString("image_data"),
        captionLanguage = getString("caption_language"),
        createdAt = instant("created_at"),
        expiresAt = instant("expires_at")
    )
}
