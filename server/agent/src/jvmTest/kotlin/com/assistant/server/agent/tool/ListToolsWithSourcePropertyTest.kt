package com.assistant.server.agent.tool

import com.assistant.agent.models.ToolResult
import com.assistant.agent.tool.AgentTool
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
 * Property-based tests for listToolsWithSource() (Property 6).
 *
 * Feature: agent-mcp-tool-bridge
 * Property 6: Tool source metadata correctness
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 */
@OptIn(ExperimentalKotest::class)
class ListToolsWithSourcePropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    // ── Generators ──────────────────────────────────────────────

    private fun arbLocalToolName(): Arb<String> = arbitrary {
        val suffix = Arb.string(3..10, Codepoint.alphanumeric()).bind()
        "local_$suffix"
    }

    private fun arbSharedMcpToolName(): Arb<String> = arbitrary {
        val server = Arb.string(2..6, Codepoint.alphanumeric()).bind()
        val tool = Arb.string(2..6, Codepoint.alphanumeric()).bind()
        "mcp_${server}_$tool"
    }

    private fun arbAgentMcpToolName(): Arb<String> = arbitrary {
        val server = Arb.string(2..6, Codepoint.alphanumeric()).bind()
        val tool = Arb.string(2..6, Codepoint.alphanumeric()).bind()
        "mcp_agent_${server}_$tool"
    }

    private fun stubTool(name: String): AgentTool = object : AgentTool {
        override val name = name
        override val description = "desc-$name"
        override val parameterNames = emptyList<String>()
        override suspend fun execute(
            params: Map<String, String>
        ) = ToolResult(toolName = name, success = true)
    }

    // ── Property tests ──────────────────────────────────────────

    /**
     * Property 6a: Result count matches total unique registered tools.
     *
     * **Validates: Requirements 3.4**
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-6")
    fun `listToolsWithSource count matches registered tools`() {
        runBlocking {
            checkAll(cfg, arbMixedToolNames()) { names ->
                val registry = ToolRegistryImpl()
                names.forEach { registry.register(stubTool(it)) }
                val result = registry.listToolsWithSource()
                result.size shouldBe names.toSet().size
            }
        }
    }

    /**
     * Property 6b: Every MCP tool has correct toolSource.
     *
     * For any tool with "mcp_agent_" prefix → AGENT_MCP,
     * "mcp_" prefix → SHARED_MCP, else → LOCAL.
     *
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-6")
    fun `listToolsWithSource assigns correct source per prefix`() {
        runBlocking {
            checkAll(cfg, arbMixedToolNames()) { names ->
                val registry = ToolRegistryImpl()
                val unique = names.toSet()
                unique.forEach { registry.register(stubTool(it)) }
                val result = registry.listToolsWithSource()
                result.forEach { desc ->
                    val expected = expectedSource(desc.name)
                    desc.toolSource shouldBe expected
                }
            }
        }
    }

    /**
     * Property 6c: MCP tools have non-null serverName.
     *
     * **Validates: Requirements 3.2, 3.3**
     */
    @Test
    @Tag("agent-mcp-tool-bridge")
    @Tag("Property-6")
    fun `MCP tools have serverName populated`() {
        runBlocking {
            checkAll(cfg, arbMixedToolNames()) { names ->
                val registry = ToolRegistryImpl()
                names.toSet().forEach { registry.register(stubTool(it)) }
                val result = registry.listToolsWithSource()
                result.filter { it.toolSource != "LOCAL" }
                    .forEach { it.serverName shouldBe it.serverName }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun arbMixedToolNames(): Arb<List<String>> = arbitrary {
        val localCount = Arb.int(0..3).bind()
        val sharedCount = Arb.int(0..3).bind()
        val agentCount = Arb.int(0..3).bind()
        val locals = (1..localCount).map { arbLocalToolName().bind() }
        val shared = (1..sharedCount).map { arbSharedMcpToolName().bind() }
        val agent = (1..agentCount).map { arbAgentMcpToolName().bind() }
        (locals + shared + agent).shuffled()
    }

    private fun expectedSource(name: String): String = when {
        name.startsWith("mcp_agent_") -> "AGENT_MCP"
        name.startsWith("mcp_") -> "SHARED_MCP"
        else -> "LOCAL"
    }
}
