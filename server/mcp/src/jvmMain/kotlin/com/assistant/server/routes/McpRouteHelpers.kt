package com.assistant.server.routes

import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Extract userId and role from JWT claims. Returns null and responds 401 if missing.
 * Local copy for the MCP module — mirrors the aggregator's version.
 */
internal suspend fun RoutingContext.extractUserClaims(): Pair<String, String>? {
    val principal = call.principal<JWTPrincipal>()
    val userId = principal?.payload?.getClaim("user_id")?.asString()
    val role = principal?.payload?.getClaim("role")?.asString()
    if (userId == null || role == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
        return null
    }
    return userId to role
}
