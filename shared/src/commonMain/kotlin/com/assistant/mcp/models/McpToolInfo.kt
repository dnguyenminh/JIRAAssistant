package com.assistant.mcp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Tool info discovered from a single MCP server.
 * Requirements: 6.44, 6.46
 */
@Serializable
data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchema: JsonElement
)

/**
 * Aggregated tool with server info for GET /mcp/tools endpoint.
 * Requirements: 6.46
 */
@Serializable
data class McpAggregatedTool(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String,
    val inputSchema: JsonElement
)
