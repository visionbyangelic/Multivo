package com.example.repositories

import com.example.database.Db
import com.example.database.execute
import java.util.UUID

class TranslationRepository {

    fun saveDetectedSourceLanguage(messageId: UUID, language: String) {
        Db.withConnection { c ->
            c.execute(
                "UPDATE messages SET original_language = ? WHERE id = ? AND original_language IS NULL",
                language, messageId
            )
        }
    }

    fun saveTranslation(messageId: UUID, recipientId: UUID, translatedText: String, targetLanguage: String) {
        Db.withConnection { c ->
            c.execute(
                """
                INSERT INTO message_translations (message_id, recipient_id, translated_text, target_language)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (message_id, recipient_id) DO UPDATE
                    SET translated_text = EXCLUDED.translated_text,
                        target_language = EXCLUDED.target_language
                """.trimIndent(),
                messageId, recipientId, translatedText, targetLanguage
            )
        }
    }
}
