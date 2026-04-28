package com.assistant.server.mcp

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.McpHealthResponse
import com.assistant.mcp.models.McpServerHealth
import com.assistant.server.mcp.internal.InternalMcpBridge
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Checks readiness of all active MCP servers by pinging them concurrently.
 * Requirements: 1.1, 1.5, 1.6, 4.2
 */
class McpHealthChecker(
    private val processManager: McpProcessManager,
    private val internalMcpBridge: InternalMcpBridge,
    private val mcpRepo: McpServerRepository
) {
    private val logger = LoggerFactory.getLogger(McpHealthChecker::class.java)

    /** Check all active MCP servers concurrently. Req: 1.1, 1.6, 4.2 */
    suspend fun checkAll(): McpHealthResponse {
        val configs = mcpRepo.getAll().filter { !it.disabled && it.type != "marker" }
        val results = coroutineScope {
            configs.map { config ->
                async {
                    if (internalMcpBridge.isInternalServer(config.id)) {
                        buildInternalHealth(config)
                    } else {
                        pingServer(config)
                    }
                }
            }.map { it.await() }
        }
        return McpHealthResponse(allReady = results.all { it.ready }, servers = results)
    }

    /** Ping an external server with 3-second timeout. Req: 1.3, 1.4, 4.3 */
    private suspend fun pingServer(config: McpServerConfig): McpServerHealth {
        val client = processManager.getClient(config.id)
            ?: return McpServerHealth(
                configId = config.id, serverName = config.name,
                ready = false, error = "Server not running", role = classifyRole(config)
            )
        return try {
            val tools = withTimeout(3000) { client.listTools() }
            McpServerHealth(
                configId = config.id, serverName = config.name,
                ready = true, toolCount = tools.size, role = classifyRole(config)
            )
        } catch (_: TimeoutCancellationException) {
            McpServerHealth(
                configId = config.id, serverName = config.name,
                ready = false, error = "Connection timeout (3s)", role = classifyRole(config)
            )
        } catch (e: Exception) {
            logger.warn("Ping failed for '{}': {}", config.name, e.message)
            McpServerHealth(
                configId = config.id, serverName = config.name,
                ready = false, error = e.message, role = classifyRole(config)
            )
        }
    }

    /** Build health entry for internal server (always ready). Req: 1.5 */
    private fun buildInternalHealth(config: McpServerConfig): McpServerHealth {
        return McpServerHealth(
            configId = config.id, serverName = config.name,
            ready = true, role = "jira_internal",
            toolCount = internalMcpBridge.getAggregatedTools().size
        )
    }

    /** Classify server role by name/ID patterns (case-insensitive). Req: 5.1 */
    internal fun classifyRole(config: McpServerConfig): String {
        if (config.id == InternalMcpBridge.INTERNAL_SERVER_ID) return "jira_internal"
        val name = config.name.lowercase()
        return when {
            name.contains("knowledge") || name.contains("kb") -> "knowledge_base"
            name.contains("database") || name.contains("db") -> "database"
            name.contains("markitdown") -> "markitdown"
            else -> "other"
        }
    }
}
