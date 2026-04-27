package com.assistant.mcp.models

import kotlinx.serialization.Serializable

/**
 * Health check response containing readiness status of all active MCP servers.
 * Requirements: 1.2, 1.6
 */
@Serializable
data class McpHealthResponse(
    val allReady: Boolean,
    val servers: List<McpServerHealth>
)

/**
 * Readiness status of a single MCP server.
 * Requirements: 1.2, 5.1
 */
@Serializable
data class McpServerHealth(
    val configId: String,
    val serverName: String,
    val ready: Boolean,
    val toolCount: Int = 0,
    val error: String? = null,
    val role: String = "other"
)
