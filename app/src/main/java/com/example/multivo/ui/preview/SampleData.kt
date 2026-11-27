package com.example.multivo.ui.preview

import com.example.multivo.data.ChatMessage
import com.example.multivo.data.ConnectionRequest
import com.example.multivo.data.ConversationSummary
import com.example.multivo.data.Participant
import com.example.multivo.data.PublicUser
import com.example.multivo.data.UserProfile
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Fake data used by @Preview functions and screenshot tests. */
object SampleData {
    private fun ago(minutes: Long) = Instant.now().minus(minutes, ChronoUnit.MINUTES).toString()

    val me = UserProfile(
        id = "me", username = "ada", fullName = "Ada Obi", email = "ada@example.com",
        bio = "Lagos · coffee · languages", preferredLanguage = "en", isOnline = true
    )

    val amelie = PublicUser("u1", "amelie", "Amélie Laurent", null, "fr", true, "contact")
    val diego = PublicUser("u2", "diego", "Diego Ramírez", null, "es", false, "contact")
    val yuki = PublicUser("u3", "yuki", "Yuki Tanaka", null, "ja", true, "contact")
    val tunde = PublicUser("u4", "tunde", "Tunde Bakare", null, "yo", false, "none")

    val contacts = listOf(amelie, diego, yuki)
    val searchResults = listOf(tunde, amelie.copy(relationship = "contact"), PublicUser("u5", "lena", "Lena Fischer", null, "de", false, "request_sent"))

    val requests = listOf(
        ConnectionRequest("r1", PublicUser("u6", "kemi", "Kemi Adeyemi", null, "en", true), me.toPublic(), "pending", ago(30))
    )

    private fun UserProfile.toPublic() = PublicUser(id, username, fullName, null, preferredLanguage, isOnline)
    private fun PublicUser.toParticipant() = Participant(id, username, fullName, preferredLanguage, isOnline)
    private val meParticipant = Participant(me.id, me.username, me.fullName, me.preferredLanguage, true)

    val messages = listOf(
        ChatMessage("m1", "c1", "u1", "Amélie Laurent", "Salut Ada ! Tu es libre ce soir ?", "fr",
            "Hi Ada! Are you free tonight?", "en", ago(42)),
        ChatMessage("m2", "c1", "me", "Ada Obi", "Yes! Dinner at 8?", "en", null, null, ago(40)),
        ChatMessage("m3", "c1", "u1", "Amélie Laurent", "Parfait, je réserve une table près du port.", "fr",
            "Perfect, I'll book a table near the harbour.", "en", ago(38)),
        ChatMessage("m4", "c1", "me", "Ada Obi", "See you there 🙌", "en", null, null, ago(2))
    )

    val conversations = listOf(
        ConversationSummary("c1", "Amélie Laurent", false, "me", ago(600), ago(2),
            listOf(meParticipant, amelie.toParticipant()), messages.last(), 0),
        ConversationSummary("c2", "Diego Ramírez", false, "u2", ago(3000), ago(90),
            listOf(meParticipant, diego.toParticipant()),
            ChatMessage("m9", "c2", "u2", "Diego Ramírez", "¿Mañana a las diez?", "es", "Tomorrow at ten?", "en", ago(90)), 2),
        ConversationSummary("c3", "Weekend trip", true, "me", ago(5000), ago(1500),
            listOf(meParticipant, amelie.toParticipant(), yuki.toParticipant()),
            ChatMessage("m10", "c3", "u3", "Yuki Tanaka", "電車は9時です", "ja", "The train is at 9", "en", ago(1500)), 5)
    )
}
