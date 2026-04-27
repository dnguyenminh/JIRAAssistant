package com.assistant.server.routes

import com.assistant.mcp.McpProcessManager
import com.assistant.server.mcp.McpLogBuffer
import com.assistant.server.mcp.internal.InternalMcpBridge
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Handlers for MCP runtime start/stop/status/logs endpoints.
 * Requirements: 6.37, 6.57, 6.61, 6.70
 */

/** Check if the given ID is the internal MCP server. Req: 6.70 */
private fun isInternalServerId(id: String): Boolean =
    id == InternalMcpBridge.INTERNAL_SERVER_ID

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

/** POST /{id}/stop — Administrator only. Req: 6.37, 6.70 */
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
    if (isInternalServerId(id)) {
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Cannot modify Internal MCP Server"))
        return
    }
    val status = processManager.stopServer(id)
    call.respond(HttpStatusCode.OK, status)
}

/** GET /{id}/status — Reader+. Req: 6.57, 6.70 */
internal suspend fun RoutingContext.handleServerStatus(
    processManager: McpProcessManager
) {
    extractUserClaims() ?: return
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    if (isInternalServerId(id)) {
        val bridge by call.application.inject<InternalMcpBridge>()
        call.respond(HttpStatusCode.OK, bridge.getStatus())
        return
    }
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
