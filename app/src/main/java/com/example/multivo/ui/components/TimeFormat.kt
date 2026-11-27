package com.example.multivo.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("d MMM")

/** "14:05" for today, "Yesterday", or "3 Nov". */
fun shortTime(iso: String?, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val date = instant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(date, today)) {
        0L -> instant.atZone(zone).format(timeFormat)
        1L -> "Yesterday"
        else -> instant.atZone(zone).format(dayFormat)
    }
}

fun clockTime(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { Instant.parse(iso).atZone(zone).format(timeFormat) }.getOrDefault("")
