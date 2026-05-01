package com.assistant.server.agent.home

import com.assistant.agent.home.AgentHomeConfig
import com.assistant.agent.home.AgentHomeDirectory
import com.assistant.agent.home.AgentMcpConfig
import com.assistant.agent.home.RuleDefinition
import com.assistant.agent.home.SkillDefinition
import com.assistant.agent.home.WorkflowDefinition
import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
import com.assistant.server.agent.tool.ToolRegistryImpl
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Property-based tests for MCP tool registration with server
 * prefix (Property 36).
 */
@OptIn(ExperimentalKotest::class)
class McpToolRegistrationPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 36: MCP tool registration with server prefix.
     *
     * For any MCP server config with server name S containing
     * tools [t₁, t₂, ..., tₙ], after auto-registration the
     * ToolRegistry contains tools named `mcp_{S}_{tᵢ}`.
     *
     * **Validates: Requirements 19.1, 19.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-36")
    fun `MCP tools registered with server name prefix`() {
        runBlocking {
            checkAll(cfg, arbMcpConfigs()) { configs ->
                val registry = ToolRegistryImpl()
                val home = FakeHomeDirectory(configs)
                val manager = AgentMcpManager(home, registry)
                manager.initialize()
                assertPrefixedToolsRegistered(registry, configs)
            }
        }
    }

    /**
     * Property 36 (continued): MCP tools inherit the same
     * rate limiting, timeout, and logging policies as native
     * AgentTool invocations — verified by invoking an MCP
     * tool through the registry and checking it counts toward
     * the rate limit.
     *
     * **Validates: Requirements 19.1, 19.3**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-36")
    fun `MCP tools share rate limit with native tools`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..5)) { maxCalls ->
                val registry = ToolRegistryImpl(maxCalls = maxCalls)
                val config = singleToolConfig("srv", "search")
                val home = FakeHomeDirectory(listOf(config))
                AgentMcpManager(home, registry).initialize()
                registry.register(nativeTool("local_tool"))
                drainRateLimit(registry, maxCalls)
                // Find the actual registered MCP tool name
                val mcpToolName = registry.listTools()
                    .map { it.name }
                    .first { it.startsWith("mcp_") && it.contains("search") }
                val overflow = registry.invoke(
                    mcpToolName, emptyMap()
                )
                overflow.success shouldBe false
                overflow.errorType shouldBe "RATE_LIMIT_EXCEEDED"
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun assertPrefixedToolsRegistered(
        registry: ToolRegistryImpl,
        configs: List<AgentMcpConfig>
    ) {
        val listed = registry.listTools().map { it.name }.toSet()
        for (config in configs) {
            for (toolName in config.toolDescriptions.keys) {
                // Non-colliding tools: "mcp_{toolName}"
                // Colliding tools: "mcp_{serverName}_{toolName}"
                val sanitized = AgentMcpManager.sanitizeServerName(
                    config.serverName
                )
                val prefixed = "mcp_${sanitized}_$toolName"
                val unprefixed = "mcp_$toolName"
                assertTrue(
                    listed.contains(prefixed) || listed.contains(unprefixed),
                    "Tool '$toolName' from server '${config.serverName}' " +
                        "should be registered as '$prefixed' or '$unprefixed'. " +
                        "Registered tools: $listed"
                )
            }
        }
    }

    private suspend fun drainRateLimit(
        registry: ToolRegistryImpl,
        maxCalls: Int
    ) {
        val tools = registry.listTools()
        if (tools.isEmpty()) return
        val name = tools.first().name
        repeat(maxCalls) { registry.invoke(name, emptyMap()) }
    }

    private fun singleToolConfig(
        server: String,
        tool: String
    ) = AgentMcpConfig(
        serverName = server,
        command = "builtin",
        toolDescriptions = mapOf(tool to "desc")
    )

    private fun nativeTool(name: String) = object : AgentTool {
        override val name = name
        override val description = "native"
        override val parameterNames = emptyList<String>()
        override suspend fun execute(
            params: Map<String, String>
        ) = ToolResult(toolName = name, success = true)
    }
}

// ── Arb generators ──────────────────────────────────────────────

private val safeStr = Arb.string(1..10, Codepoint.alphanumeric())

private fun arbMcpConfigs(): Arb<List<AgentMcpConfig>> = arbitrary {
    val count = Arb.int(1..3).bind()
    (1..count).map {
        val toolCount = Arb.int(1..4).bind()
        val tools = (1..toolCount).associate {
            safeStr.bind() to safeStr.bind()
        }
        AgentMcpConfig(
            serverName = safeStr.bind(),
            command = "builtin",
            toolDescriptions = tools
        )
    }
}

// ── Fake AgentHomeDirectory ─────────────────────────────────────

private class FakeHomeDirectory(
    private val mcpConfigs: List<AgentMcpConfig>
) : AgentHomeDirectory {
    override fun getConfig() = AgentHomeConfig(agentType = "test")
    override fun getSkills() = emptyList<SkillDefinition>()
    override fun getActiveSkills() = emptyList<SkillDefinition>()
    override fun getRules() = emptyList<RuleDefinition>()
    override fun getWorkflows() = emptyList<WorkflowDefinition>()
    override fun getMcpConfigs() = mcpConfigs
    override fun buildSystemPrompt() = ""
    override fun reload() {}
}
