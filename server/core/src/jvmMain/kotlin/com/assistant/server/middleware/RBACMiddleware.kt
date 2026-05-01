package com.assistant.server.middleware

import com.assistant.auth.UserRole
import com.assistant.rbac.Permission
import com.assistant.rbac.RBACEngine
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Ktor route wrapper that enforces JWT authentication AND checks RBAC permissions.
 *
 * Uses authenticate("auth-jwt") and a phase interceptor that runs AFTER
 * the authentication phase so the JWTPrincipal is available.
 */
fun Route.withPermission(permission: Permission, build: Route.() -> Unit): Route {
    authenticate("auth-jwt") {
        // Use ApplicationCallPipeline.Call phase — runs after auth is resolved
        intercept(ApplicationCallPipeline.Call) {
            val rbacEngine by call.application.inject<RBACEngine>()
            val principal = call.principal<JWTPrincipal>()
            val roleStr = principal?.payload?.getClaim("role")?.asString()

            if (roleStr == null) {
                call.respondText(
                    """{"error":"Unauthorized"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized
                )
                finish()
                return@intercept
            }

            val role = try {
                UserRole.valueOf(roleStr)
            } catch (_: IllegalArgumentException) {
                call.respondText(
                    """{"error":"Invalid role"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Unauthorized
                )
                finish()
                return@intercept
            }

            if (!rbacEngine.hasPermission(role, permission)) {
                call.respondText(
                    """{"error":"Forbidden"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.Forbidden
                )
                finish()
                return@intercept
            }
        }
        build()
    }
    return this
}
