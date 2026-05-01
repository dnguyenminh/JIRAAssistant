package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeDirectory
import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.server.agent.tool.InputSchemaParser
import com.assistant.server.agent.tool.ParamTypeConverter
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
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
        configs.forEach { startServer(it) }
        registerAllToolsWithCollisionDetection(configs)
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

    private suspend fun startServer(config: AgentMcpConfig) {
        try {
            val serverName = sanitizeServerName(config.serverName)
            val state = startServerProcess(config, serverName)
            activeServers[serverName] = state
        } catch (e: Exception) {
            logger.error("Failed to start MCP server '{}': {}",
                config.serverName, e.message)
        }
    }

    private suspend fun startServerProcess(
        config: AgentMcpConfig, serverName: String
    ): McpServerState {
        logger.info("Starting MCP server '{}': {} {}",
            serverName, config.command, config.args.joinToString(" "))
        return McpProcessStarter.startProcess(config, serverName, scope, logger)
    }

    private suspend fun registerAllToolsWithCollisionDetection(
        configs: List<AgentMcpConfig>
    ) {
        val allTools = discoverAllTools(configs)
        if (allTools.isEmpty()) return
        val resolved = McpCollisionDetector.resolve(allTools)
        resolved.forEach { r ->
            val state = activeServers[r.serverName] ?: return@forEach
            val prefixed = "mcp_${r.registeredName}"
            val tool = allTools.first {
                it.name == r.originalName && it.serverName == r.serverName
            }
            val wrapper = McpToolWrapper(
                prefixed, tool.description, r.serverName,
                state.client, tool.inputSchema
            )
            toolRegistry.register(wrapper)
            val updated = state.copy(
                toolNames = state.toolNames + prefixed
            )
            activeServers[r.serverName] = updated
        }
    }

    private suspend fun discoverAllTools(
        configs: List<AgentMcpConfig>
    ): List<McpAggregatedTool> {
        return configs.flatMap { config ->
            val serverName = sanitizeServerName(config.serverName)
            val state = activeServers[serverName] ?: return@flatMap emptyList()
            McpToolDiscovery.discoverTools(state, config, logger)
                .map { (name, desc, schema) ->
                    McpAggregatedTool(
                        serverId = serverName,
                        serverName = serverName,
                        name = name,
                        description = desc,
                        inputSchema = schema
                    )
                }
        }
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
    private val client: McpProtocolClient?,
    private val inputSchema: JsonElement = JsonObject(emptyMap())
) : AgentTool {

    override val parameterNames: List<String> =
        InputSchemaParser.extractParameterNames(inputSchema)
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
            val jsonArgs = ParamTypeConverter.convert(params, inputSchema)
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
