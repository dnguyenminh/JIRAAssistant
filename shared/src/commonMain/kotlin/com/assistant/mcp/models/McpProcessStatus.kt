package com.assistant.mcp.models

import kotlinx.serialization.Serializable

/**
 * Runtime status of a managed MCP server process.
 * Requirements: 6.32, 6.57
 */
@Serializable
data class McpProcessStatus(
    val configId: String,
    val pid: Long? = null,
    val state: McpServerState,
    val uptime: Long = 0,
    val toolCount: Int = 0,
    val lastError: String? = null,
    val restartCount: Int = 0
)

/**
 * MCP server process lifecycle states.
 * State machine: STOPPED→STARTING→RUNNING→STOPPED,
 * STARTING→ERROR, RUNNING→ERROR, ERROR→STARTING (retry),
 * ERROR→OFFLINE (max retries), OFFLINE→STARTING (manual).
 */
@Serializable
enum class McpServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
    OFFLINE
}
