package com.assistant.server.routes

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.mcp.models.McpToolCallResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

/**
 * Handlers for MCP tool discovery and execution endpoints.
 * Requirements: 6.45, 6.46, 6.48, 6.50
 */

private val json = Json { ignoreUnknownKeys = true }

/** GET /{id}/tools — Reader+. Req: 6.45 */
internal suspend fun RoutingContext.handleServerTools(
    processManager: McpProcessManager
) {
    extractUserClaims() ?: return
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    val client = processManager.getClient(id)
    if (client == null) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse("Server not running"))
        return
    }
    val tools = client.listTools()
    call.respond(HttpStatusCode.OK, tools)
}

/** GET /tools — Reader+. Aggregated tools from all servers. Req: 6.46 */
internal suspend fun RoutingContext.handleAggregatedTools(
    processManager: McpProcessManager
) {
    extractUserClaims() ?: return
    val tools = processManager.getActiveTools()
    call.respond(HttpStatusCode.OK, tools)
}

/** POST /tools/call — Administrator. Req: 6.48, 6.50 */
internal suspend fun RoutingContext.handleToolCall(
    processManager: McpProcessManager
) {
    val (_, role) = extractUserClaims() ?: return
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val request = call.receive<McpToolCallRequest>()
    val needsApproval = checkApprovalNeeded(request, processManager)
    if (needsApproval) {
        val pending = McpToolCallResponse(
            requiresApproval = true,
            toolName = request.toolName,
            arguments = request.arguments
        )
        call.respond(HttpStatusCode.OK, pending)
        return
    }
    executeToolCall(request, processManager)
}

/** Check if tool requires approval. */
private suspend fun RoutingContext.checkApprovalNeeded(
    request: McpToolCallRequest,
    processManager: McpProcessManager
): Boolean {
    if (request.approved) return false
    val mcpRepo by call.application.inject<McpServerRepository>()
    val config = mcpRepo.findById(request.serverId) ?: return true
    val autoApproveList = parseAutoApprove(config.autoApprove)
    return request.toolName !in autoApproveList
}

/** Execute tool call via protocol client. */
private suspend fun RoutingContext.executeToolCall(
    request: McpToolCallRequest,
    processManager: McpProcessManager
) {
    val client = processManager.getClient(request.serverId)
    if (client == null) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse("Server not running"))
        return
    }
    try {
        val result = client.callTool(request.toolName, request.arguments)
        call.respond(HttpStatusCode.OK, result)
    } catch (e: Exception) {
        val errorResp = McpToolCallResponse(isError = true)
        call.respond(HttpStatusCode.OK, errorResp)
    }
}

private fun parseAutoApprove(raw: String): List<String> = try {
    json.decodeFromString<List<String>>(raw)
} catch (_: Exception) {
    emptyList()
}
