package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.server.agent.ba.subprocess.pipeline.AiBackendPipelineStrategy
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import com.assistant.server.mcp.internal.InternalMcpBridge
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Preservation Property Tests — Existing Tool Routing and KB Detection.
 * PASS on unfixed code, MUST continue passing after fix.
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6** */
@OptIn(ExperimentalKotest::class)
class BrdPipelinePreservationPropertyTest {

    /** Property 2a: Unknown MCP tools return "MCP tool not found". **Validates: Requirements 3.3, 3.4** */
    @Test
    fun `Property 2a - unknown MCP tools return not-found error`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbUnknownToolRequest()) { toolName ->
                val bridge = ToolExecutionBridge(
                    stubProxy(), stubReporter(),
                    stubMcpManager(), InternalMcpBridge(null, null)
                )
                val result = bridge.execute(ToolRequest(tool = toolName))
                assertFalse(result.rawResponse.success, "Unknown tool '$toolName' should fail")
                assertTrue(
                    result.rawResponse.error.contains("MCP tool not found"),
                    "Error should contain 'MCP tool not found'. Got: '${result.rawResponse.error}'"
                )
            }
        }
    }

    /** Property 2a: Non-MCP tools route to SubprocessProxy. **Validates: Requirements 3.3, 3.4** */
    @Test
    fun `Property 2a - non-MCP tools route to SubprocessProxy`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbNonMcpToolName()) { toolName ->
                val bridge = ToolExecutionBridge(
                    stubProxy(), stubReporter(),
                    stubMcpManager(), InternalMcpBridge(null, null)
                )
                val result = bridge.execute(ToolRequest(tool = toolName))
                assertTrue(
                    result.rawResponse.success,
                    "Non-MCP tool '$toolName' should route to SubprocessProxy. Error: ${result.rawResponse.error}"
                )
            }
        }
    }

    /** Property 2b: hasKbTools() true for external KB tools. **Validates: Requirements 3.2** */
    @Test
    fun `Property 2b - hasKbTools returns true for external KB tools`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbExternalKbToolList()) { tools ->
                val filter = PhaseToolFilter()
                assertTrue(
                    filter.hasKbTools(tools),
                    "hasKbTools() should return true when KB patterns present. Tools: ${tools.map { it.name }}"
                )
            }
        }
    }

    /** Property 2b: hasKbTools() false when no KB patterns. **Validates: Requirements 3.2** */
    @Test
    fun `Property 2b - hasKbTools returns false when no KB patterns`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbNonKbToolList()) { tools ->
                val filter = PhaseToolFilter()
                assertFalse(
                    filter.hasKbTools(tools),
                    "hasKbTools() should return false when no KB patterns. Tools: ${tools.map { it.name }}"
                )
            }
        }
    }

    /** Property 2c: collectToolDescriptors() excludes Local KB when disabled. **Validates: Requirements 3.1** */
    @Test
    fun `Property 2c - collectToolDescriptors excludes KB tools when disabled`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbExternalToolSet()) { externalTools ->
                val strategy = AiBackendPipelineStrategy(
                    subprocessProxy = stubProxy(),
                    cliBackendResolver = stubResolver(),
                    mcpProcessManager = stubMcpManager(externalTools),
                    internalMcpBridge = InternalMcpBridge(null, null)
                )
                val method = AiBackendPipelineStrategy::class.java
                    .getDeclaredMethod("collectToolDescriptors")
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val tools = method.invoke(strategy) as List<ToolDescriptor>

                val hasLocalKb = tools.any { it.name.contains("local-knowledge-base") }
                assertFalse(
                    hasLocalKb,
                    "collectToolDescriptors() should NOT include local-knowledge-base tools. " +
                        "Got: ${tools.filter { it.name.contains("local-knowledge-base") }.map { it.name }}"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────────

    /** Generate MCP tool names for unknown servers (not internal, not external) */
    private fun arbUnknownToolRequest(): Arb<String> = Arb.element(
        "mcp_unknown-server_some_tool",
        "mcp_nonexistent_action",
        "mcp_fake-mcp_do_something",
        "mcp_test-server-xyz_run",
        "mcp_random-service_query"
    )

    /** Generate non-MCP tool names (no "mcp_" prefix) */
    private fun arbNonMcpToolName(): Arb<String> = Arb.element(
        "navigate", "scan_project", "analyze_ticket",
        "get_settings", "update_settings", "chat_message",
        "get_scan_status", "get_analysis_result"
    )

    /** Generate tool lists that contain at least one KB pattern */
    private fun arbExternalKbToolList(): Arb<List<ToolDescriptor>> =
        Arb.bind(arbKbToolDescriptor(), arbNonKbToolList()) { kb, nonKb -> nonKb + kb }

    private fun arbKbToolDescriptor(): Arb<ToolDescriptor> = Arb.element(
        ToolDescriptor("mcp_knowledge-base_kb_search", "Search KB"),
        ToolDescriptor("mcp_knowledge-base_kb_ingest", "Ingest KB"),
        ToolDescriptor("mcp_knowledge-base_kb_write", "Write KB"),
        ToolDescriptor("mcp_obsidian_kb_search_smart", "Smart search"),
        ToolDescriptor("mcp_kb-server_kb_ingest_doc", "Ingest doc"),
        ToolDescriptor("mcp_kb-server_kb_write_note", "Write note")
    )

    /** Generate tool lists with NO KB patterns */
    private fun arbNonKbToolList(): Arb<List<ToolDescriptor>> =
        Arb.list(arbNonKbToolDescriptor(), 1..8)

    private fun arbNonKbToolDescriptor(): Arb<ToolDescriptor> = Arb.element(
        ToolDescriptor("mcp_markitdown_convert_to_markdown", "Convert"),
        ToolDescriptor("mcp_fetch_fetch", "Fetch URL"),
        ToolDescriptor("mcp_jira_get_issue", "Get issue"),
        ToolDescriptor("mcp_jira_search_jira", "Search Jira"),
        ToolDescriptor("mcp_drawio_create_diagram", "Create diagram"),
        ToolDescriptor("navigate", "Navigate to page"),
        ToolDescriptor("scan_project", "Scan project"),
        ToolDescriptor("analyze_ticket", "Analyze ticket")
    )

    private fun arbExternalToolSet(): Arb<List<McpAggregatedTool>> =
        Arb.list(arbExternalMcpTool(), 0..6)

    private fun arbExternalMcpTool(): Arb<McpAggregatedTool> = Arb.element(
        mcpTool("markitdown", "convert_to_markdown", "Convert"),
        mcpTool("fetch", "fetch", "Fetch URL"),
        mcpTool("jira", "get_issue", "Get Jira issue"),
        mcpTool("jira", "search_jira", "Search Jira"),
        mcpTool("drawio", "create_diagram", "Create diagram"),
        mcpTool("stitch", "create_project", "Create project")
    )
}
