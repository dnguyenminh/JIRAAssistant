package com.assistant.server.mcp

import com.assistant.mcp.models.*

/**
 * Status building and tool aggregation helpers for McpProcessManagerImpl.
 * Requirements: 6.32, 6.46, 6.57
 */

/** Build McpProcessStatus for a running ManagedProcess. */
internal fun McpProcessManagerImpl.buildRunningStatus(
    configId: String,
    mp: ManagedProcess
): McpProcessStatus {
    val uptimeMs = System.currentTimeMillis() - mp.startedAt
    val pid = extractPid(mp.process)
    return McpProcessStatus(
        configId = configId,
        pid = pid,
        state = McpServerState.RUNNING,
        uptime = uptimeMs / 1000,
        toolCount = mp.tools.size,
        restartCount = mp.restartCount
    )
}

/** Build a STOPPED status for disabled configs. */
internal fun buildStoppedStatus(configId: String): McpProcessStatus {
    return McpProcessStatus(configId, state = McpServerState.STOPPED)
}

/** Collect aggregated tools from all running processes. Req: 6.46 */
internal fun McpProcessManagerImpl.collectActiveTools(): List<McpAggregatedTool> {
    return processes.flatMap { (configId, mp) ->
        mp.tools.map { tool ->
            McpAggregatedTool(
                serverId = configId,
                serverName = mp.configName,
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            )
        }
    }
}

/** Extract PID from Process (best-effort, JDK 9+). */
private fun extractPid(process: Process?): Long? {
    if (process == null) return null
    return try {
        process.pid()
    } catch (_: Exception) {
        null
    }
}
