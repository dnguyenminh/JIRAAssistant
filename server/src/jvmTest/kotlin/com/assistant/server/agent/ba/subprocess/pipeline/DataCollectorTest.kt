package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.pipeline.DataCollector.Companion.TOOL_JIRA_GET_ISSUE
import com.assistant.server.agent.ba.subprocess.pipeline.DataCollector.Companion.TOOL_JIRA_SEARCH
import com.assistant.server.agent.ba.subprocess.pipeline.DataCollector.Companion.TOOL_KB_GET_TICKET_INFO
import com.assistant.server.agent.ba.subprocess.pipeline.DataCollector.Companion.TOOL_KB_SEARCH_RELATIONSHIPS
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for [DataCollector].
 *
 * Property 1: Data collection completeness
 * Property 2: Data collection resilience
 */
@OptIn(ExperimentalKotest::class)
@Tag("multi-turn-ba-orchestration")
class DataCollectorTest {

    private val allToolNames = listOf(
        TOOL_JIRA_GET_ISSUE,
        TOOL_JIRA_SEARCH,
        TOOL_KB_GET_TICKET_INFO,
        TOOL_KB_SEARCH_RELATIONSHIPS
    )

    /**
     * Generates ticket IDs like "PROJ-123".
     */
    private val arbTicketId: Arb<String> = Arb.string(
        minSize = 2, maxSize = 6, codepoints = Codepoint.alphanumeric()
    ).filter { it.isNotBlank() }

    // ── Property 1: Data collection completeness ────────────

    /**
     * **Property 1: Data collection completeness**
     *
     * For any valid rootTicketId, CollectedContext contains exactly
     * 4 ToolCallOutcome entries for the 4 MCP tools, and toolCallLog
     * has exactly 4 entries.
     *
     * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.6**
     */
    @Test
    fun `Property 1 - collectData returns 4 outcomes for 4 MCP tools`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 100), arbTicketId) { ticketId ->
            val collector = DataCollector(successProxy(), NoOpReporter)
            val ctx = collector.collectData(ticketId)

            // Verify 4 distinct tool outcomes
            assertEquals(TOOL_JIRA_GET_ISSUE, ctx.rootTicketData.toolName)
            assertEquals(TOOL_JIRA_SEARCH, ctx.linkedTicketsData.toolName)
            assertEquals(TOOL_KB_GET_TICKET_INFO, ctx.kbAnalysisData.toolName)
            assertEquals(TOOL_KB_SEARCH_RELATIONSHIPS, ctx.dependenciesData.toolName)

            // Verify toolCallLog has exactly 4 entries
            assertEquals(4, ctx.toolCallLog.size)
            val loggedTools = ctx.toolCallLog.map { it.toolName }
            assertTrue(loggedTools.containsAll(allToolNames),
                "Log must contain all 4 tool names, got: $loggedTools")

            // All should succeed with this proxy
            assertTrue(ctx.rootTicketData.success)
            assertTrue(ctx.linkedTicketsData.success)
            assertTrue(ctx.kbAnalysisData.success)
            assertTrue(ctx.dependenciesData.success)
            assertEquals(4, ctx.successCount)
        }
    }

    // ── Property 2: Data collection resilience ──────────────

    /**
     * **Property 2: Data collection resilience**
     *
     * For any combination of success/failure of 4 MCP tools
     * (2^4 = 16 cases), collectData() always returns CollectedContext,
     * never throws. Successful tools have success=true with non-empty
     * data; failed tools have success=false with non-null errorMessage.
     *
     * **Validates: Requirements 1.5, 1.6**
     */
    @Test
    fun `Property 2 - collectData never throws regardless of tool failures`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            arbTicketId,
            Arb.boolean(), // jira get issue success
            Arb.boolean(), // jira search success
            Arb.boolean(), // kb get ticket info success
            Arb.boolean()  // kb search relationships success
        ) { ticketId, s1, s2, s3, s4 ->
            val flags = listOf(s1, s2, s3, s4)
            val proxy = selectiveProxy(flags)
            val collector = DataCollector(proxy, NoOpReporter)

            // Must never throw
            val ctx = collector.collectData(ticketId)

            // Verify each outcome matches its flag
            verifyOutcome(ctx.rootTicketData, s1, TOOL_JIRA_GET_ISSUE)
            verifyOutcome(ctx.linkedTicketsData, s2, TOOL_JIRA_SEARCH)
            verifyOutcome(ctx.kbAnalysisData, s3, TOOL_KB_GET_TICKET_INFO)
            verifyOutcome(ctx.dependenciesData, s4, TOOL_KB_SEARCH_RELATIONSHIPS)

            // toolCallLog always has 4 entries
            assertEquals(4, ctx.toolCallLog.size)
            assertEquals(flags.count { it }, ctx.successCount)
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun verifyOutcome(
        outcome: com.assistant.server.agent.ba.subprocess.pipeline.models.ToolCallOutcome,
        expectedSuccess: Boolean,
        expectedToolName: String
    ) {
        assertEquals(expectedToolName, outcome.toolName)
        assertEquals(expectedSuccess, outcome.success)
        if (expectedSuccess) {
            assertTrue(outcome.data.isNotEmpty(),
                "Successful tool '$expectedToolName' must have non-empty data")
        } else {
            assertNotNull(outcome.errorMessage,
                "Failed tool '$expectedToolName' must have non-null errorMessage")
        }
    }

    /**
     * Proxy that succeeds for all tool calls.
     */
    private fun successProxy() = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(
            id = request.id,
            success = true,
            data = """{"result":"data for ${request.name}"}"""
        )
        override fun getAvailableToolDescriptors() = emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    /**
     * Proxy that succeeds or throws based on per-tool flags.
     * Flag order: [jiraGetIssue, jiraSearch, kbGetTicket, kbSearchRel]
     */
    private fun selectiveProxy(flags: List<Boolean>): SubprocessProxy {
        val toolFlagMap = allToolNames.zip(flags).toMap()
        return object : SubprocessProxy {
            override suspend fun handleToolCallRequest(
                request: ToolCallRequest
            ): ToolCallResponse {
                val shouldSucceed = toolFlagMap[request.name] ?: true
                if (!shouldSucceed) {
                    throw RuntimeException("Simulated failure for ${request.name}")
                }
                return ToolCallResponse(
                    id = request.id,
                    success = true,
                    data = """{"result":"data for ${request.name}"}"""
                )
            }
            override fun getAvailableToolDescriptors() = emptyList<ToolDescriptor>()
            override fun buildToolListMessage() = ""
            override fun buildToolsUpdatedMessage() = ""
        }
    }
}

/** No-op ProgressReporter for unit tests. */
private object NoOpReporter : ProgressReporter {
    override suspend fun reportPhase(
        phaseName: String, phaseIndex: Int, totalPhases: Int
    ) = Unit
    override suspend fun reportProgress(percent: Int, message: String) = Unit
    override suspend fun reportToolCall(toolName: String, status: String) = Unit
}
