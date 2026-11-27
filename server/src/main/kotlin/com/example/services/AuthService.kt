package com.example.services

import com.example.models.AuthResponse
import com.example.models.RegisterRequest
import com.example.repositories.UserRepository
import com.example.security.JwtConfig
import com.example.security.PasswordHasher
import com.example.utils.ConflictException
import com.example.utils.UnauthorizedException
import com.example.utils.normalizeLanguage
import java.sql.SQLException
import java.util.UUID

class AuthService(
    private val jwt: JwtConfig,
    private val users: UserRepository = UserRepository()
) {

    fun register(request: RegisterRequest): AuthResponse {
        val username = request.username.trim()
        val fullName = request.fullName.trim()
        val email = request.email.trim().lowercase()
        val language = normalizeLanguage(request.preferredLanguage)

        require(username.matches(Regex("^[a-zA-Z0-9_]{3,30}$"))) {
            "Username must be 3-30 characters: letters, numbers or underscores."
        }
        require(fullName.length in 2..100) { "Full name must be between 2 and 100 characters." }
        require(email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            "Enter a valid email address."
        }
        require(request.password.length >= 8) { "Password must contain at least 8 characters." }

        val user = try {
            users.createUser(
                username = username,
                fullName = fullName,
                email = email,
                passwordHash = PasswordHasher.hashPassword(request.password),
                preferredLanguage = language
            )
        } catch (e: SQLException) {
            if (e.sqlState == "23505") throw ConflictException("That username or email is already registered.")
            throw e
        }

        return AuthResponse(
            message = "Registration successful.",
            token = jwt.generateToken(UUID.fromString(user.id)),
            user = user
        )
    }

    fun login(identifier: String, password: String): AuthResponse {
        require(identifier.isNotBlank()) { "Enter your email or username." }
        require(password.isNotBlank()) { "Password is required." }

        val stored = users.findByIdentifier(identifier.trim())
        if (stored == null || !PasswordHasher.verifyPassword(password, stored.passwordHash)) {
            throw UnauthorizedException("Wrong username/email or password.")
        }

        return AuthResponse(
            message = "Login successful.",
            token = jwt.generateToken(UUID.fromString(stored.profile.id)),
            user = stored.profile
        )
    }
}
