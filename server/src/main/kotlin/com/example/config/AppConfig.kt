package com.example.config

import org.slf4j.LoggerFactory

/**
 * All server settings, read from environment variables.
 *
 * | Variable                  | Default                                       |
 * |---------------------------|-----------------------------------------------|
 * | PORT                      | 8080                                          |
 * | DB_URL                    | jdbc:postgresql://localhost:5432/multivo      |
 * | DB_USER                   | postgres                                      |
 * | DB_PASSWORD               | postgres                                      |
 * | JWT_SECRET                | development secret (set a real one!)          |
 * | GOOGLE_TRANSLATE_API_KEY  | (empty -> translation turned off)             |
 * | DEEPL_API_KEY             | DeepL key, free plan (empty -> falls back   |
 * |                           | to Google, then to translation off)         |
 */
data class AppConfig(
    val port: Int = 8080,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val jwtSecret: String,
    val jwtIssuer: String = "multivo",
    val jwtAudience: String = "multivo-app",
    val jwtValidityDays: Long = 30,
    val googleTranslateApiKey: String? = null,
    val deeplApiKey: String? = null
) {
    companion object {
        private const val DEV_JWT_SECRET = "multivo-development-secret-change-me"
        private val log = LoggerFactory.getLogger(AppConfig::class.java)

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig {
            val secret = env["JWT_SECRET"]?.takeIf { it.isNotBlank() } ?: run {
                log.warn("JWT_SECRET is not set. Using a development secret - never do this in production.")
                DEV_JWT_SECRET
            }
            return AppConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                dbUrl = env["DB_URL"] ?: "jdbc:postgresql://localhost:5432/multivo",
                dbUser = env["DB_USER"] ?: "postgres",
                dbPassword = env["DB_PASSWORD"] ?: "postgres",
                jwtSecret = secret,
                googleTranslateApiKey = env["GOOGLE_TRANSLATE_API_KEY"]?.takeIf { it.isNotBlank() },
                deeplApiKey = env["DEEPL_API_KEY"]?.takeIf { it.isNotBlank() }
            )
        }
    }
}
