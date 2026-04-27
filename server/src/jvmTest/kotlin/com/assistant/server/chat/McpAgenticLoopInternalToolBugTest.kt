package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import com.assistant.server.mcp.internal.InternalMcpBridge
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Bug Condition Exploration Test — Property 1: Internal Tool Calls Fail via Agentic Loop
 *
 * This test MUST FAIL on unfixed code — failure confirms the bug exists.
 * Internal tools (serverId: "jira-assistant-ui") route via McpProcessManager.getClient()
 * which returns null → "Error: server 'jira-assistant-ui' not running".
 *
 * After fix: internal tools route via InternalMcpBridge.callTool() → actual result.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3**
 */
@OptIn(ExperimentalKotest::class)
class McpAgenticLoopInternalToolBugTest {

    companion object {
        private const val INTERNAL_SERVER = "jira-assistant-ui"
        private const val ERROR_MARKER = "Error: server '$INTERNAL_SERVER' not running"
        private const val BRIDGE_RESULT = "Tool executed successfully via bridge"
        private val INTERNAL_TOOLS = listOf(
            "get_dashboard_metrics", "list_projects", "get_graph_data",
            "navigate_to_page", "start_scan", "analyze_ticket",
            "get_scan_status", "list_tickets", "search_knowledge_base"
        )
    }

    /** McpProcessManager returning null for all getClient calls (real behavior). */
    private class NullClientMcpManager : McpProcessManager {
        private val dummy = McpProcessStatus("x", state = McpServerState.STOPPED)
        override suspend fun startServer(configId: String) = dummy
        override suspend fun stopServer(configId: String) = dummy
        override suspend fun restartServer(configId: String) = dummy
        override fun getRunningServers(): Map<String, McpProcessStatus> = emptyMap()
        override fun getStatus(id: String): McpProcessStatus? = null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools(): List<McpAggregatedTool> = emptyList()
        override fun getClient(configId: String): McpProtocolClient? = null
    }

    /** Stub InternalMcpBridge that returns a successful response. */
    private class StubBridge : InternalMcpBridge(executor = null, mcpRepo = null) {
        override suspend fun callTool(
            toolName: String, arguments: JsonObject,
            userId: String, userRole: String
        ): McpToolCallResponse = McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = BRIDGE_RESULT)),
            isError = false
        )
    }

    private fun toResponse(r: AIResult, len: Int) =
        ChatResponse(reply = (r as AIResult.Success).response)

    private fun buildToolCallJson(toolName: String): String =
        """{"mcpToolCall":{"serverId":"$INTERNAL_SERVER","toolName":"$toolName","arguments":{}}}"""

    /**
     * Property 1: For all internal tool calls (serverId = "jira-assistant-ui"),
     * the tool result fed back to AI SHALL NOT contain the error string.
     *
     * We capture the prompt sent to callAI in round 2 — if it contains the
     * error marker, the bug is confirmed (tool routed via McpProcessManager).
     *
     * On UNFIXED code: EXPECTED TO FAIL (confirms bug exists).
     */
    @Test
    fun `Property 1 - internal tool calls shall not produce server-not-running error`() {
        val bridge = StubBridge()
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 9),
                Arb.element(INTERNAL_TOOLS)
            ) { toolName ->
                var callCount = 0
                var capturedRound2Prompt: String? = null

                McpAgenticLoop.execute(
                    initialPrompt = "Use tool $toolName",
                    mcpProcessManager = NullClientMcpManager(),
                    callAI = { prompt ->
                        callCount++
                        if (callCount == 1) {
                            AIResult.Success(buildToolCallJson(toolName))
                        } else {
                            capturedRound2Prompt = prompt
                            AIResult.Success("Final answer with data")
                        }
                    },
                    toResponse = ::toResponse,
                    internalMcpBridge = bridge
                )

                assertNotNull(capturedRound2Prompt, "Round 2 should have been called")
                assertFalse(
                    capturedRound2Prompt!!.contains(ERROR_MARKER),
                    "Internal tool '$toolName' routed via McpProcessManager " +
                        "instead of InternalMcpBridge. Tool result contains: $ERROR_MARKER"
                )
            }
        }
    }
}
