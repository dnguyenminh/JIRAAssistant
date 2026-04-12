package com.assistant.frontend.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Frontend models for MCP runtime status, tools, and tool calls.
 * Requirements: 6.57, 6.44, 6.48, 6.50
 */

@Serializable
data class McpProcessStatusDto(
    val configId: String = "",
    val pid: Long? = null,
    val state: String = "OFFLINE",
    val uptime: Long = 0,
    val toolCount: Int = 0,
    val lastError: String? = null,
    val restartCount: Int = 0
)

@Serializable
data class McpToolInfoDto(
    val name: String = "",
    val description: String = "",
    val inputSchema: JsonElement? = null
)

@Serializable
data class McpToolCallRequestDto(
    val serverId: String,
    val toolName: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
    val approved: Boolean = false
)

@Serializable
data class McpToolCallResponseDto(
    val content: List<McpContentDto> = emptyList(),
    val isError: Boolean = false,
    val requiresApproval: Boolean = false,
    val toolName: String? = null,
    val arguments: JsonObject? = null
)

@Serializable
data class McpContentDto(
    val type: String = "text",
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)
