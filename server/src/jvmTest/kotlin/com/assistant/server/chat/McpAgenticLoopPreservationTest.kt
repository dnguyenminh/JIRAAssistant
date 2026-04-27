package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatResponse
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Preservation Property Tests — Non-Internal Tool Call Behavior Unchanged.
 *
 * These tests MUST PASS on UNFIXED code — they capture baseline behavior
 * that must be preserved after the internal-tool-routing fix.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 */
@OptIn(ExperimentalKotest::class)
class McpAgenticLoopPreservationTest {

    private lateinit var permRepo: InMemoryPermissionRepo
    private lateinit var permService: UserToolPermissionService

    @BeforeEach
    fun setUp() {
        permRepo = InMemoryPermissionRepo()
        permService = UserToolPermissionService(permRepo, InMemoryMcpServerRepo())
    }

    private fun toResponse(r: AIResult, len: Int) =
        ChatResponse(reply = (r as AIResult.Success).response)

    private fun toolCallJson(sid: String, tool: String) =
        """{"mcpToolCall":{"serverId":"$sid","toolName":"$tool","arguments":{}}}"""

    /** Property 2a: External tool calls route via getClient and return response.
     *  **Validates: Requirements 3.2** */
    @Test
    fun `Preservation - external tool calls route via getClient`() {
        val arbSid = Arb.element(listOf("jira-mcp", "confluence-mcp", "custom-srv"))
        val arbTool = Arb.element(listOf("search_issues", "get_page", "run_query"))
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbSid, arbTool) { sid, tool ->
                val expected = "result-$sid-$tool"
                val mgr = TrackingClientManager(expected)
                var count = 0; var round2: String? = null
                McpAgenticLoop.execute(
                    initialPrompt = "use $tool",
                    mcpProcessManager = mgr,
                    callAI = {
                        count++
                        if (count == 1) AIResult.Success(toolCallJson(sid, tool))
                        else { round2 = it; AIResult.Success("done") }
                    },
                    toResponse = ::toResponse
                )
                assertTrue(mgr.getClientCalled, "getClient must be called for '$sid'")
                assertEquals(sid, mgr.getClientServerId)
                assertNotNull(round2, "Round 2 should be called")
                assertTrue(round2!!.contains(expected), "Must contain client response")
            }
        }
    }

    /** Property 2b: Local KB tool calls route via LocalKBToolExecutor.
     *  **Validates: Requirements 3.3** */
    @Test
    fun `Preservation - local KB calls route via LocalKBToolExecutor`() {
        runBlocking {
            val mgr = TrackingClientManager("unused")
            val exec = LocalKBToolExecutor(
                PreservationStubEmbedding, PreservationStubVectorStore, PreservationStubKBRepo
            )
            var count = 0; var round2: String? = null
            McpAgenticLoop.execute(
                initialPrompt = "search kb",
                mcpProcessManager = mgr,
                callAI = {
                    count++
                    if (count == 1) AIResult.Success(
                        toolCallJson(LocalKBToolExecutor.SERVER_ID, "search_knowledge")
                    )
                    else { round2 = it; AIResult.Success("KB done") }
                },
                toResponse = ::toResponse,
                localKBExecutor = exec
            )
            assertFalse(mgr.getClientCalled, "getClient must NOT be called for local-kb")
            assertNotNull(round2, "Round 2 should be called")
            // Empty arguments → "Tool error: missing 'query'" from executor
            val r2 = round2!!
            assertTrue(
                r2.contains("Tool error") || r2.contains("missing"),
                "Must contain KB executor response: $r2"
            )
        }
    }

    /** Property 2c: Non-tool-call AI responses return text directly.
     *  **Validates: Requirements 3.4** */
    @Test
    fun `Preservation - non-tool-call responses return text directly`() {
        val arbText = Arb.element(listOf(
            "Here is your answer about the project.",
            "The analysis shows 5 critical issues.",
            "No tools needed for this response.",
            "Summary: all tests passed successfully."
        ))
        runBlocking {
            checkAll(PropTestConfig(iterations = 15), arbText) { text ->
                var count = 0
                val resp = McpAgenticLoop.execute(
                    initialPrompt = "tell me something",
                    mcpProcessManager = null,
                    callAI = { count++; AIResult.Success(text) },
                    toResponse = ::toResponse
                )
                assertEquals(1, count, "AI should be called exactly once")
                assertEquals(text, resp.reply, "Response must be AI text directly")
            }
        }
    }

    /** Property 2d: Per-user disabled tool returns disabled message.
     *  **Validates: Requirements 3.5, 3.6** */
    @Test
    fun `Preservation - disabled tool returns disabled message`() {
        val arbSid = Arb.element(listOf("jira-mcp", "confluence-mcp", "custom-srv"))
        val arbTool = Arb.element(listOf("search_issues", "get_page", "run_query"))
        val arbUser = Arb.string(3..8, Codepoint.az())
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 15), arbUser, arbSid, arbTool
            ) { userId, sid, tool ->
                permRepo.clear()
                permService.savePermissions(userId, mapOf("$sid::$tool" to "disabled"))
                val mgr = TrackingClientManager("should-not-reach")
                var count = 0; var round2: String? = null
                McpAgenticLoop.execute(
                    initialPrompt = "use tool",
                    mcpProcessManager = mgr,
                    callAI = {
                        count++
                        if (count == 1) AIResult.Success(toolCallJson(sid, tool))
                        else { round2 = it; AIResult.Success("After disabled") }
                    },
                    toResponse = ::toResponse,
                    syncHandler = null, projectKey = null,
                    userId = userId, permService = permService
                )
                assertNotNull(round2, "Round 2 should be called after disabled skip")
                assertTrue(round2!!.contains("disabled by user"), "Must contain disabled msg")
                assertFalse(mgr.getClientCalled, "getClient must NOT be called for disabled")
            }
        }
    }
}
