package com.example.services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TranslationResult(
    val translatedText: String,
    val detectedSourceLanguage: String?
)

interface TranslationProvider {
    val isEnabled: Boolean
    fun translate(text: String, targetLanguage: String, sourceLanguage: String? = null): TranslationResult
}

/** Used when no API key is configured: messages are delivered untranslated. */
object DisabledTranslationProvider : TranslationProvider {
    override val isEnabled = false
    override fun translate(text: String, targetLanguage: String, sourceLanguage: String?) =
        throw IllegalStateException("Translation is turned off (GOOGLE_TRANSLATE_API_KEY is not set).")
}

/** Google Cloud Translation API (v2 / Basic). */
class GoogleTranslationProvider(private val apiKey: String) : TranslationProvider {

    override val isEnabled = true

    private val json = Json { ignoreUnknownKeys = true }

    override fun translate(text: String, targetLanguage: String, sourceLanguage: String?): TranslationResult {
        require(text.isNotBlank()) { "Text cannot be empty." }

        val url = URI(
            "https://translation.googleapis.com/language/translate/v2?key=" +
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
        ).toURL()

        val body = buildJsonObject {
            put("q", text)
            put("target", targetLanguage)
            if (!sourceLanguage.isNullOrBlank()) put("source", sourceLanguage)
            put("format", "text")
        }.toString()

        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

            check(status in 200..299) {
                "Google Translation API returned HTTP $status: ${responseText.take(300)}"
            }

            val translation = json.parseToJsonElement(responseText).jsonObject["data"]
                ?.jsonObject?.get("translations")
                ?.jsonArray?.firstOrNull()
                ?.jsonObject
                ?: error("Invalid translation response.")

            return TranslationResult(
                translatedText = translation["translatedText"]?.jsonPrimitive?.content
                    ?: error("Translation text is missing."),
                detectedSourceLanguage = translation["detectedSourceLanguage"]?.jsonPrimitive?.content
            )
        } finally {
            connection.disconnect()
        }
    }
}

fun translationProviderFor(apiKey: String?): TranslationProvider =
    if (apiKey.isNullOrBlank()) DisabledTranslationProvider else GoogleTranslationProvider(apiKey)
