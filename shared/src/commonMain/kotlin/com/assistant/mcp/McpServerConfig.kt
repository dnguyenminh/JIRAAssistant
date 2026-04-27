package com.assistant.mcp

import kotlinx.serialization.Serializable

/**
 * MCP server configuration stored in DB.
 * Requirements: 6.25
 */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val type: String = "stdio",
    val command: String = "",
    val url: String = "",
    val args: String = "[]",
    val env: String = "{}",
    val autoApprove: String = "[]",
    val disabled: Boolean = false,
    val status: String = "OFFLINE",
    val createdAt: String = "",
    val updatedAt: String = "",
    val internal: Boolean = false
)

/**
 * MCP JSON import/export format (mcp.json compatible).
 */
@Serializable
data class McpConfigExport(
    val mcpServers: Map<String, McpServerEntry> = emptyMap()
)

@Serializable
data class McpServerEntry(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val disabled: Boolean = false,
    val autoApprove: List<String> = emptyList()
)
