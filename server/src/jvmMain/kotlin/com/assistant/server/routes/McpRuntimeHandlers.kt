package com.assistant.server.routes

import com.assistant.mcp.McpProcessManager
import com.assistant.server.mcp.McpLogBuffer
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Handlers for MCP runtime start/stop/status/logs endpoints.
 * Requirements: 6.37, 6.57, 6.61
 */

/** POST /{id}/start — Administrator only. Req: 6.37 */
internal suspend fun RoutingContext.handleStartServer(
    processManager: McpProcessManager
) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    val status = processManager.startServer(id)
    call.respond(HttpStatusCode.OK, status)
}

/** POST /{id}/stop — Administrator only. Req: 6.37 */
internal suspend fun RoutingContext.handleStopServer(
    processManager: McpProcessManager
) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    val status = processManager.stopServer(id)
    call.respond(HttpStatusCode.OK, status)
}

/** GET /{id}/status — Reader+. Req: 6.57 */
internal suspend fun RoutingContext.handleServerStatus(
    processManager: McpProcessManager
) {
    extractUserClaims() ?: return
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    val status = processManager.getStatus(id)
    if (status == null) {
        call.respond(HttpStatusCode.NotFound, ErrorResponse("Server not found or not running"))
        return
    }
    call.respond(HttpStatusCode.OK, status)
}

/** GET /{id}/logs — Administrator only. Req: 6.61 */
internal suspend fun RoutingContext.handleServerLogs(
    processManager: McpProcessManager
) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    val logs = McpLogBuffer.getLogs(id)
    call.respond(HttpStatusCode.OK, logs)
}
