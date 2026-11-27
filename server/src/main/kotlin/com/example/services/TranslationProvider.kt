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
        throw IllegalStateException("Translation is turned off (neither DEEPL_API_KEY nor GOOGLE_TRANSLATE_API_KEY is set).")
}

/** DeepL API. The free key ends in ":fx" and talks to the free endpoint
 * (api-free.deepl.com); a pro key uses api.deepl.com. The free plan needs
 * no billing account and covers 500,000 characters a month — enough for
 * this app — which is why it is the default when both keys are set. */
class DeepLTranslationProvider(private val apiKey: String) : TranslationProvider {

    override val isEnabled = true

    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint =
        if (apiKey.trimEnd().endsWith(":fx")) "https://api-free.deepl.com/v2/translate"
        else "https://api.deepl.com/v2/translate"

    /** DeepL wants upper-case codes: zh -> ZH, fr -> FR, ko -> KO, en -> EN. */
    private fun deeplCode(code: String): String = when (code.lowercase().substringBefore('-')) {
        "zh" -> "ZH"
        "en" -> "EN"
        else -> code.uppercase().substringBefore('-')
    }

    override fun translate(text: String, targetLanguage: String, sourceLanguage: String?): TranslationResult {
        require(text.isNotBlank()) { "Text cannot be empty." }

        val form = buildString {
            append("text=").append(URLEncoder.encode(text, StandardCharsets.UTF_8))
            append("&target_lang=").append(deeplCode(targetLanguage))
            if (!sourceLanguage.isNullOrBlank()) {
                append("&source_lang=").append(deeplCode(sourceLanguage))
            }
        }

        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "DeepL-Auth-Key $apiKey")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(form.toByteArray(StandardCharsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

            check(status in 200..299) {
                "DeepL API returned HTTP $status: ${responseText.take(300)}"
            }

            val translation = json.parseToJsonElement(responseText).jsonObject["translations"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject
                ?: error("Invalid translation response.")

            return TranslationResult(
                translatedText = translation["text"]?.jsonPrimitive?.content
                    ?: error("Translation text is missing."),
                detectedSourceLanguage = translation["detected_source_language"]?.jsonPrimitive?.content
            )
        } finally {
            connection.disconnect()
        }
    }
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

/** DeepL first (free, no billing account), Google second, off when neither key is set. */
fun translationProviderFor(googleApiKey: String?, deeplApiKey: String?): TranslationProvider =
    when {
        !deeplApiKey.isNullOrBlank() -> DeepLTranslationProvider(deeplApiKey)
        !googleApiKey.isNullOrBlank() -> GoogleTranslationProvider(googleApiKey)
        else -> DisabledTranslationProvider
    }
