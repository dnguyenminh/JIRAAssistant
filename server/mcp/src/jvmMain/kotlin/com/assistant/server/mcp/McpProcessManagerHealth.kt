package com.assistant.server.mcp

import com.assistant.mcp.models.*
import kotlinx.coroutines.*

/**
 * Health monitoring and auto-restart for McpProcessManagerImpl.
 * Requirements: 6.35
 */

/** Launch coroutine that monitors process exit and triggers auto-restart. */
internal fun McpProcessManagerImpl.launchHealthMonitor(configId: String): Job {
    return scope.launch(Dispatchers.IO) {
        try {
            val mp = processes[configId] ?: return@launch
            val process = mp.process
            if (process == null) {
                // HTTP server — no process to monitor, just keep alive
                while (isActive) { delay(30_000) }
                return@launch
            }
            process.waitFor()
            handleProcessExit(configId)
        } catch (_: CancellationException) {
            // Normal cancellation on stop
        } catch (e: Exception) {
            logger.warn("Health monitor error for $configId: ${e.message}")
        }
    }
}

/** When process exits unexpectedly, attempt auto-restart or go OFFLINE. */
private suspend fun McpProcessManagerImpl.handleProcessExit(configId: String) {
    val mp = processes[configId] ?: return
    val currentRetries = mp.restartCount
    if (currentRetries < maxRetries) {
        attemptRestart(configId, mp, currentRetries)
    } else {
        markOffline(configId, mp)
    }
}

/** Delay with backoff, increment restart count, re-start server. */
private suspend fun McpProcessManagerImpl.attemptRestart(
    configId: String,
    mp: ManagedProcess,
    retryIndex: Int
) {
    val delayMs = backoffMs[retryIndex]
    logger.warn("MCP server '${mp.configName}' exited, retry ${retryIndex + 1}/$maxRetries in ${delayMs}ms")
    mp.restartCount = retryIndex + 1
    processes[configId] = mp
    delay(delayMs)
    try {
        startServer(configId)
    } catch (e: Exception) {
        logger.error("Auto-restart failed for '${mp.configName}': ${e.message}")
    }
}

/** Max retries exceeded — mark OFFLINE. */
private suspend fun McpProcessManagerImpl.markOffline(
    configId: String,
    mp: ManagedProcess
) {
    logger.error("MCP server '${mp.configName}' exceeded $maxRetries retries, marking OFFLINE")
    processes.remove(configId)
    updateStatusSafe(configId, "OFFLINE")
}
