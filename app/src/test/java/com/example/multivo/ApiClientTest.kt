package com.example.multivo

import com.example.multivo.data.ApiException
import com.example.multivo.data.MultivoApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ApiClientTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun `sends the token and parses conversations`() = runTest {
        var seenAuth: String? = null
        var seenUrl: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            seenUrl = request.url.toString()
            respond(
                """[{"id":"c1","title":"Amélie","isGroup":false,"createdAt":"2026-11-18T10:00:00Z",
                   "updatedAt":"2026-11-18T10:00:00Z","unreadCount":3,"extraFieldFromNewerServer":true}]""",
                HttpStatusCode.OK, json
            )
        }
        val api = MultivoApi(serverUrl = { "http://server:8080/" }, token = { "abc" }, engine = engine)

        val list = api.conversations()

        assertEquals("Bearer abc", seenAuth)
        assertEquals("http://server:8080/conversations", seenUrl)
        assertEquals(3, list.single().unreadCount)
    }

    @Test fun `server error messages reach the screen`() = runTest {
        val engine = MockEngine { respond("""{"error":"Wrong username/email or password."}""", HttpStatusCode.Unauthorized, json) }
        val api = MultivoApi(serverUrl = { "http://server:8080" }, token = { null }, engine = engine)
        try {
            api.login(com.example.multivo.data.LoginRequest("ada", "nope"))
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals("Wrong username/email or password.", e.message)
            assertEquals(401, e.status)
        }
    }

    @Test fun `websocket url uses ws scheme and token`() = runTest {
        val api = MultivoApi(serverUrl = { "https://chat.example.com" }, token = { "t k" }, engine = MockEngine { respond("") })
        assertEquals("wss://chat.example.com/ws?token=t%20k", api.webSocketUrl())
    }
}
