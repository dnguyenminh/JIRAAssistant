package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.models.ToolResult
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.agent.tool.AgentTool
import com.assistant.agent.tool.ToolRegistry
import com.assistant.server.agent.subprocess.SubprocessProxyImpl
import com.assistant.server.agent.tool.ToolRegistryImpl
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Feature: agent-subprocess-orchestration
 * Properties 7, 8 for SubprocessProxy and ToolRegistry
 *
 * **Validates: Requirements 5.3, 5.5**
 */
@OptIn(ExperimentalKotest::class)
@Tag("agent-subprocess-orchestration")
class SubprocessProxyPropertyTest {

    /**
     * Property 7: Combined tool descriptors completeness
     *
     * For any set of native BA tool descriptors and MCP tool
     * descriptors (with unique names), getAvailableToolDescriptors()
     * returns a list containing all native BA tool names and all
     * MCP tool names.
     *
     * **Validates: Requirements 5.3**
     */
    @Test
    fun `Property 7 - combined tool descriptors completeness`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolNameSet("native_", 1..6),
                arbToolNameSet("mcp_shared_", 1..10)
            ) { nativeNames, mcpNames ->
                val registry = ToolRegistryImpl()
                val allNames = nativeNames + mcpNames

                // Register MCP tools first (lower priority)
                mcpNames.forEach { name ->
                    registry.register(stubTool(name, "MCP: $name"))
                }
                // Register native tools last (higher priority)
                nativeNames.forEach { name ->
                    registry.register(stubTool(name, "Native: $name"))
                }

                val proxy = SubprocessProxyImpl(
                    toolRegistry = registry, agentType = "ba"
                )
                val descriptors = proxy.getAvailableToolDescriptors()
                val descriptorNames = descriptors.map { it.name }.toSet()

                for (expected in allNames) {
                    assertTrue(
                        expected in descriptorNames,
                        "Missing tool '$expected' in descriptors"
                    )
                }
            }
        }
    }

    /**
     * Property 8: Tool registration priority ordering
     *
     * For any tool name registered from both Local (native) and
     * MCP sources, ToolRegistry resolves to the Local native tool.
     * The MCP tool with duplicate name does not appear in listTools().
     *
     * **Validates: Requirements 5.5**
     */
    @Test
    fun `Property 8 - tool registration priority ordering`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolName("tool_"),
                arbToolNameSet("unique_mcp_", 0..5)
            ) { sharedName, extraMcpNames ->
                val registry = ToolRegistryImpl()

                // Register MCP tool first (lower priority)
                registry.register(
                    stubTool(sharedName, "MCP version")
                )
                // Register extra unique MCP tools
                extraMcpNames.forEach { name ->
                    registry.register(stubTool(name, "MCP: $name"))
                }
                // Register native tool last (higher priority)
                registry.register(
                    stubTool(sharedName, "Native version")
                )

                val tools = registry.listTools()
                val matching = tools.filter { it.name == sharedName }

                assertEquals(
                    1, matching.size,
                    "Duplicate name '$sharedName' should appear once"
                )
                assertEquals(
                    "Native version", matching.first().description,
                    "Native tool should take precedence"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    /** Generates a single tool name with the given prefix. */
    private fun arbToolName(prefix: String): Arb<String> =
        Arb.string(3..12, Codepoint.alphanumeric()).map {
            "$prefix$it"
        }

    /** Generates a set of unique tool names with the given prefix. */
    private fun arbToolNameSet(
        prefix: String,
        sizeRange: IntRange
    ): Arb<Set<String>> = arbitrary {
        val size = Arb.int(sizeRange).bind()
        (1..size).map { i ->
            val suffix = Arb.string(
                3..10, Codepoint.alphanumeric()
            ).bind()
            "${prefix}${i}_$suffix"
        }.toSet()
    }

    // ── Helpers ─────────────────────────────────────────────

    /** Creates a stub AgentTool for registration in ToolRegistry. */
    private fun stubTool(
        toolName: String,
        toolDescription: String
    ): AgentTool = object : AgentTool {
        override val name = toolName
        override val description = toolDescription
        override val parameterNames = emptyList<String>()
        override suspend fun execute(
            params: Map<String, String>
        ) = ToolResult(toolName = name, success = true, data = "ok")
    }
}
