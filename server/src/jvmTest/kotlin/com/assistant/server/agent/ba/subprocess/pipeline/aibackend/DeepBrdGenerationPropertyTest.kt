package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for Deep BRD Generation feature.
 * 12 properties validating recursive exploration, KB caching,
 * tool detection, diagrams, prompt ordering, truncation, and filtering.
 */
class DeepBrdGenerationPropertyTest {

    // ── Property 1: Recursive exploration prompt completeness (Task 5.2) ──
    @Test fun `Property 1 - recursive exploration prompt completeness`() = runBlocking {
        checkAll(PTC, arbTicketId(), arbToolsWithJira()) { ticketId, tools ->
            val out = buildString { appendDataCollectionStrategy(ticketId, tools) }
            assertTrue(out.contains("visited", true), "Should mention visited set")
            assertTrue(out.contains("depth", true), "Should mention depth")
            assertTrue(out.contains("attachment", true), "Should mention attachments")
            assertTrue(out.contains("Parent") || out.contains("priority", true), "Should mention priority")
            assertTrue(out.contains("Stop when") || out.contains("early termination", true), "Should mention early termination")
            assertTrue(out.contains("summary only") || out.contains("Depth 2", true), "Should mention depth-based summarization")
        }
    }

    // ── Property 2: KB cache prompt completeness (Task 2.2) ──
    @Test fun `Property 2 - KB cache prompt completeness`() = runBlocking {
        checkAll(PTC, arbTicketId(), arbToolsWithKb()) { ticketId, tools ->
            val out = buildString { appendDataCollectionStrategy(ticketId, tools) }
            assertTrue(out.contains("KB", true), "Should mention KB")
            assertTrue(out.contains("Lookup", true) || out.contains("search", true), "Should mention lookup/search")
            assertTrue(out.contains("Fallback", true), "Should mention fallback")
            assertTrue(out.contains("Save", true) || out.contains("cache", true), "Should mention save/cache")
            val kbTool = tools.first { it.name.contains("kb_search", true) }
            assertTrue(out.contains(kbTool.name), "Should contain actual KB tool name: ${kbTool.name}")
        }
    }

    // ── Property 3: Tool detection correctness (Task 1.2) ──
    @Test fun `Property 3 - tool detection correctness`() = runBlocking {
        checkAll(PTC, arbToolsWithBoth()) { tools ->
            val detected = detectToolCategories(tools)
            assertTrue(detected.hasJiraTools, "Should detect Jira tools")
            assertTrue(detected.hasKbTools, "Should detect KB tools")
            assertTrue(detected.kbSearchTool != null, "Should detect KB search tool")
            assertTrue(detected.getIssueTool != null, "Should detect get_issue tool")
        }
    }

    // ── Property 4: Tool detection determinism (Task 1.3) ──
    @Test fun `Property 4 - tool detection determinism`() = runBlocking {
        checkAll(PTC, arbToolsWithBoth()) { tools ->
            val result1 = detectToolCategories(tools)
            val result2 = detectToolCategories(tools.shuffled())
            assertEquals(result1, result2, "Detection must be order-independent")
        }
    }

    // ── Property 5: KB fallback backward compatibility (Task 2.3) ──
    @Test fun `Property 5 - KB fallback backward compatibility`() = runBlocking {
        checkAll(PTC, arbTicketId(), arbToolsNoKb()) { ticketId, tools ->
            val out = buildString { appendDataCollectionStrategy(ticketId, tools) }
            assertFalse(out.contains("kb_search"), "Should NOT contain kb_search")
            assertFalse(out.contains("kb_read"), "Should NOT contain kb_read")
            assertFalse(out.contains("kb_ingest"), "Should NOT contain kb_ingest")
            assertTrue(out.contains("get_issue", true) || out.contains("Jira", true), "Should contain Jira instructions")
        }
    }

    // ── Property 6: No hardcoded tool names in prompt (Task 5.3) ──
    @Test fun `Property 6 - no hardcoded tool names in prompt`() = runBlocking {
        checkAll(PTC, arbTicketId(), arbToolsWithBoth()) { ticketId, tools ->
            val out = buildString { appendDataCollectionStrategy(ticketId, tools) }
            val toolNames = tools.map { it.name }.toSet()
            val backtickPattern = Regex("`([a-zA-Z_][a-zA-Z0-9_]*)`")
            backtickPattern.findAll(out).map { it.groupValues[1] }.forEach { ref ->
                if (ref.contains("_") && ref.length > 5) {
                    assertTrue(toolNames.contains(ref), "Referenced tool `$ref` must be in input tools")
                }
            }
        }
    }

