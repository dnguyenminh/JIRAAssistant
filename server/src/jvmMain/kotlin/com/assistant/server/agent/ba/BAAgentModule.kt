package com.assistant.server.agent.ba

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolResult
import com.assistant.agent.registry.AgentRegistry
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.server.agent.ba.memory.JiraContextMemorySchema
import com.assistant.server.agent.ba.subprocess.BASubprocessOrchestrator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.assistant.server.agent.ba.BAAgentModule")

/**
 * Koin module for the BA Document Agent.
 *
 * Tool registration: MCP tools from Integrations page (database)
 * via McpProcessManager.getActiveTools().
 */
val baAgentModule = module {

    single {
        BASubprocessOrchestrator(
            subprocessManager = get(),
            subprocessProxy = get(),
            progressReporter = get(),
            settingsRepository = get(),
            providerConfigRepo = getOrNull(),
            mcpProcessManager = getOrNull(),
            internalMcpBridge = getOrNull(),
            localKBToolExecutor = getOrNull(),
            kbRepository = getOrNull(),
            jiraContentExtractor = getOrNull(),
            vectorStore = getOrNull()
        )
    }

    single(createdAtStart = true) {
        val registry: AgentRegistry = get()
        registry.register(BADocumentAgent.AGENT_TYPE) { _ ->
            val toolRegistry: ToolRegistry = get()
            registerMcpToolsFromDatabase(toolRegistry)
            BADocumentAgent(
                toolRegistry = toolRegistry,
                memory = JiraContextMemorySchema.createMemory(),
                progressReporter = get(),
                subprocessOrchestrator = getOrNull()
            )
        }
    }
}

/**
 * Register MCP tools from McpProcessManager (database/Integrations page)
 * into the BA agent's ToolRegistry.
 */
private fun org.koin.core.scope.Scope.registerMcpToolsFromDatabase(
    toolRegistry: ToolRegistry
) {
    val processManager: McpProcessManager? = getOrNull()
    if (processManager == null) {
        logger.warn("McpProcessManager not available — no MCP tools for BA agent")
        return
    }
    val activeTools = processManager.getActiveTools()
    if (activeTools.isEmpty()) {
        logger.info("No active MCP tools found for BA agent")
        return
    }
    logger.info("Registering ${activeTools.size} MCP tools for BA agent")
    activeTools.forEach { tool ->
        val client = processManager.getClient(tool.serverId)
        val wrapper = BaMcpToolWrapper(
            name = "mcp_${tool.serverName}_${tool.name}",
            description = tool.description,
            serverName = tool.serverName,
            originalToolName = tool.name,
            client = client
        )
        toolRegistry.register(wrapper)
    }
    logger.info("BA agent tool registration complete: ${activeTools.size} tools")
}

/**
 * Wraps an MCP tool from McpProcessManager for use in BA agent's ToolRegistry.
 */
private class BaMcpToolWrapper(
    override val name: String,
    override val description: String,
    private val serverName: String,
    private val originalToolName: String,
    private val client: McpProtocolClient?
) : AgentTool {

    override val parameterNames: List<String> = emptyList()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        logger.info("BA MCP tool call: {} on '{}' params={}", name, serverName, params)
        if (client == null) {
            return ToolResult(toolName = name, success = false,
                errorMessage = "No MCP client for server '$serverName'")
        }
        return try {
            val jsonArgs = JsonObject(params.mapValues { JsonPrimitive(it.value) })
            val response = client.callTool(originalToolName, jsonArgs)
            val text = response.content.joinToString("\n") { it.text ?: "" }
            ToolResult(toolName = name, data = text,
                dataSizeChars = text.length, success = !response.isError)
        } catch (e: Exception) {
            logger.error("BA MCP call failed for {}: {}", name, e.message)
            ToolResult(toolName = name, success = false,
                errorMessage = e.message ?: "MCP call failed")
        }
    }
}
