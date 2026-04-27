package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import org.junit.jupiter.api.Test
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Deep BRD Generation feature.
 * Validates constants, diagram sections, API compatibility,
 * and backward compatibility with existing prompt builder.
 */
class DeepBrdGenerationUnitTest {

    // ── Task 9.1: Constants ─────────────────────────────────

    @Test fun `DEPTH_LIMIT equals 3`() = assertEquals(3, DEPTH_LIMIT)
    @Test fun `MAX_TICKETS equals 30`() = assertEquals(30, MAX_TICKETS)
    @Test fun `MAX_TOOL_RESULTS_CHARS equals 80000`() = assertEquals(80_000, MAX_TOOL_RESULTS_CHARS)

    // ── Task 9.1: API compatibility ─────────────────────────

    @Test fun `appendDataCollectionStrategy accepts ticketId and tools`() {
        val tools = listOf(ToolDescriptor("mcp_jira_get_issue", "Get issue"))
        val out = buildString { appendDataCollectionStrategy("PROJ-1", tools) }
        assertTrue(out.contains("DATA COLLECTION"), "Should produce data collection strategy")
    }

    // ── Task 3.3: Diagram sections ──────────────────────────

    private val noToolsDetected = detectToolCategories(emptyList())
    private val withDiagramTool = detectToolCategories(
        listOf(ToolDescriptor("mcp_drawio_create", "Create draw.io diagram"))
    )

    @Test fun `diagram instructions without tools contain inline XML guidance`() {
        val out = buildString { appendDiagramInstructions(noToolsDetected) }
        assertTrue(out.contains("Process Flow"), "Should mention Process Flow diagram")
        assertTrue(out.contains("mxGraphModel"), "Should contain XML template")
    }

    @Test fun `diagram instructions with tool prefer MCP tool`() {
        val out = buildString { appendDiagramInstructions(withDiagramTool) }
        assertTrue(out.contains("mcp_drawio_create"), "Should reference the diagram tool")
        assertTrue(out.contains("PREFER"), "Should prefer tool over inline XML")
    }

    @Test fun `diagram placement rules present`() {
        val out = buildString { appendDiagramInstructions(noToolsDetected) }
        assertTrue(out.contains("Existing Processes"), "Should mention Existing Processes section")
        assertTrue(out.contains("Data Requirements") || out.contains("Project Requirements"))
    }

    @Test fun `diagram fallback text pattern present`() {
        val out = buildString { appendDiagramInstructions(noToolsDetected) }
        assertTrue(out.contains("Diagram không khả dụng"), "Should contain Vietnamese fallback text")
    }

    // ── Task 9.2: Backward compatibility ────────────────────

    @Test fun `buildInitialPrompt works with realistic tool list`() {
        val tools = listOf(
            ToolDescriptor("mcp_jira_get_issue", "Get Jira issue"),
            ToolDescriptor("mcp_jira_search_jira", "Search Jira"),
            ToolDescriptor("mcp_knowledge_base_kb_search_smart", "KB search")
        )
        val proxy = mockProxy(tools)
        val builder = AgenticPromptBuilder(proxy)
        val prompt = builder.buildInitialPrompt("PROJ-123", "BRD")
        assertTrue(prompt.contains("PROJ-123"))
        assertTrue(prompt.contains("mcp_jira_get_issue"))
        assertTrue(prompt.contains("DIAGRAM"))
    }

    @Test fun `buildStatelessContinuation with large data triggers truncation`() {
        val tools = listOf(ToolDescriptor("mcp_jira_get_issue", "Get issue"))
        val proxy = mockProxy(tools)
        val builder = AgenticPromptBuilder(proxy)
        val bigResults = List(10) { "x".repeat(20_000) }
        val out = builder.buildStatelessContinuation("PROJ-1", "BRD", bigResults)
        assertTrue(out.contains("TRUNCATED"), "Should contain truncation annotation")
        assertTrue(out.contains("DIAGRAM"), "Should still contain diagram instructions")
    }

    private fun mockProxy(tools: List<ToolDescriptor>): SubprocessProxy =
        object : SubprocessProxy {
            override suspend fun handleToolCallRequest(r: ToolCallRequest) =
                ToolCallResponse(r.id, true, "", "")
            override fun getAvailableToolDescriptors() = tools
            override fun buildToolListMessage() = ""
            override fun buildToolsUpdatedMessage() = ""
        }
}
