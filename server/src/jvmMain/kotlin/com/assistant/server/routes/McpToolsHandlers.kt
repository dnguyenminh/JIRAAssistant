package com.assistant.server.routes

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.internal.InternalMcpBridge
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

/**
 * Handlers for MCP tool discovery and execution endpoints.
 * Requirements: 6.45, 6.46, 6.48, 6.50, 6.71, 6.107
 */

private val json = Json { ignoreUnknownKeys = true }

/** GET /{id}/tools — Reader+. Req: 6.45, 6.71 */
internal suspend fun RoutingContext.handleServerTools(
    processManager: McpProcessManager
) {
    extractUserClaims() ?: return
    val id = call.parameters["id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing id"))
    if (id == InternalMcpBridge.INTERNAL_SERVER_ID) {
        val bridge by call.application.inject<InternalMcpBridge>()
        call.respond(HttpStatusCode.OK, bridge.getAggregatedTools())
        return
    }
    val client = processManager.getClient(id)
    if (client == null) {
        call.respond(HttpStatusCode.Conflict, ErrorResponse("Server not running"))
        return
    }
    val tools = client.listTools()
    call.respond(HttpStatusCode.OK, tools)
}

/** GET /tools — Reader+. Aggregated: internal first + external. Req: 6.46, 6.107 */
internal suspend fun RoutingContext.handleAggregatedTools(
    processManager: McpProcessManager
) {
    extractUserClaims() ?: return
    val internalBridge by call.application.inject<InternalMcpBridge>()
    val internalTools = internalBridge.getAggregatedTools()
    val externalTools = processManager.getActiveTools()
    call.respond(HttpStatusCode.OK, internalTools + externalTools)
}

/** POST /tools/call — Internal: any role (executor checks RBAC), External: Administrator. Req: 6.48, 6.50, 6.71 */
internal suspend fun RoutingContext.handleToolCall(
    processManager: McpProcessManager
) {
    val (userId, role) = extractUserClaims() ?: return
    val request = call.receive<McpToolCallRequest>()
    val internalBridge by call.application.inject<InternalMcpBridge>()
    if (internalBridge.isInternalServer(request.serverId)) {
        handleInternalToolCall(internalBridge, request, userId, role)
        return
    }
    handleExternalToolCall(request, role, processManager)
}

/** Route internal tool call — RBAC handled by executor. Req: 6.71 */
private suspend fun RoutingContext.handleInternalToolCall(
    bridge: InternalMcpBridge, request: McpToolCallRequest,
    userId: String, role: String
) {
    try {
        val result = bridge.callTool(request.toolName, request.arguments, userId, role)
        call.respond(HttpStatusCode.OK, result)
    } catch (e: Exception) {
        val errorResp = McpToolCallResponse(isError = true)
        call.respond(HttpStatusCode.OK, errorResp)
    }
}

/** Route external tool call — Administrator only + approval check. Req: 6.48, 6.50 */
private suspend fun RoutingContext.handleExternalToolCall(
    request: McpToolCallRequest, role: String,
    processManager: McpProcessManager
) {
    if (role != "ADMINISTRATOR") {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse("Administrator required"))
        return
    }
    val needsApproval = checkApprovalNeeded(request)
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
    request: McpToolCallRequest
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
