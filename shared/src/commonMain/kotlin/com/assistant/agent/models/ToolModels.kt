package com.assistant.agent.models

import kotlinx.serialization.Serializable

/**
 * Result of a single tool invocation.
 * The ToolRegistry never throws — all errors are captured here.
 */
@Serializable
data class ToolResult(
    val toolName: String,
    val data: String = "",
    val executionTimeMs: Long = 0,
    val dataSizeChars: Int = 0,
    val success: Boolean = true,
    val errorType: String? = null,
    val errorMessage: String? = null
)

/**
 * A request to invoke a tool with the given parameters.
 */
@Serializable
data class ToolCall(
    val toolName: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * Record of a completed tool invocation, stored in AgentState history.
 */
@Serializable
data class ToolCallRecord(
    val toolName: String,
    val params: Map<String, String> = emptyMap(),
    val executionTimeMs: Long = 0,
    val dataSizeChars: Int = 0,
    val success: Boolean = true,
    val timestamp: String = ""
)

/**
 * Descriptor for a registered tool, returned by ToolRegistry.listTools().
 */
@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    val parameterNames: List<String> = emptyList()
)

/**
 * Enriched tool descriptor with source metadata.
 * Returned by ToolRegistry.listToolsWithSource().
 *
 * [toolSource] uses String ("LOCAL", "AGENT_MCP", "SHARED_MCP")
 * for cross-platform compatibility.
 */
@Serializable
data class ToolDescriptorWithSource(
    val name: String,
    val description: String,
    val parameterNames: List<String> = emptyList(),
    val toolSource: String = "LOCAL",
    val serverId: String? = null,
    val serverName: String? = null
)
