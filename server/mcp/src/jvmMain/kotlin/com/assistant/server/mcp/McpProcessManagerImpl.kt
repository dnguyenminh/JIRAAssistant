package com.assistant.server.mcp

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages MCP server process lifecycles: start, stop, health, auto-restart.
 * Requirements: 6.31, 6.32, 6.33, 6.35, 6.36, 6.37
 */
class McpProcessManagerImpl(
    internal val mcpRepo: McpServerRepository,
    internal val scope: CoroutineScope
) : McpProcessManager {

    internal val processes = ConcurrentHashMap<String, ManagedProcess>()
    internal val maxRetries = 3
    internal val backoffMs = longArrayOf(2000, 4000, 8000)
    internal val logger = LoggerFactory.getLogger(McpProcessManagerImpl::class.java)

    /** Start an MCP server process. Req: 6.31, 6.32 */
    override suspend fun startServer(configId: String): McpProcessStatus {
        val config = mcpRepo.findById(configId)
            ?: return McpProcessStatus(configId, state = McpServerState.STOPPED, lastError = "Config not found")
        if (config.disabled) return buildStoppedStatus(configId)
        return doStartServer(configId, config)
    }

    /** Stop process gracefully. Req: 6.36 */
    override suspend fun stopServer(configId: String): McpProcessStatus {
        return stopServerInternal(configId)
    }

    /** Restart = stop + start. Req: 6.32 */
    override suspend fun restartServer(configId: String): McpProcessStatus {
        stopServer(configId)
        return startServer(configId)
    }

    /** Map of currently running servers. Req: 6.32 */
    override fun getRunningServers(): Map<String, McpProcessStatus> {
        return processes.mapValues { (id, mp) -> buildRunningStatus(id, mp) }
    }

    /** Detailed status for a single server. Req: 6.57 */
    override fun getStatus(configId: String): McpProcessStatus? {
        val mp = processes[configId] ?: return null
        return buildRunningStatus(configId, mp)
    }

    /** Start all enabled servers on app startup. Req: 6.33 */
    override suspend fun startAllEnabled() {
        startAllEnabledInternal()
    }

    /** Stop all servers (shutdown hook). */
    override suspend fun stopAll() {
        stopAllInternal()
    }

    /** Aggregated tools from all running servers. Req: 6.46 */
    override fun getActiveTools(): List<McpAggregatedTool> {
        return collectActiveTools()
    }

    /** Get protocol client for a specific server. */
    override fun getClient(configId: String): McpProtocolClient? {
        return processes[configId]?.client
    }
}
