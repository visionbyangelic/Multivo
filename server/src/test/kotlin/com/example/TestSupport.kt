package com.example

import com.example.config.AppConfig
import com.example.services.TranslationProvider
import com.example.services.TranslationResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.DriverManager
import com.example.models.AuthResponse
import com.example.models.RegisterRequest

/**
 * Tests run against a real PostgreSQL:
 *  - set TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD to use your own database, or
 *  - leave them empty and an embedded PostgreSQL is started automatically.
 * The database is wiped before every test.
 */
object TestDatabase {
    private val embedded: EmbeddedPostgres? by lazy {
        if (System.getenv("TEST_DB_URL").isNullOrBlank()) EmbeddedPostgres.start() else null
    }

    val url: String get() = System.getenv("TEST_DB_URL")?.takeIf { it.isNotBlank() }
        ?: embedded!!.getJdbcUrl("postgres", "postgres")
    val user: String get() = System.getenv("TEST_DB_USER") ?: "postgres"
    val password: String get() = System.getenv("TEST_DB_PASSWORD") ?: "postgres"

    fun reset() {
        DriverManager.getConnection(url, user, password).use { c ->
            c.createStatement().use {
                it.execute("DROP SCHEMA IF EXISTS public CASCADE")
                it.execute("CREATE SCHEMA public")
            }
        }
    }

    fun config() = AppConfig(
        dbUrl = url,
        dbUser = user,
        dbPassword = password,
        jwtSecret = "test-secret"
    )
}

/** Predictable "translation" so tests can check who received what. */
object FakeTranslator : TranslationProvider {
    override val isEnabled = true
    override fun translate(text: String, targetLanguage: String, sourceLanguage: String?) =
        TranslationResult("[$targetLanguage] $text", detectedSourceLanguage = sourceLanguage ?: "en")
}

fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) { json(AppJson) }
    install(io.ktor.client.plugins.websocket.WebSockets)
}

fun HttpRequestBuilder.json(body: Any, token: String? = null) {
    contentType(ContentType.Application.Json)
    token?.let { bearerAuth(it) }
    setBody(body)
}

suspend fun HttpClient.register(username: String, language: String): AuthResponse =
    post("/auth/register") {
        json(
            RegisterRequest(
                username = username,
                fullName = username.replaceFirstChar { it.uppercase() } + " Tester",
                email = "$username@example.com",
                password = "password123",
                preferredLanguage = language
            )
        )
    }.body()
