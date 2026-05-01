package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext
import com.assistant.server.agent.ba.subprocess.pipeline.models.StepResponse
import com.assistant.server.agent.ba.subprocess.pipeline.models.ToolCallOutcome
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for [DocumentAssembler].
 *
 * Property 8: Document assembly correctness
 * Property 9: Metrics mapping correctness
 */
@OptIn(ExperimentalKotest::class)
@Tag("multi-turn-ba-orchestration")
class DocumentAssemblerTest {

    private val assembler = DocumentAssembler()

    // ── Generators ──────────────────────────────────────

    private val arbToolCallLogEntry: Arb<ToolCallLogEntry> = arbitrary {
        ToolCallLogEntry(
            toolName = Arb.string(3..30).bind(),
            durationMs = Arb.long(0L..5000L).bind(),
            success = Arb.boolean().bind(),
            resultSizeChars = Arb.int(0..10000).bind()
        )
    }

    private fun arbCollectedContext(
        logSize: Arb<Int> = Arb.int(1..8)
    ): Arb<CollectedContext> = arbitrary {
        val n = logSize.bind()
        val entries = List(n) { arbToolCallLogEntry.bind() }
        CollectedContext(
            rootTicketData = arbOutcome("mcp_jira_get_issue"),
            linkedTicketsData = arbOutcome("mcp_jira_search"),
            kbAnalysisData = arbOutcome("mcp_kb_get_ticket_info"),
            dependenciesData = arbOutcome("mcp_kb_search_relationships"),
            toolCallLog = entries
        )
    }

    private fun arbOutcome(name: String) = ToolCallOutcome(
        toolName = name, success = true, data = "data", durationMs = 50L
    )

    private val arbStepResponse: Arb<StepResponse> = arbitrary {
        StepResponse(
            stepName = Arb.string(3..20).bind(),
            content = Arb.string(0..500).bind(),
            durationMs = Arb.long(0L..5000L).bind(),
            timedOut = Arb.boolean().bind(),
            isEmpty = Arb.boolean().bind()
        )
    }

    private val arbNonEmptyStepResponse: Arb<StepResponse> = arbitrary {
        StepResponse(
            stepName = Arb.string(3..20).bind(),
            content = Arb.string(5..500).bind(),
            durationMs = Arb.long(0L..5000L).bind(),
            timedOut = false,
            isEmpty = false
        )
    }

    // ── Property 8: Document assembly correctness ───────

    /**
     * **Property 8: Document assembly correctness**
     *
     * For any non-empty list of StepResponses and CollectedContext:
     * - document = content of last StepResponse with non-empty content
     * - status is a valid BATaskStatus
     * - totalDurationMs >= 0
     *
     * **Validates: Requirements 6.1, 6.3**
     */
    @Test
    fun `Property 8 - assemble returns correct document and valid status`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(arbStepResponse, 1..6),
            arbCollectedContext()
        ) { responses, context ->
            val startTime = System.currentTimeMillis() - 100
            val result = assembler.assemble(responses, context, startTime)

            val lastNonEmpty = responses.lastOrNull { it.content.isNotBlank() }
            if (lastNonEmpty != null) {
                assertEquals(
                    lastNonEmpty.content, result.document,
                    "Document must be content of last non-empty response"
                )
            } else {
                assertEquals("", result.document, "No non-empty response → empty document")
                assertEquals(BATaskStatus.FAILED, result.status)
            }

            assertTrue(
                result.status in BATaskStatus.entries,
                "Status must be a valid BATaskStatus"
            )
            assertTrue(
                result.totalDurationMs >= 0,
                "totalDurationMs must be >= 0"
            )
        }
    }

    @Test
    fun `Property 8b - all empty responses produce FAILED status`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            arbCollectedContext()
        ) { context ->
            val emptyResponses = listOf(
                StepResponse("s1", "", 100L),
                StepResponse("s2", "   ", 200L)
            )
            val startTime = System.currentTimeMillis() - 50
            val result = assembler.assemble(emptyResponses, context, startTime)
            assertEquals(BATaskStatus.FAILED, result.status)
            assertEquals("", result.document)
        }
    }

    // ── Property 9: Metrics mapping correctness ─────────

    /**
     * **Property 9: Metrics mapping correctness**
     *
     * For any CollectedContext with N tool calls (M success, F failed):
     * - toolCallsExecuted == N
     * - toolCallsFailed == F
     * - toolCallLog has exactly N entries
     *
     * **Validates: Requirements 7.3, 7.4**
     */
    @Test
    fun `Property 9 - metrics map correctly from CollectedContext`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            arbCollectedContext(Arb.int(0..12)),
            arbNonEmptyStepResponse
        ) { context, response ->
            val startTime = System.currentTimeMillis() - 100
            val result = assembler.assemble(listOf(response), context, startTime)

            val expectedN = context.toolCallLog.size
            val expectedF = context.toolCallLog.count { !it.success }

            assertEquals(
                expectedN, result.toolCallsExecuted,
                "toolCallsExecuted must equal toolCallLog.size"
            )
            assertEquals(
                expectedF, result.toolCallsFailed,
                "toolCallsFailed must equal count of failed entries"
            )
            assertEquals(
                expectedN, result.toolCallLog.size,
                "toolCallLog must have exactly N entries"
            )

            // Verify each entry matches
            for (i in context.toolCallLog.indices) {
                assertEquals(context.toolCallLog[i].toolName, result.toolCallLog[i].toolName)
                assertEquals(context.toolCallLog[i].success, result.toolCallLog[i].success)
            }
        }
    }
}
