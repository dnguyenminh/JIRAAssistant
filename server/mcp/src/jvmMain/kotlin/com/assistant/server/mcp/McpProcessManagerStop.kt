package com.assistant.server.mcp

import com.assistant.mcp.models.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Stop/shutdown operations for McpProcessManagerImpl.
 * Requirements: 6.33, 6.36
 */

/** Graceful shutdown: SIGTERM → 5s wait → SIGKILL. Req: 6.36 */
internal suspend fun McpProcessManagerImpl.stopServerInternal(
    configId: String
): McpProcessStatus {
    val mp = processes.remove(configId)
        ?: return McpProcessStatus(configId, state = McpServerState.STOPPED)
    cancelJobs(mp)
    terminateProcess(mp.process)
    mp.client.close()
    updateStatusSafe(configId, "OFFLINE")
    logger.info("Stopped MCP server '${mp.configName}'")
    return McpProcessStatus(configId, state = McpServerState.STOPPED)
}

/** Cancel health and reader coroutine jobs. */
private fun cancelJobs(mp: ManagedProcess) {
    mp.healthJob.cancel()
    mp.readerJob?.cancel()
}

/** SIGTERM then force kill if still alive after 5s. */
private suspend fun terminateProcess(process: Process?) {
    if (process == null) return
    withContext(Dispatchers.IO) {
        process.destroy()
        val exited = process.waitFor(5, TimeUnit.SECONDS)
        if (!exited) process.destroyForcibly()
    }
}

/** Start all enabled servers with 30s timeout each. Req: 6.33 */
internal suspend fun McpProcessManagerImpl.startAllEnabledInternal() {
    val configs = mcpRepo.getAll().filter { !it.disabled }
    logger.info("Starting ${configs.size} enabled MCP servers")
    configs.map { config ->
        scope.launch {
            try {
                withTimeout(30_000) { startServer(config.id) }
                logger.info("Auto-started MCP server '${config.name}'")
            } catch (e: Exception) {
                logger.error("Failed to auto-start '${config.name}': ${e.message}")
            }
        }
    }.forEach { it.join() }
}

/** Stop all running servers. */
internal suspend fun McpProcessManagerImpl.stopAllInternal() {
    val ids = processes.keys.toList()
    logger.info("Stopping ${ids.size} MCP servers")
    ids.forEach { stopServerInternal(it) }
}

/** Update DB status, swallowing exceptions. */
internal suspend fun McpProcessManagerImpl.updateStatusSafe(
    configId: String,
    status: String
) {
    try {
        mcpRepo.updateStatus(configId, status)
    } catch (e: Exception) {
        logger.warn("Failed to update status for $configId: ${e.message}")
    }
}
