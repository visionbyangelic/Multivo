package com.example.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.config.AppConfig
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

class JwtConfig(private val config: AppConfig) {

    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val realm = "Multivo API"

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .build()

    fun generateToken(userId: UUID): String = JWT.create()
        .withSubject(userId.toString())
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .withIssuedAt(Date())
        .withExpiresAt(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(config.jwtValidityDays)))
        .sign(algorithm)

    /** Returns the user id inside a valid token, or null. */
    fun userIdFrom(token: String): UUID? = runCatching {
        UUID.fromString(verifier.verify(token).subject)
    }.getOrNull()
}
