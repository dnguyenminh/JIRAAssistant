package com.assistant.server.chat

import com.assistant.ai.models.OllamaChatToolCall
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.server.mcp.internal.InternalMcpBridge
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Executes native tool calls by routing to the correct MCP server.
 * Tool names use "serverId__toolName" convention from OllamaToolConverter.
 * Requirements: 6.50, 19.67, 19.69
 */
private val logger = LoggerFactory.getLogger("NativeToolCallExecutor")

/**
 * Execute a single native tool call, routing by serverId prefix.
 */
internal suspend fun executeNativeToolCall(
    call: OllamaChatToolCall,
    pm: McpProcessManager?,
    localKB: LocalKBToolExecutor?,
    bridge: InternalMcpBridge?,
    userId: String?,
    permService: UserToolPermissionService?
): String {
    val (serverId, toolName) = parseToolName(call.function.name)
    val arguments = call.function.arguments
    logger.info("[NativeExec] Routing: serverId=$serverId, tool=$toolName")

    // Per-user permission check
    if (isDisabledByUser(userId, permService, serverId, toolName)) {
        return "Tool '$toolName' is disabled by user"
    }

    return when (serverId) {
        LocalKBToolExecutor.SERVER_ID -> executeLocalKB(localKB, toolName, arguments)
        InternalMcpBridge.INTERNAL_SERVER_ID -> executeInternal(bridge, toolName, arguments, userId)
        else -> executeExternal(pm, serverId, toolName, arguments)
    }
}

/** Parse "serverId__toolName" back to (serverId, toolName). */
internal fun parseToolName(combined: String): Pair<String, String> {
    val idx = combined.indexOf("__")
    return if (idx > 0) {
        combined.substring(0, idx) to combined.substring(idx + 2)
    } else {
        "unknown" to combined
    }
}

private suspend fun isDisabledByUser(
    userId: String?, permService: UserToolPermissionService?,
    serverId: String, toolName: String
): Boolean {
    if (permService == null || userId == null) return false
    return !permService.isEnabled(userId, serverId, toolName)
}

private suspend fun executeLocalKB(
    executor: LocalKBToolExecutor?, toolName: String, args: JsonObject
): String {
    if (executor == null) return "Error: local KB executor unavailable"
    return try {
        val stringArgs = args.mapValues { (_, v) ->
            v.jsonPrimitive.contentOrNull ?: v.toString()
        }
        executor.execute(toolName, stringArgs)
    } catch (e: Exception) {
        logger.warn("Local KB tool error: {}", e.message)
        "Tool error: ${e.message}"
    }
}

private suspend fun executeInternal(
    bridge: InternalMcpBridge?, toolName: String,
    args: JsonObject, userId: String?
): String {
    if (bridge == null) return "Error: no internal MCP bridge"
    return try {
        val resp = bridge.callTool(toolName, args, userId ?: "system", "READER")
        if (resp.isError) "Tool error: ${resp.content.firstOrNull()?.text ?: "unknown"}"
        else resp.content.mapNotNull { it.text }.joinToString("\n")
    } catch (e: Exception) {
        logger.warn("Internal tool error: {}", e.message)
        "Tool error: ${e.message}"
    }
}

private suspend fun executeExternal(
    pm: McpProcessManager?, serverId: String,
    toolName: String, args: JsonObject
): String {
    if (pm == null) return "Error: no MCP process manager"
    return try {
        val client = pm.getClient(serverId)
            ?: return "Error: server '$serverId' not running"
        val resp = client.callTool(toolName, args)
        if (resp.isError) "Tool error: ${resp.content.firstOrNull()?.text ?: "unknown"}"
        else resp.content.mapNotNull { it.text }.joinToString("\n")
    } catch (e: Exception) {
        logger.warn("External tool error: {}", e.message)
        "Tool error: ${e.message}"
    }
}
