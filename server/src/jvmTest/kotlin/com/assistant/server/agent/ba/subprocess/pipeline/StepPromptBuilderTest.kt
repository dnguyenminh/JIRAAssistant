package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext
import com.assistant.server.agent.ba.subprocess.pipeline.models.ToolCallOutcome
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for [StepPromptBuilder].
 *
 * Property 5: Step prompt structure invariant
 * Property 6: Step prompt data inclusion
 * Property 7: Step prompt size invariant
 */
@OptIn(ExperimentalKotest::class)
@Tag("multi-turn-ba-orchestration")
class StepPromptBuilderTest {

    private val forbiddenPatterns = listOf(
        "toolCall", "tool_name", "TOOL USAGE INSTRUCTIONS",
        "Available tools:", """{"toolCall":{"""
    )

    private val docTypes = listOf("BRD", "FSD")

    // ── Generators ──────────────────────────────────────

    private fun arbToolCallOutcome(toolName: String): Arb<ToolCallOutcome> =
        arbitrary {
            ToolCallOutcome(
                toolName = toolName,
                success = Arb.boolean().bind(),
                data = Arb.string(0..5000).bind(),
                durationMs = Arb.long(0L..10000L).bind(),
                errorMessage = if (Arb.boolean().bind())
                    Arb.string(0..100).bind() else null
            )
        }

    private fun arbCollectedContext(): Arb<CollectedContext> = arbitrary {
        val root = arbToolCallOutcome("mcp_jira_get_issue").bind()
        val linked = arbToolCallOutcome("mcp_jira_search").bind()
        val kb = arbToolCallOutcome("mcp_local_knowledge_base_get_ticket_info").bind()
        val deps = arbToolCallOutcome("mcp_local_knowledge_base_search_relationships").bind()
        CollectedContext(
            rootTicketData = root,
            linkedTicketsData = linked,
            kbAnalysisData = kb,
            dependenciesData = deps,
            toolCallLog = listOf(root, linked, kb, deps).map {
                ToolCallLogEntry(it.toolName, it.durationMs, it.success, it.data.length)
            }
        )
    }

    // ── Property 5: Step prompt structure invariant ──────

    /**
     * **Property 5: Step prompt structure invariant**
     *
     * For any CollectedContext and step type, prompt does NOT contain
     * forbidden patterns: "toolCall", "tool_name",
     * "TOOL USAGE INSTRUCTIONS", "Available tools:", or JSON tool call format.
     *
     * **Validates: Requirements 3.1, 3.2, 5.2**
     */
    @Test
    fun `Property 5 - prompts never contain forbidden tool patterns`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 100), arbCollectedContext()) { ctx ->
            for (docType in docTypes) {
                val analysis = StepPromptBuilder.buildAnalysisPrompt(ctx, docType)
                val requirements = StepPromptBuilder.buildRequirementsPrompt(
                    "sample analysis", ctx.linkedTicketsData.data
                )
                val writing = StepPromptBuilder.buildWritingPrompt(
                    "accumulated results", docType
                )
                val review = StepPromptBuilder.buildReviewPrompt(
                    "feedback text", "current document"
                )

                val allPrompts = listOf(
                    "analysis" to analysis,
                    "requirements" to requirements,
                    "writing" to writing,
                    "review" to review
                )
                for ((name, prompt) in allPrompts) {
                    for (pattern in forbiddenPatterns) {
                        assertFalse(
                            prompt.contains(pattern),
                            "$name prompt for $docType contains forbidden pattern: '$pattern'"
                        )
                    }
                }
            }
        }
    }

    // ── Property 6: Step prompt data inclusion ──────────

    /**
     * **Property 6: Step prompt data inclusion**
     *
     * For any CollectedContext with ticket data D, analysis prompt contains D
     * (or truncated prefix). For any analysis result A, requirements prompt
     * contains A. For any accumulated results R, writing prompt contains R.
     *
     * **Validates: Requirements 3.3, 3.4, 3.5**
     */
    @Test
    fun `Property 6 - prompts include relevant input data`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 100), arbCollectedContext()) { ctx ->
            // Analysis prompt includes ticket data (or truncated prefix)
            val analysisPrompt = StepPromptBuilder.buildAnalysisPrompt(ctx, "BRD")
            val ticketData = ctx.rootTicketData.data
            if (ticketData.isNotEmpty()) {
                val prefix = ticketData.take(minOf(ticketData.length, 8000))
                assertTrue(
                    analysisPrompt.contains(prefix),
                    "Analysis prompt must contain ticket data (or truncated prefix)"
                )
            }
        }
    }

    @Test
    fun `Property 6b - requirements prompt includes analysis result`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 100), Arb.string(1..4000)) { analysisResult ->
            val prompt = StepPromptBuilder.buildRequirementsPrompt(
                analysisResult, "linked data"
            )
            assertTrue(
                prompt.contains(analysisResult),
                "Requirements prompt must contain analysis result"
            )
        }
    }

    @Test
    fun `Property 6c - writing prompt includes accumulated results`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 100), Arb.string(1..4000)) { accumulated ->
            val prompt = StepPromptBuilder.buildWritingPrompt(accumulated, "BRD")
            assertTrue(
                prompt.contains(accumulated),
                "Writing prompt must contain accumulated results"
            )
        }
    }

    // ── Property 7: Step prompt size invariant ──────────

    /**
     * **Property 7: Step prompt size invariant**
     *
     * For any input data of any size (including very large strings),
     * prompt has ≤200 lines. Builder truncates data if too large.
     *
     * **Validates: Requirements 3.6**
     */
    @Test
    fun `Property 7 - prompts never exceed 200 lines`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..50000)) { largeData ->
            val ctx = CollectedContext(
                rootTicketData = ToolCallOutcome("mcp_jira_get_issue", true, largeData, 100),
                linkedTicketsData = ToolCallOutcome("mcp_jira_search", true, largeData, 100),
                kbAnalysisData = ToolCallOutcome("mcp_local_knowledge_base_get_ticket_info", true, largeData, 100),
                dependenciesData = ToolCallOutcome("mcp_local_knowledge_base_search_relationships", true, largeData, 100),
                toolCallLog = emptyList()
            )

            for (docType in docTypes) {
                val analysis = StepPromptBuilder.buildAnalysisPrompt(ctx, docType)
                val requirements = StepPromptBuilder.buildRequirementsPrompt(largeData, largeData)
                val writing = StepPromptBuilder.buildWritingPrompt(largeData, docType)
                val review = StepPromptBuilder.buildReviewPrompt(largeData, largeData)

                val allPrompts = listOf(
                    "analysis" to analysis,
                    "requirements" to requirements,
                    "writing" to writing,
                    "review" to review
                )
                for ((name, prompt) in allPrompts) {
                    val lineCount = prompt.lines().size
                    assertTrue(
                        lineCount <= 200,
                        "$name prompt for $docType has $lineCount lines (max 200)"
                    )
                }
            }
        }
    }
}
