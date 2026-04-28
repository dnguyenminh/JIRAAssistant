package com.assistant.server.routes

import com.assistant.auth.AuthResult
import com.assistant.auth.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Simplified login request — no Jira credentials needed.
 * Jira credentials are managed via the Integrations page and stored in DB.
 * Login only creates a JWT session for the user.
 */
@Serializable
data class LoginRequest(
    val email: String = "",
    val password: String = ""
)

@Serializable
data class LoginSuccessResponse(
    val jwt: String,
    val user: UserResponse,
    val projects: List<com.assistant.jira.JiraProject> = emptyList()
)

@Serializable
data class UserResponse(
    val userId: String,
    val email: String,
    val role: String,
    val projectKey: String,
    val jiraDomain: String
)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class MessageResponse(val message: String)

fun Routing.authRoutes() {
    val authService by inject<AuthService>()

    route("/api/auth") {
        /**
         * POST /api/auth/login
         *
         * Simplified login: creates a JWT session for the user.
         * Jira credentials are no longer passed here — they are configured
         * via the Integrations page and stored in the database.
         *
         * Accepts optional email/password. If omitted, performs auto-login
         * using Jira config from DB (if available).
         */
        post("/login") {
            val request = call.receive<LoginRequest>()

            when (val result = authService.authenticate(request.email, request.password)) {
                is AuthResult.Success -> {
                    call.respond(HttpStatusCode.OK, LoginSuccessResponse(
                        jwt = result.jwt,
                        user = UserResponse(
                            userId = result.user.userId,
                            email = result.user.email,
                            role = result.user.role.name,
                            projectKey = result.user.projectKey,
                            jiraDomain = result.user.jiraDomain
                        ),
                        projects = result.projects
                    ))
                }
                is AuthResult.Failure -> {
                    val status = when (result.code) {
                        401 -> HttpStatusCode.Unauthorized
                        403 -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respond(status, ErrorResponse(result.message))
                }
            }
        }

        authenticate("auth-jwt") {
            post("/logout") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("user_id")?.asString()

                if (userId != null) {
                    authService.invalidateSession(userId)
                    call.respond(HttpStatusCode.OK, MessageResponse("Logged out successfully"))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid token"))
                }
            }
        }
    }
}
