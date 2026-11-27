package com.example.security

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordHasher {
    private const val COST = 10

    fun hashPassword(password: String): String =
        BCrypt.withDefaults().hashToString(COST, password.toCharArray())

    fun verifyPassword(password: String, hash: String): Boolean =
        runCatching { BCrypt.verifyer().verify(password.toCharArray(), hash).verified }
            .getOrDefault(false)
}
