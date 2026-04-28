package com.assistant.server.routes

import com.assistant.rbac.Permission
import com.assistant.server.mcp.McpHealthChecker
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("McpHealthRoutes")

/**
 * MCP health check route — GET /api/mcp/health.
 * Returns readiness status of all active MCP servers.
 * Requirements: 1.1, 1.7, 1.8
 */
fun Routing.mcpHealthRoutes() {
    val checker by inject<McpHealthChecker>()

    route("/api/mcp") {
        withPermission(Permission.ANALYZE_AI) {
            get("/health") {
                try {
                    val response = checker.checkAll()
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    logger.error("Health check failed", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(e.message ?: "Health check failed")
                    )
                }
            }
        }
    }
}
