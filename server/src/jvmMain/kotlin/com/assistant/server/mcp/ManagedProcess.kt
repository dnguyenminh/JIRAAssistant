package com.assistant.server.mcp

import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpToolInfo
import kotlinx.coroutines.Job

/**
 * Runtime state of a single managed MCP server process.
 * Requirements: 6.31, 6.32
 */
internal data class ManagedProcess(
    val process: Process? = null,
    val client: McpProtocolClient,
    val readerJob: Job? = null,
    val healthJob: Job,
    val tools: List<McpToolInfo>,
    val configName: String,
    val startedAt: Long,
    var restartCount: Int = 0
)
