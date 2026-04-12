package com.assistant.mcp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Tool call request routed to an MCP server.
 * Requirements: 6.48, 6.50
 */
@Serializable
data class McpToolCallRequest(
    val serverId: String,
    val toolName: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
    val approved: Boolean = false
)

/**
 * Tool call response from MCP server.
 * Requirements: 6.48, 6.50
 */
@Serializable
data class McpToolCallResponse(
    val content: List<McpContent> = emptyList(),
    val isError: Boolean = false,
    val requiresApproval: Boolean = false,
    val toolName: String? = null,
    val arguments: JsonObject? = null
)

/**
 * Content block in MCP tool response.
 */
@Serializable
data class McpContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)
