package com.assistant.server.agent.subprocess

import com.assistant.agent.models.ToolResult
import com.assistant.agent.subprocess.ToolCallRequest
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

/**
 * Property-based tests for tool registration priority ordering
 * (Property 40).
 *
 * Three-tier priority: Local > Agent Home MCP > Shared MCP Bridge.
 * When tools from different sources share a name, the higher-priority
 * source takes precedence.
 */
@OptIn(ExperimentalKotest::class)
class ToolPriorityPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * Property 40: Tool registration priority ordering.
     *
     * When a local tool is registered after an MCP tool with the
     * same name, the local tool wins (last-registration-wins in
     * ToolRegistry, and the framework registers in priority order:
     * Shared MCP → Agent Home MCP → Local).
     *
     * **Validates: Requirements 20.9**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-40")
    fun `local tool overrides shared MCP tool`() {
        runBlocking {
            checkAll(cfg, arbToolName()) { toolName ->
                val registry = ToolRegistryImpl()
                registry.register(markerTool(toolName, "SHARED_MCP"))
                registry.register(markerTool(toolName, "LOCAL"))
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry
                )
                val request = ToolCallRequest(
                    id = "req1", name = toolName
                )
                val response = proxy.handleToolCallRequest(request)
                response.success shouldBe true
                response.data shouldBe "LOCAL"
            }
        }
    }

    /**
     * Property 40 (continued): Agent Home MCP overrides Shared MCP.
     *
     * **Validates: Requirements 20.9**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-40")
    fun `agent home MCP overrides shared MCP tool`() {
        runBlocking {
            checkAll(cfg, arbToolName()) { toolName ->
                val registry = ToolRegistryImpl()
                registry.register(markerTool(toolName, "SHARED_MCP"))
                registry.register(markerTool(toolName, "AGENT_MCP"))
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry
                )
                val request = ToolCallRequest(
                    id = "req1", name = toolName
                )
                val response = proxy.handleToolCallRequest(request)
                response.success shouldBe true
                response.data shouldBe "AGENT_MCP"
            }
        }
    }

    /**
     * Property 40 (continued): Full three-tier ordering.
     * Register Shared → Agent Home → Local; Local wins.
     *
     * **Validates: Requirements 20.9**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-40")
    fun `three tier priority Local over AgentMCP over SharedMCP`() {
        runBlocking {
            checkAll(cfg, arbToolName()) { toolName ->
                val registry = ToolRegistryImpl()
                // Register in ascending priority order
                registry.register(markerTool(toolName, "SHARED_MCP"))
                registry.register(markerTool(toolName, "AGENT_MCP"))
                registry.register(markerTool(toolName, "LOCAL"))
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry
                )
                val request = ToolCallRequest(
                    id = "req1", name = toolName
                )
                val response = proxy.handleToolCallRequest(request)
                response.success shouldBe true
                response.data shouldBe "LOCAL"
            }
        }
    }

    /**
     * Property 40 (continued): When only lower-priority sources
     * exist, the highest available source is used.
     *
     * **Validates: Requirements 20.9**
     */
    @Test
    @Tag("generic-agent-framework")
    @Tag("Property-40")
    fun `lowest priority source used when no higher exists`() {
        runBlocking {
            checkAll(cfg, arbToolName()) { toolName ->
                val registry = ToolRegistryImpl()
                registry.register(markerTool(toolName, "SHARED_MCP"))
                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry
                )
                val request = ToolCallRequest(
                    id = "req1", name = toolName
                )
                val response = proxy.handleToolCallRequest(request)
                response.success shouldBe true
                response.data shouldBe "SHARED_MCP"
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Tool that returns its source marker as data. */
    private fun markerTool(name: String, marker: String) =
        object : AgentTool {
            override val name = name
            override val description = "$marker tool"
            override val parameterNames = emptyList<String>()
            override suspend fun execute(
                params: Map<String, String>
            ) = ToolResult(
                toolName = name,
                data = marker,
                dataSizeChars = marker.length,
                success = true
            )
        }
}

// ── Arb generators ──────────────────────────────────────────────

private fun arbToolName(): Arb<String> =
    Arb.string(1..15, Codepoint.alphanumeric())
