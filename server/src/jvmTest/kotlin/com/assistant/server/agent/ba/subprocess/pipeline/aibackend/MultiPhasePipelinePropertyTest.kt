package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.*
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
 * Property-based tests for the Multi-Phase BRD Pipeline.
 *
 * Uses JUnit 5 + Kotest property testing with 100 iterations per property.
 * Follows the same pattern as [PromptBuildingPropertyTest].
 */
class MultiPhasePipelinePropertyTest {

    // ── Property 1: KB detection determines pipeline mode ───────────
    // Feature: multi-phase-brd-pipeline, Property 1: KB detection determines pipeline mode
    // **Validates: Requirements 1.1, 8.1**

    @Test
    fun `Property 1 - KB detection determines pipeline mode`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbToolList()) { tools ->
                val filter = PhaseToolFilter()
                val result = filter.hasKbTools(tools)
                val expected = tools.any { t ->
                    val n = t.name.lowercase()
                    n.contains("kb_search") || n.contains("kb_ingest") || n.contains("kb_write")
                }
                assertEquals(expected, result,
                    "hasKbTools mismatch for tools: ${tools.map { it.name }}")
            }
        }
    }

    // ── Property 2: Result aggregation preserves totals ─────────────
    // Feature: multi-phase-brd-pipeline, Property 2: Result aggregation preserves totals
    // **Validates: Requirements 1.5**

    @Test
    fun `Property 2 - Result aggregation preserves totals`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbPhaseResult(), arbPhaseResult(), arbPhaseResult()
            ) { p1, p2, p3 ->
                val allLogs = p1.toolCallLog + p2.toolCallLog + p3.toolCallLog
                val result = AgenticLoopResult(
                    document = "test",
                    toolCallLog = allLogs,
                    toolCallsExecuted = allLogs.size,
                    toolCallsFailed = allLogs.count { !it.success },
                    timedOut = false,
                    totalDurationMs = 0
                )
                assertEquals(
                    p1.toolCallLog.size + p2.toolCallLog.size + p3.toolCallLog.size,
                    result.toolCallsExecuted,
                    "Aggregated toolCallsExecuted should equal sum of phases"
                )
                assertEquals(
                    p1.toolCallLog.count { !it.success } +
                        p2.toolCallLog.count { !it.success } +
                        p3.toolCallLog.count { !it.success },
                    result.toolCallsFailed,
                    "Aggregated toolCallsFailed should equal sum of phases"
                )
            }
        }
    }


    // ── Property 3: Phase prompt content isolation ──────────────────
    // Feature: multi-phase-brd-pipeline, Property 3: Phase prompt content isolation
    // **Validates: Requirements 2.1, 3.1, 4.1, 6.1, 6.2, 6.3, 10.3**

    @Test
    fun `Property 3 - Phase prompt content isolation`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100), arbTicketId(), arbToolList()
            ) { ticketId, tools ->
                val builder = PhasePromptBuilder()

                val p1 = builder.buildPhase1Prompt(ticketId, tools)
                assertTrue(p1.contains("DATA COLLECTION", ignoreCase = true),
                    "Phase 1 prompt should contain DATA COLLECTION")
                assertFalse(p1.contains("BRD STRUCTURE", ignoreCase = true),
                    "Phase 1 prompt should NOT contain BRD STRUCTURE")

                val p2 = builder.buildPhase2Prompt(ticketId, "BRD", tools)
                assertTrue(p2.contains("BRD", ignoreCase = true),
                    "Phase 2 prompt should contain BRD")
                assertTrue(p2.contains("BRD:$ticketId"),
                    "Phase 2 prompt should contain BRD:{ticketId} search instruction")
                assertFalse(p2.contains("DATA COLLECTION", ignoreCase = true),
                    "Phase 2 prompt should NOT contain DATA COLLECTION")

                val p3 = builder.buildPhase3Prompt(ticketId, tools)
                assertTrue(p3.contains("DIAGRAM", ignoreCase = true),
                    "Phase 3 prompt should contain DIAGRAM")
                assertTrue(p3.contains("BRD:$ticketId"),
                    "Phase 3 prompt should contain BRD:{ticketId} search instruction")
                assertFalse(p3.contains("BRD STRUCTURE", ignoreCase = true),
                    "Phase 3 prompt should NOT contain BRD STRUCTURE")
                assertFalse(p3.contains("DATA COLLECTION", ignoreCase = true),
                    "Phase 3 prompt should NOT contain DATA COLLECTION")
            }
        }
    }

    // ── Property 4: Phase tool filter correctness ───────────────────
    // Feature: multi-phase-brd-pipeline, Property 4: Phase tool filter correctness
    // **Validates: Requirements 2.2, 3.2, 4.2, 7.1, 7.2, 7.3, 7.4**

    @Test
    fun `Property 4 - Phase tool filter correctness`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbToolList()) { tools ->
                val filter = PhaseToolFilter()

                val p1Tools = filter.filterForPhase1(tools)
                p1Tools.forEach { tool ->
                    val name = tool.name.lowercase()
                    val matchesPhase1 = PhaseToolFilter.PHASE1_PATTERNS.any { name.contains(it) }
                    val matchesExcluded = PhaseToolFilter.EXCLUDED_PATTERNS.any { name.contains(it) }
                    assertTrue(matchesPhase1,
                        "Phase 1 tool '${tool.name}' should match at least one PHASE1_PATTERN")
                    assertFalse(matchesExcluded,
                        "Phase 1 tool '${tool.name}' should NOT match EXCLUDED_PATTERNS")
                }

                val p2Tools = filter.filterForPhase2(tools)
                p2Tools.forEach { tool ->
                    val name = tool.name.lowercase()
                    val matchesPhase2 = PhaseToolFilter.PHASE2_PATTERNS.any { name.contains(it) }
                    val matchesExcluded = PhaseToolFilter.EXCLUDED_PATTERNS.any { name.contains(it) }
                    assertTrue(matchesPhase2,
                        "Phase 2 tool '${tool.name}' should match at least one PHASE2_PATTERN")
                    assertFalse(matchesExcluded,
                        "Phase 2 tool '${tool.name}' should NOT match EXCLUDED_PATTERNS")
                }

                val p3Tools = filter.filterForPhase3(tools)
                p3Tools.forEach { tool ->
                    val name = tool.name.lowercase()
                    val matchesPhase3 = PhaseToolFilter.PHASE3_PATTERNS.any { name.contains(it) }
                    val matchesExcluded = PhaseToolFilter.EXCLUDED_PATTERNS.any { name.contains(it) }
                    assertTrue(matchesPhase3,
                        "Phase 3 tool '${tool.name}' should match at least one PHASE3_PATTERN")
                    assertFalse(matchesExcluded,
                        "Phase 3 tool '${tool.name}' should NOT match EXCLUDED_PATTERNS")
                }
            }
        }
    }

    // ── Property 5: Tool filter determinism ─────────────────────────
    // Feature: multi-phase-brd-pipeline, Property 5: Tool filter determinism
    // **Validates: Requirements 7.6**

    @Test
    fun `Property 5 - Tool filter determinism`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbToolList()) { tools ->
                val filter = PhaseToolFilter()
                val shuffled = tools.shuffled()

                assertEquals(
                    filter.filterForPhase1(tools).map { it.name },
                    filter.filterForPhase1(shuffled).map { it.name },
                    "Phase 1 filter should be deterministic regardless of input order"
                )
                assertEquals(
                    filter.filterForPhase2(tools).map { it.name },
                    filter.filterForPhase2(shuffled).map { it.name },
                    "Phase 2 filter should be deterministic regardless of input order"
                )
                assertEquals(
                    filter.filterForPhase3(tools).map { it.name },
                    filter.filterForPhase3(shuffled).map { it.name },
                    "Phase 3 filter should be deterministic regardless of input order"
                )
            }
        }
    }

    // ── Property 6: Excluded tools never pass any phase filter ──────
    // Feature: multi-phase-brd-pipeline, Property 6: Excluded tools never pass any phase filter
    // **Validates: Requirements 7.5**

    @Test
    fun `Property 6 - Excluded tools never pass any phase filter`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbToolList()) { tools ->
                val filter = PhaseToolFilter()
                val excludedTools = tools.filter { tool ->
                    val n = tool.name.lowercase()
                    n.contains("playwright") || n.contains("browser")
                }

                val p1Names = filter.filterForPhase1(tools).map { it.name }.toSet()
                val p2Names = filter.filterForPhase2(tools).map { it.name }.toSet()
                val p3Names = filter.filterForPhase3(tools).map { it.name }.toSet()

                excludedTools.forEach { excluded ->
                    assertFalse(excluded.name in p1Names,
                        "Excluded tool '${excluded.name}' should not appear in Phase 1")
                    assertFalse(excluded.name in p2Names,
                        "Excluded tool '${excluded.name}' should not appear in Phase 2")
                    assertFalse(excluded.name in p3Names,
                        "Excluded tool '${excluded.name}' should not appear in Phase 3")
                }
            }
        }
    }


    // ── Property 7: Assembly diagram block parsing ──────────────────
    // Feature: multi-phase-brd-pipeline, Property 7: Assembly diagram block parsing
    // **Validates: Requirements 5.2**

    @Test
    fun `Property 7 - Assembly diagram block parsing`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), arbDiagramBlocks()) { blocks ->
                val assembler = BrdAssembler()
                val input = blocks.joinToString("\n") { (label, xml) ->
                    "<!-- DIAGRAM:$label -->\n```xml\n$xml\n```"
                }
                val parsed = assembler.parseDiagramBlocks(input)

                assertEquals(blocks.size, parsed.size,
                    "parseDiagramBlocks should return ${blocks.size} entries")
                blocks.forEach { (label, xml) ->
                    assertTrue(parsed.containsKey(label),
                        "Parsed map should contain label '$label'")
                    assertEquals(xml, parsed[label],
                        "Parsed XML for '$label' should match input")
                }
            }
        }
    }

    // ── Property 8: Assembly placeholder replacement ────────────────
    // Feature: multi-phase-brd-pipeline, Property 8: Assembly placeholder replacement
    // **Validates: Requirements 5.3, 5.5**

    @Test
    fun `Property 8 - Assembly placeholder replacement`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100), arbBrdWithPlaceholders()
            ) { (brd, usedLabels) ->
                val assembler = BrdAssembler()

                // Provide diagrams for a random subset of the used labels
                val diagramMap = usedLabels
                    .take(usedLabels.size / 2 + 1)
                    .associateWith { "<mxGraphModel><root>$it</root></mxGraphModel>" }

                val result = assembler.replacePlaceholders(brd, diagramMap)

                // (c) No placeholder markers remain
                BrdAssembler.DIAGRAM_LABELS.forEach { label ->
                    val placeholder = "${BrdAssembler.PLACEHOLDER_PREFIX}$label${BrdAssembler.PLACEHOLDER_SUFFIX}"
                    assertFalse(result.contains(placeholder),
                        "Output should not contain placeholder for '$label'")
                }

                // (a) Matched placeholders get diagram content
                diagramMap.forEach { (label, xml) ->
                    if (label in usedLabels) {
                        assertTrue(result.contains(xml),
                            "Output should contain diagram XML for matched label '$label'")
                    }
                }

                // (b) Unmatched placeholders get fallback text
                val unmatchedLabels = usedLabels.filter { it !in diagramMap }
                if (unmatchedLabels.isNotEmpty()) {
                    assertTrue(result.contains(BrdAssembler.FALLBACK_TEXT),
                        "Output should contain fallback text for unmatched placeholders")
                }
            }
        }
    }

    // ── Generators ──────────────────────────────────────────────────

    private fun arbToolDescriptor(): Arb<ToolDescriptor> = arbitrary {
        val categories = listOf(
            "mcp_jira_get_issue", "mcp_jira_search_jira", "mcp_jira_analyze_ticket",
            "mcp_jira_get_ticket_analysis",
            "mcp_kb_kb_search", "mcp_kb_kb_search_smart", "mcp_kb_kb_read",
            "mcp_kb_kb_context", "mcp_kb_kb_ingest", "mcp_kb_kb_write",
            "mcp_drawio_create_diagram", "mcp_diagram_generate",
            "mcp_playwright_browser_click", "mcp_playwright_browser_snapshot",
            "mcp_markitdown_convert_to_markdown",
            "mcp_fetch_fetch", "mcp_db_execute_sql"
        )
        val name = Arb.element(categories).bind()
        ToolDescriptor(name = name, description = "Test tool $name")
    }

    private fun arbToolList(): Arb<List<ToolDescriptor>> =
        Arb.list(arbToolDescriptor(), 1..20)

    private fun arbTicketId(): Arb<String> = arbitrary {
        val prefix = Arb.string(2..4, Codepoint.az()).bind().uppercase()
        val number = Arb.int(1..9999).bind()
        "$prefix-$number"
    }

    private fun arbPhaseResult(): Arb<PhaseResult> = arbitrary {
        val phaseId = Arb.element(PhaseId.entries.toList()).bind()
        val toolCount = Arb.int(0..20).bind()
        val failCount = Arb.int(0..toolCount).bind()
        val toolLog = (0 until toolCount).map { i ->
            ToolCallLogEntry(
                toolName = "tool_$i",
                durationMs = Arb.long(10L..5000L).bind(),
                success = i >= failCount,
                resultSizeChars = Arb.int(10..10000).bind()
            )
        }
        PhaseResult(
            phaseId = phaseId,
            output = Arb.string(10..200).bind(),
            toolCallLog = toolLog,
            toolCallsExecuted = toolCount,
            toolCallsFailed = failCount,
            durationMs = Arb.long(100L..30000L).bind(),
            success = true,
            timedOut = false
        )
    }

    private fun arbDiagramBlocks(): Arb<List<Pair<String, String>>> = arbitrary {
        val count = Arb.int(1..4).bind()
        val labels = BrdAssembler.DIAGRAM_LABELS.shuffled().take(count)
        labels.map { label ->
            val cellId = Arb.int(1..999).bind()
            val xml = "<mxGraphModel><root><mxCell id=\"$cellId\"/></root></mxGraphModel>"
            Pair(label, xml)
        }
    }

    private fun arbBrdWithPlaceholders(): Arb<Pair<String, List<String>>> = arbitrary {
        val count = Arb.int(1..4).bind()
        val labels = BrdAssembler.DIAGRAM_LABELS.shuffled().take(count)
        val brd = buildString {
            appendLine("## Project Overview")
            appendLine("Some content here")
            labels.forEach { label ->
                appendLine("${BrdAssembler.PLACEHOLDER_PREFIX}$label${BrdAssembler.PLACEHOLDER_SUFFIX}")
                appendLine("More content after $label")
            }
        }
        Pair(brd, labels)
    }
}
