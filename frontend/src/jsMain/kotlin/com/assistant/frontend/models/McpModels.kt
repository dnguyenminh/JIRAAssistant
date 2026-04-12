package com.assistant.frontend.models

import kotlinx.serialization.Serializable

/**
 * MCP server info for frontend display.
 * Requirements: 6.21
 */
@Serializable
data class McpServerInfo(
    val id: String = "",
    val name: String = "",
    val type: String = "stdio",
    val command: String = "",
    val url: String = "",
    val args: String = "[]",
    val env: String = "{}",
    val autoApprove: String = "[]",
    val disabled: Boolean = false,
    val status: String = "OFFLINE",
    val createdAt: String = "",
    val updatedAt: String = ""
)
