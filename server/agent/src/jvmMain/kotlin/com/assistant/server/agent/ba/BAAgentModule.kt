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
import com.assistant.server.agent.home.McpCollisionDetector
import com.assistant.server.agent.tool.InputSchemaParser
import com.assistant.server.agent.tool.ParamTypeConverter
import com.assistant.server.agent.tool.ToolNameMapper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
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
    toolRegistry: ToolRegistry,
    nameMapper: ToolNameMapper = ToolNameMapper()
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
    val resolvedTools = McpCollisionDetector.resolve(activeTools)
    resolvedTools.forEach { resolved ->
        val tool = activeTools.first {
            it.name == resolved.originalName && it.serverName == resolved.serverName
        }
        val client = processManager.getClient(tool.serverId)
        val registeredName = resolveToolName(
            resolved.registeredName, tool.serverName, tool.name, nameMapper
        )
        val wrapper = BaMcpToolWrapper(
            name = registeredName,
            description = tool.description,
            serverName = tool.serverName,
            originalToolName = tool.name,
            client = client,
            inputSchema = tool.inputSchema
        )
        toolRegistry.register(wrapper)
    }
    logger.info("BA agent tool registration complete: ${activeTools.size} tools")
}

/**
 * Resolve the agent-facing tool name: use ToolNameMapper if a mapping exists,
 * otherwise fall back to `mcp_{collisionResolvedName}` convention.
 * Collision detection is applied BEFORE name mapping.
 */
private fun resolveToolName(
    collisionResolvedName: String, serverName: String,
    mcpToolName: String, nameMapper: ToolNameMapper
): String {
    val mapping = nameMapper.findByMcpTool(serverName, mcpToolName)
    if (mapping != null) {
        logger.info("Tool name mapping: {} → {}", mcpToolName, mapping.agentName)
        return mapping.agentName
    }
    return "mcp_$collisionResolvedName"
}

/**
 * Wraps an MCP tool from McpProcessManager for use in BA agent's ToolRegistry.
 */
private class BaMcpToolWrapper(
    override val name: String,
    override val description: String,
    private val serverName: String,
    private val originalToolName: String,
    private val client: McpProtocolClient?,
    private val inputSchema: JsonElement = JsonObject(emptyMap())
) : AgentTool {

    override val parameterNames: List<String> =
        InputSchemaParser.extractParameterNames(inputSchema)

    override suspend fun execute(params: Map<String, String>): ToolResult {
        logger.info("BA MCP tool call: {} on '{}' params={}", name, serverName, params)
        if (client == null) {
            return ToolResult(toolName = name, success = false,
                errorMessage = "No MCP client for server '$serverName'")
        }
        return try {
            val jsonArgs = ParamTypeConverter.convert(params, inputSchema)
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
