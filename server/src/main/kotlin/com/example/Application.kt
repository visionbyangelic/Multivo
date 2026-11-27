package com.example

import com.example.config.AppConfig
import com.example.database.DatabaseFactory
import com.example.models.ApiError
import com.example.repositories.NotificationRepository
import com.example.repositories.StoryRepository
import com.example.repositories.UserRepository
import com.example.routes.AUTH_JWT
import com.example.routes.authRoutes
import com.example.routes.contactRoutes
import com.example.routes.conversationRoutes
import com.example.routes.healthRoutes
import com.example.routes.notificationRoutes
import com.example.routes.storyRoutes
import com.example.routes.userRoutes
import com.example.routes.webSocketRoutes
import com.example.security.JwtConfig
import com.example.services.AuthService
import com.example.services.ChatService
import com.example.services.ContactService
import com.example.services.DeepLTranslationProvider
import com.example.services.GoogleTranslationProvider
import com.example.services.TranslationProvider
import com.example.services.translationProviderFor
import com.example.utils.ConflictException
import com.example.utils.ForbiddenException
import com.example.utils.NotFoundException
import com.example.utils.UnauthorizedException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

fun Application.module(
    config: AppConfig = AppConfig.fromEnvironment(),
    translator: TranslationProvider = translationProviderFor(config.googleTranslateApiKey, config.deeplApiKey)
) {
    DatabaseFactory.connect(config)
    monitor.subscribe(ApplicationStopped) { DatabaseFactory.close() }

    if (!translator.isEnabled) {
        log.warn("Neither DEEPL_API_KEY nor GOOGLE_TRANSLATE_API_KEY is set - messages will be delivered without translation.")
    } else {
        val providerName = when (translator) {
            is DeepLTranslationProvider -> "DeepL"
            is GoogleTranslationProvider -> "Google"
            else -> "unknown"
        }
        log.info("Translation provider: $providerName")
    }

    val jwt = JwtConfig(config)
    val users = UserRepository()
    val notifications = NotificationRepository()
    val authService = AuthService(jwt, users)
    val contactService = ContactService(notifications = notifications)
    val chatService = ChatService(translator, notifications = notifications)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().startsWith("/ws") }
    }

    install(ContentNegotiation) { json(AppJson) }

    // The iPhone web app lives on another host (Vercel, and GitHub Pages),
    // so browsers refuse its calls to this server unless the server names
    // those origins here. Without this, the app's "Save & check server"
    // fails in the browser with a plain "Load failed" even though the
    // server itself is up — the browser blocks the request before it
    // ever reaches us. Installed early so preflight (OPTIONS) requests
    // are answered before auth or routing can reject them.
    install(CORS) {
        allowHost("multivo-web.vercel.app", schemes = listOf("https"))
        allowHost("visionbyangelic.github.io", schemes = listOf("https"))
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = false
        maxAgeInSeconds = 3600
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 1024 * 1024
        masking = false
    }

    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("The request body is missing or badly formatted."))
        }
        exception<IllegalArgumentException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ApiError(e.message ?: "Invalid request."))
        }
        exception<UnauthorizedException> { call, e ->
            call.respond(HttpStatusCode.Unauthorized, ApiError(e.message ?: "Sign in required."))
        }
        exception<ForbiddenException> { call, e ->
            call.respond(HttpStatusCode.Forbidden, ApiError(e.message ?: "Not allowed."))
        }
        exception<NotFoundException> { call, e ->
            call.respond(HttpStatusCode.NotFound, ApiError(e.message ?: "Not found."))
        }
        exception<ConflictException> { call, e ->
            call.respond(HttpStatusCode.Conflict, ApiError(e.message ?: "Conflict."))
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled error on ${call.request.path()}", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Something went wrong on the server."))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiError("Route not found."))
        }
    }

    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = jwt.realm
            verifier(jwt.verifier)
            validate { credential ->
                if (!credential.payload.subject.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("Sign in required."))
            }
        }
    }

    routing {
        healthRoutes(translator)
        authRoutes(authService, users)
        userRoutes(users)
        contactRoutes(contactService)
        conversationRoutes(chatService)
        storyRoutes(StoryRepository())
        notificationRoutes(notifications)
        webSocketRoutes(jwt, users, chatService)
    }
}
