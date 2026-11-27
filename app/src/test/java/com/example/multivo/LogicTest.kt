package com.example.multivo

import com.example.multivo.data.Languages
import com.example.multivo.ui.auth.AuthUiState
import com.example.multivo.ui.auth.AuthViewModel
import com.example.multivo.ui.components.initialsOf
import com.example.multivo.ui.components.shortTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class LogicTest {

    private val base = AuthUiState()

    @Test fun `sign in needs identifier and password`() {
        assertEquals("Enter your username or email.", AuthViewModel.validateSignIn(base.copy(password = "x")))
        assertEquals("Enter your password.", AuthViewModel.validateSignIn(base.copy(identifier = "ada")))
        assertNull(AuthViewModel.validateSignIn(base.copy(identifier = "ada", password = "secret")))
    }

    @Test fun `sign up enforces the same rules as the server`() {
        val ok = base.copy(username = "ada_obi", fullName = "Ada Obi", email = "ada@x.com", password = "password1")
        assertNull(AuthViewModel.validateSignUp(ok))
        assertEquals("Username: 3-30 letters, numbers or underscores.", AuthViewModel.validateSignUp(ok.copy(username = "a!")))
        assertEquals("Password must be at least 8 characters.", AuthViewModel.validateSignUp(ok.copy(password = "short")))
        assertEquals("Enter a valid email address.", AuthViewModel.validateSignUp(ok.copy(email = "ada")))
    }

    @Test fun `sign in no longer asks for a server address`() {
        // Any username + password is enough: there is no server field to fill in.
        assertNull(AuthViewModel.validateSignIn(AuthUiState(identifier = "ada", password = "secret")))
    }

    @Test fun `new accounts translate into the phone language`() {
        assertEquals("yo", Languages.fromPhone("yo"))
        assertEquals("fr", Languages.fromPhone("fr-CA"))
        // Languages Multivo can't translate into fall back to English.
        assertEquals("en", Languages.fromPhone("xx"))
        assertEquals("en", Languages.fromPhone(null))
    }

    @Test fun `initials and language names`() {
        assertEquals("AO", initialsOf("Ada  Obi Okafor"))
        assertEquals("?", initialsOf("   "))
        assertEquals("Yoruba", Languages.nameOf("yo"))
        assertEquals("French", Languages.nameOf("fr-CA"))
    }

    @Test fun `short time shows clock today and date otherwise`() {
        val now = Instant.parse("2026-11-18T12:00:00Z")
        assertEquals("09:30", shortTime("2026-11-18T09:30:00Z", ZoneOffset.UTC, now))
        assertEquals("Yesterday", shortTime("2026-11-17T20:00:00Z", ZoneOffset.UTC, now))
        assertEquals("3 Nov", shortTime("2026-11-03T08:00:00Z", ZoneOffset.UTC, now))
    }
}
