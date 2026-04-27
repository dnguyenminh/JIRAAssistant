package com.assistant.server.mcp.internal

/**
 * User context passed through internal MCP tool execution.
 * Requirements: AC 6.104
 */
data class UserContext(
    val userId: String,
    val userRole: String,
    val email: String? = null
)
