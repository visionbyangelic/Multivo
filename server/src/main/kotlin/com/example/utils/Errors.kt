package com.example.utils

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

class UnauthorizedException(message: String = "Sign in required.") : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)

fun ApplicationCall.currentUserId(): UUID {
    val subject = principal<JWTPrincipal>()?.payload?.subject ?: throw UnauthorizedException()
    return runCatching { UUID.fromString(subject) }.getOrElse { throw UnauthorizedException() }
}

fun ApplicationCall.uuidParameter(name: String): UUID {
    val raw = parameters[name] ?: throw IllegalArgumentException("Missing $name.")
    return parseUuid(raw, name)
}

fun parseUuid(raw: String, label: String = "id"): UUID =
    runCatching { UUID.fromString(raw.trim()) }
        .getOrElse { throw IllegalArgumentException("Invalid $label.") }

private val LANGUAGE_PATTERN = Regex("^[a-z]{2,3}(-[a-zA-Z0-9]{2,8})?$")

fun normalizeLanguage(code: String): String {
    val value = code.trim()
    val normalized = value.substringBefore('-').lowercase() +
        (if (value.contains('-')) "-" + value.substringAfter('-') else "")
    require(LANGUAGE_PATTERN.matches(normalized)) { "Invalid language code." }
    return normalized
}
