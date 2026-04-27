package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeDirectory
import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import com.assistant.mcp.McpProtocolClient
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Manages MCP server lifecycle for an agent's home directory.
 *
 * At agent initialization, reads MCP configs from [AgentHomeDirectory],
 * starts configured servers, and auto-registers their tools in the
 * [ToolRegistry] with a `mcp_{serverName}_{toolName}` prefix.
 *
 * If an MCP server fails to start, falls back to config-based tool
 * registration using [AgentMcpConfig.toolDescriptions].
 */
class AgentMcpManager(
    private val homeDirectory: AgentHomeDirectory,
    private val toolRegistry: ToolRegistry
) {

    private val logger = LoggerFactory.getLogger(AgentMcpManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeServers = mutableMapOf<String, McpServerState>()

    suspend fun initialize() {
        val configs = homeDirectory.getMcpConfigs()
        if (configs.isEmpty()) {
            logger.info("No MCP server configurations found")
            return
        }
        logger.info("Initializing {} MCP server(s)", configs.size)
        configs.forEach { startAndRegister(it) }
        logger.info("MCP initialization complete: {}/{} servers active",
            activeServers.size, configs.size)
    }

    suspend fun shutdown() {
        if (activeServers.isEmpty()) return
        logger.info("Shutting down {} MCP server(s)", activeServers.size)
        activeServers.values.toList().forEach { shutdownServer(it) }
        activeServers.clear()
        scope.cancel()
        logger.info("All MCP servers shut down")
    }

    fun getActiveServerNames(): List<String> = activeServers.keys.toList()

    fun getRegisteredToolCount(): Int =
        activeServers.values.sumOf { it.toolNames.size }

    // ── Private helpers ──────────────────────────────────────────

    private suspend fun startAndRegister(config: AgentMcpConfig) {
        try {
            val serverName = sanitizeServerName(config.serverName)
            val state = startServer(config, serverName)
            activeServers[serverName] = state
            registerTools(config, serverName)
        } catch (e: Exception) {
            logger.error("Failed to start MCP server '{}': {}",
                config.serverName, e.message)
        }
    }

    private suspend fun startServer(
        config: AgentMcpConfig, serverName: String
    ): McpServerState {
        logger.info("Starting MCP server '{}': {} {}",
            serverName, config.command, config.args.joinToString(" "))
        return McpProcessStarter.startProcess(config, serverName, scope, logger)
    }

    private suspend fun registerTools(
        config: AgentMcpConfig, serverName: String
    ) {
        val state = activeServers[serverName] ?: return
        val tools = McpToolDiscovery.discoverTools(state, config, logger)
        val toolNames = tools.map { (name, desc) ->
            val prefixed = buildToolName(serverName, name)
            val wrapper = McpToolWrapper(prefixed, desc, serverName, state.client)
            toolRegistry.register(wrapper)
            prefixed
        }
        activeServers[serverName] = state.copy(toolNames = toolNames)
        logger.info("Registered {} tool(s) from MCP server '{}'",
            toolNames.size, serverName)
    }

    private fun shutdownServer(state: McpServerState) {
        try {
            logger.info("Shutting down MCP server '{}'", state.serverName)
            state.client?.close()
            state.process?.destroyForcibly()
        } catch (e: Exception) {
            logger.warn("Error shutting down '{}': {}",
                state.serverName, e.message)
        }
    }

    companion object {
        fun buildToolName(serverName: String, toolName: String): String {
            val prefix = "${serverName}_"
            return if (toolName.startsWith(prefix))
                "mcp_$toolName"
            else
                "mcp_${serverName}_$toolName"
        }

        fun sanitizeServerName(name: String): String =
            name.replace(Regex("[^a-zA-Z0-9_]"), "_")
            .lowercase()
            .trimStart('_').trimEnd('_')
            .ifEmpty { "unknown" }
    }
}

/** Tracks the state of a running MCP server. */
data class McpServerState(
    val serverName: String,
    val toolNames: List<String> = emptyList(),
    val isHealthy: Boolean = true,
    val client: McpProtocolClient? = null,
    val process: Process? = null
)

/**
 * Wrapper [AgentTool] that delegates execution to an MCP server.
 * Calls [McpProtocolClient.callTool] with the original tool name
 * (prefix stripped) and converts the response to [ToolResult].
 */
internal class McpToolWrapper(
    override val name: String,
    override val description: String,
    private val serverName: String,
    private val client: McpProtocolClient?
) : AgentTool {

    override val parameterNames: List<String> = emptyList()
    private val logger = LoggerFactory.getLogger(McpToolWrapper::class.java)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        logger.info("MCP tool call: {} on server '{}' params={}",
            name, serverName, params)
        if (client == null) return fallbackResult()
        return callMcpTool(params)
    }

    private suspend fun callMcpTool(params: Map<String, String>): ToolResult {
        return try {
            val originalName = McpToolNameResolver.stripPrefix(name, serverName)
            val jsonArgs = McpToolNameResolver.toJsonObject(params)
            val response = client!!.callTool(originalName, jsonArgs)
            McpToolNameResolver.toToolResult(name, response)
        } catch (e: Exception) {
            logger.error("MCP call failed for {}: {}", name, e.message)
            ToolResult(toolName = name, success = false,
                errorMessage = e.message ?: "MCP call failed")
        }
    }

    private fun fallbackResult(): ToolResult {
        val msg = "[MCP fallback] No client for '$name' on '$serverName'"
        return ToolResult(toolName = name, data = msg,
            dataSizeChars = msg.length, success = true)
    }
}
