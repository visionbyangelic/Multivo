package com.example.multivo.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.multivo.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "multivo_session")

data class Session(val token: String, val user: UserProfile)

/** Saves the sign-in token, and the signed-in user on the phone. */
class SessionStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): Session? {
        val prefs = context.dataStore.data.first()
        val token = prefs[tokenKey] ?: return null
        val user = prefs[userKey]?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() }
            ?: return null
        return Session(token, user)
    }

    suspend fun save(session: Session) {
        context.dataStore.edit {
            it[tokenKey] = session.token
            it[userKey] = json.encodeToString(UserProfile.serializer(), session.user)
        }
    }

    suspend fun clear() {
        context.dataStore.edit {
            it.remove(tokenKey)
            it.remove(userKey)
        }
    }

    /**
     * The server is built into the app (DEFAULT_SERVER_URL in app/build.gradle.kts);
     * people never type it. An address saved by older versions is ignored.
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun serverUrl(): String = BuildConfig.DEFAULT_SERVER_URL.trim().trimEnd('/')
}
