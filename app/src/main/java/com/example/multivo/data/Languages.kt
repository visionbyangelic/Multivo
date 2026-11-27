package com.example.multivo.data

/** Same list the server exposes at GET /languages (kept locally so sign-up works offline). */
object Languages {
    val all = listOf(
        Language("en", "English"),
        Language("fr", "French"),
        Language("es", "Spanish"),
        Language("pt", "Portuguese"),
        Language("de", "German"),
        Language("it", "Italian"),
        Language("nl", "Dutch"),
        Language("ru", "Russian"),
        Language("ar", "Arabic"),
        Language("tr", "Turkish"),
        Language("hi", "Hindi"),
        Language("zh", "Chinese"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
        Language("sw", "Swahili"),
        Language("yo", "Yoruba"),
        Language("ig", "Igbo"),
        Language("ha", "Hausa")
    )

    fun nameOf(code: String?): String =
        all.firstOrNull { it.code.equals(code?.substringBefore('-'), ignoreCase = true) }?.name
            ?: code?.uppercase().orEmpty()

    /**
     * The phone's language if Multivo can translate into it, otherwise English.
     * Used so new accounts get auto-translation in the right language without picking one.
     */
    fun fromPhone(code: String?): String =
        all.firstOrNull { it.code.equals(code?.substringBefore('-'), ignoreCase = true) }?.code ?: "en"
}