    // ── Property 7: Draw.io XML template validity (Task 3.2) ──
    @Test fun `Property 7 - drawio XML template validity`() = runBlocking {
        val noTools = detectToolCategories(emptyList())
        checkAll(PTC, Arb.int(1..1)) { _ -> // deterministic, wrapped in property format
            val out = buildString { appendDiagramInstructions(noTools) }
            val xmlBlocks = Regex("```xml\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).findAll(out).toList()
            assertTrue(xmlBlocks.isNotEmpty(), "Should have at least 1 XML template")
            xmlBlocks.forEach { match ->
                val xml = match.groupValues[1].trim()
                assertTrue(xml.contains("<mxGraphModel>"), "Must have mxGraphModel root")
                val ids = Regex("""id="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
                assertEquals(ids.size, ids.distinct().size, "All IDs must be unique")
                Regex("""edge="1"[^>]*source="([^"]+)"[^>]*target="([^"]+)"""").findAll(xml).forEach { e ->
                    assertTrue(ids.contains(e.groupValues[1]), "Edge source ${e.groupValues[1]} must exist")
                    assertTrue(ids.contains(e.groupValues[2]), "Edge target ${e.groupValues[2]} must exist")
                }
            }
        }
    }

    // ── Property 8: Prompt section ordering (Task 6.3) ──
    @Test fun `Property 8 - prompt section ordering`() = runBlocking {
        checkAll(PTC, arbTicketId(), arbDocType(), arbToolsWithJira()) { ticketId, docType, tools ->
            val proxy = mockProxy(tools)
            val builder = AgenticPromptBuilder(proxy)
            val prompt = builder.buildInitialPrompt(ticketId, docType)
            val iStruct = prompt.indexOf("STRUCTURE")
            val iDiagram = prompt.indexOf("DIAGRAM")
            val iData = prompt.indexOf("DATA COLLECTION")
            assertTrue(iStruct >= 0 && iDiagram >= 0 && iData >= 0, "All sections must be present")
            assertTrue(iStruct < iDiagram, "STRUCTURE before DIAGRAM")
            assertTrue(iDiagram < iData, "DIAGRAM before DATA COLLECTION")
        }
    }

    // ── Property 9: Continuation prompt includes diagram instructions (Task 7.5) ──
    @Test fun `Property 9 - continuation includes diagram instructions`() = runBlocking {
        checkAll(PTC, arbTicketId(), arbDocType()) { ticketId, docType ->
            val tools = listOf(ToolDescriptor("mcp_jira_get_issue", "Get issue"))
            val proxy = mockProxy(tools)
            val builder = AgenticPromptBuilder(proxy)
            val out = builder.buildStatelessContinuation(ticketId, docType, listOf("result1"))
            assertTrue(out.contains("DIAGRAM") || out.contains("mxGraphModel"), "Should contain diagram instructions")
        }
    }

    // ── Property 10: Truncation preserves size limit and key data (Task 7.3) ──
    @Test fun `Property 10 - truncation preserves size limit and key data`() = runBlocking {
        checkAll(PTC, Arb.list(Arb.string(100..300, Codepoint.alphanumeric()), 3..8)) { results ->
            val maxChars = 500
            val truncated = truncateToolResults(results, maxChars)
            val totalLen = truncated.sumOf { it.length }
            assertTrue(totalLen <= maxChars || truncated.size <= 2, "Combined length should respect limit")
            assertEquals(results.first(), truncated.first(), "First element must be preserved")
            assertEquals(results.last(), truncated.last(), "Last element must be preserved")
            if (results.sumOf { it.length } > maxChars) {
                assertTrue(truncated.any { it.contains("TRUNCATED") }, "Should include truncation annotation")
            }
        }
    }

    // ── Property 11: Protected sections invariant (Task 7.4) ──
    @Test fun `Property 11 - protected sections invariant`() = runBlocking {
        checkAll(PTC, arbTicketId(), arbDocType()) { ticketId, docType ->
            val tools = listOf(ToolDescriptor("mcp_jira_get_issue", "Get issue"))
            val proxy = mockProxy(tools)
            val builder = AgenticPromptBuilder(proxy)
            val bigResults = List(5) { "x".repeat(20_000) }
            val out = builder.buildStatelessContinuation(ticketId, docType, bigResults)
            assertTrue(out.contains("CONTEXT") || out.contains("SYSTEM"), "Should contain context/system instructions")
            assertTrue(out.contains("AVAILABLE TOOLS"), "Should contain tool definitions")
            assertTrue(out.contains("TOOL PROTOCOL"), "Should contain tool protocol")
            assertTrue(out.contains("STRUCTURE"), "Should contain BRD structure")
            assertTrue(out.contains("DIAGRAM"), "Should contain diagram instructions")
        }
    }

    // ── Property 12: KB tools not excluded by filter (Task 7.6) ──
    @Test fun `Property 12 - KB tools not excluded by filter`() = runBlocking {
        val kbPatterns = listOf("kb_search", "kb_read", "kb_ingest", "kb_write", "kb_context", "kb_search_smart")
        checkAll(PTC, Arb.element(kbPatterns), Arb.string(3..8, Codepoint.az())) { pattern, prefix ->
            val tool = ToolDescriptor("mcp_${prefix}_$pattern", "KB tool")
            val filtered = filterExcludedTools(listOf(tool))
            assertTrue(filtered.contains(tool), "KB tool with pattern '$pattern' must not be excluded")
        }
    }

    // ── Shared config & generators ──────────────────────────────────────
    companion object { private val PTC = PropTestConfig(iterations = 20) }

    private fun arbTicketId(): Arb<String> = arbitrary {
        val prefix = Arb.string(2..4, Codepoint.az()).bind().uppercase()
        "$prefix-${Arb.int(1..9999).bind()}"
    }
    private fun arbDocType(): Arb<String> = Arb.of("BRD", "FSD", "TechnicalSpec")
    private fun arbToolsWithJira(): Arb<List<ToolDescriptor>> = arbitrary {
        val jira = ToolDescriptor("mcp_jira_get_issue", "Get Jira issue")
        (listOf(jira) + Arb.list(arbRandomTool(), 0..3).bind()).shuffled()
    }
    private fun arbToolsWithKb(): Arb<List<ToolDescriptor>> = arbitrary {
        val kb = ToolDescriptor("mcp_knowledge_base_kb_search_smart", "Smart KB search")
        val jira = ToolDescriptor("mcp_jira_get_issue", "Get Jira issue")
        (listOf(kb, jira) + Arb.list(arbRandomTool(), 0..2).bind()).shuffled()
    }
    private fun arbToolsWithBoth(): Arb<List<ToolDescriptor>> = arbitrary {
        val jira = ToolDescriptor("mcp_jira_get_issue", "Get Jira issue")
        val kb = ToolDescriptor("mcp_knowledge_base_kb_search_smart", "Smart KB search")
        (listOf(jira, kb) + Arb.list(arbRandomTool(), 0..2).bind()).shuffled()
    }
    private fun arbToolsNoKb(): Arb<List<ToolDescriptor>> = arbitrary {
        val jira = ToolDescriptor("mcp_jira_get_issue", "Get Jira issue")
        val search = ToolDescriptor("mcp_jira_search_jira", "Search Jira")
        (listOf(jira, search) + Arb.list(arbNonKbTool(), 0..2).bind()).shuffled()
    }
    private fun arbRandomTool(): Arb<ToolDescriptor> = arbitrary {
        ToolDescriptor("tool_${Arb.string(5..15, Codepoint.alphanumeric()).bind()}", "Random tool")
    }
    private fun arbNonKbTool(): Arb<ToolDescriptor> = arbitrary {
        ToolDescriptor("tool_${Arb.string(3..10, Codepoint.az()).bind()}", "Non-KB tool")
    }
    private fun mockProxy(tools: List<ToolDescriptor>): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(r: ToolCallRequest) = ToolCallResponse(r.id, true, "", "")
        override fun getAvailableToolDescriptors() = tools
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }
}
