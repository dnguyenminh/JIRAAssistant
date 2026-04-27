package com.assistant.server.agent.ba

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolResult
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessManager
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.kb.ProviderConfigRepository
import com.assistant.agent.home.AgentHomeConfig
import com.assistant.agent.home.AgentHomeDirectory
import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.home.RuleDefinition
import com.assistant.agent.home.SkillDefinition
import com.assistant.agent.home.WorkflowDefinition
import com.assistant.server.agent.home.AgentMcpManager
import com.assistant.server.agent.subprocess.SubprocessProxyImpl
import com.assistant.server.agent.tool.ToolRegistryImpl
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

/** In-memory ProviderConfigRepository for integration tests. */
class InMemoryProviderConfigRepo : ProviderConfigRepository() {
    private val providers = mutableListOf<ProviderConfig>()
    fun addProvider(config: ProviderConfig) { providers.add(config) }
    fun clear() = providers.clear()
    override fun getAllProviders() = providers.toList()
    override fun findById(providerId: String) =
        providers.firstOrNull { it.providerId == providerId }
    override fun findByType(type: ProviderType) =
        providers.firstOrNull { it.type == type }
    override fun save(config: ProviderConfig): Boolean {
        providers.removeAll { it.providerId == config.providerId }
        providers.add(config)
        return true
    }
}

/**
 * Fake SubprocessManager for integration tests.
 * Delegates sendCommand to a configurable stdout provider.
 */
class FakeSubprocessManager(
    private val stdoutProvider: (Int, String) -> Flow<String>
) : SubprocessManager {
    private var callCount = 0
    override suspend fun sendCommand(
        agentType: String, command: String
    ): Flow<String> { callCount++; return stdoutProvider(callCount, command) }
    override suspend fun isRunning(agentType: String) = true
    override suspend fun terminate(agentType: String) {}
    override suspend fun terminateAll() {}
    override fun getRunningAgentTypes() = listOf("ba-agent")
}

/** Fake SubprocessProxy with configurable tool handler. */
class FakeSubprocessProxy(
    private val toolHandler: (ToolCallRequest) -> ToolCallResponse =
        { req -> ToolCallResponse(req.id, true, "ok") }
) : SubprocessProxy {
    var handleCount = 0; private set
    override suspend fun handleToolCallRequest(
        request: ToolCallRequest
    ): ToolCallResponse { handleCount++; return toolHandler(request) }
    override fun getAvailableToolDescriptors() = listOf(
        ToolDescriptor("mcp_jira_get_issue", "Get Jira issue details")
    )
    override fun buildToolListMessage() = "tool-list-injection"
    override fun buildToolsUpdatedMessage() = ""
}

/** Fake SettingsRepository backed by a mutable map. */
class FakeSettingsRepo(
    private val map: MutableMap<String, String> = mutableMapOf()
) : SettingsRepository {
    override suspend fun getAll() = map.toMap()
    override suspend fun get(key: String) = map[key]
    override suspend fun put(key: String, value: String) { map[key] = value }
    override suspend fun putAll(settings: Map<String, String>) {
        map.putAll(settings)
    }
}

/** NoOp ProgressReporter for integration tests. */
object IntegrationNoOpReporter : ProgressReporter {
    override suspend fun reportPhase(
        phaseName: String, phaseIndex: Int, totalPhases: Int
    ) = Unit
    override suspend fun reportProgress(
        percent: Int, message: String
    ) = Unit
    override suspend fun reportToolCall(
        toolName: String, status: String
    ) = Unit
}

/** NoOp ToolRegistry for integration tests. */
class IntegrationNoOpToolRegistry : ToolRegistry {
    override fun register(tool: AgentTool) {}
    override fun registerAll(tools: List<AgentTool>) {}
    override fun listTools() = emptyList<ToolDescriptor>()
    override suspend fun invoke(
        toolName: String, params: Map<String, String>
    ) = ToolResult(toolName = toolName, success = false)
    override fun getRemainingCalls() = 50
    override fun resetCallCount() {}
}

// ── Factory helpers ──────────────────────────────────────

fun simpleDocStdoutProvider(): (Int, String) -> Flow<String> = { call, _ ->
    if (call <= 1) flowOf("---END---")
    else flowOf("# BRD Document", "## Introduction", "---END---")
}

fun toolCallStdoutProvider(): (Int, String) -> Flow<String> = { call, _ ->
    when {
        call <= 1 -> flowOf("---END---")
        call == 2 -> flowOf(
            """{"type":"toolCall","toolCall":{"id":"tc-1","name":"mcp_jira_get_issue","arguments":{"ticketId":"PROJ-123"}}}""",
            "---END---"
        )
        else -> flowOf(
            "# BRD Document", "## Introduction",
            "Generated from PROJ-123", "---END---"
        )
    }
}

// ── Real tool layer for MCP integration tests ────────────

/** Holds real ToolRegistryImpl + SubprocessProxyImpl wired via AgentMcpManager. */
data class RealToolLayer(
    val toolRegistry: ToolRegistryImpl,
    val subprocessProxy: SubprocessProxyImpl
)

private val mcpJson = Json { ignoreUnknownKeys = true }

/**
 * Builds a real tool layer from MCP configs in test.properties.
 * Reads `test.mcp.*` properties, parses each as AgentMcpConfig JSON,
 * initializes AgentMcpManager, and wires SubprocessProxyImpl.
 */
suspend fun buildRealToolLayer(): RealToolLayer {
    val toolRegistry = ToolRegistryImpl()
    val homeDirectory = PropertiesHomeDirectory(loadMcpConfigs())
    val mcpManager = AgentMcpManager(homeDirectory, toolRegistry)
    mcpManager.initialize()
    val subprocessProxy = SubprocessProxyImpl(
        toolRegistry = toolRegistry,
        parallelToolExecutor = null,
        agentType = "ba-agent"
    )
    return RealToolLayer(toolRegistry, subprocessProxy)
}

/** Loads MCP configs from test.properties `test.mcp.*` entries. */
private fun loadMcpConfigs(): List<AgentMcpConfig> {
    val props = loadTestProps() ?: return emptyList()
    return props.stringPropertyNames()
        .filter { it.startsWith("test.mcp.") }
        .mapNotNull { key ->
            try {
                mcpJson.decodeFromString<AgentMcpConfig>(props.getProperty(key))
            } catch (_: Exception) { null }
        }
}

private fun loadTestProps(): java.util.Properties? {
    val stream = object {}.javaClass.classLoader
        .getResourceAsStream("test.properties") ?: return null
    return java.util.Properties().apply { load(stream) }
}

/** Minimal AgentHomeDirectory backed by a list of MCP configs. */
private class PropertiesHomeDirectory(
    private val mcpConfigs: List<AgentMcpConfig>
) : AgentHomeDirectory {
    override fun getConfig() = AgentHomeConfig()
    override fun getSkills() = emptyList<SkillDefinition>()
    override fun getActiveSkills() = emptyList<SkillDefinition>()
    override fun getRules() = emptyList<RuleDefinition>()
    override fun getWorkflows() = emptyList<WorkflowDefinition>()
    override fun getMcpConfigs() = mcpConfigs
    override fun buildSystemPrompt() = ""
    override fun reload() {}
}
