package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus
import com.assistant.mcp.models.McpServerState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for McpAgenticLoop empty reply bug fix.
 * Validates: Requirements 2.1, 3.2 — FINAL_ROUND_INSTRUCTION and early exit.
 */
internal class McpAgenticLoopEmptyReplyTest {

    private fun toolCallJson(round: Int = 1): String =
        """{"mcpToolCall":{"serverId":"test-srv","toolName":"tool_$round","arguments":{}}}"""

    private fun toResponse(r: AIResult, len: Int): ChatResponse =
        ChatResponse(reply = (r as AIResult.Success).response)

    /** Stub McpProcessManager — getClient returns null so executeTool returns error. */
    private class StubMcpManager : McpProcessManager {
        private val status = McpProcessStatus("s", state = McpServerState.STOPPED)
        override suspend fun startServer(configId: String) = status
        override suspend fun stopServer(configId: String) = status
        override suspend fun restartServer(configId: String) = status
        override fun getRunningServers(): Map<String, McpProcessStatus> = emptyMap()
        override fun getStatus(configId: String): McpProcessStatus? = null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools(): List<McpAggregatedTool> = emptyList()
        override fun getClient(configId: String): McpProtocolClient? = null
    }

    /**
     * When AI returns tool calls for all 5 rounds (MAX_ROUNDS),
     * the 6th callAI prompt MUST contain FINAL_ROUND_INSTRUCTION.
     * Validates: Requirement 2.1
     */
    @Test
    fun `final prompt contains FINAL_ROUND_INSTRUCTION after MAX_ROUNDS tool calls`() = runBlocking {
        var callCount = 0
        var capturedFinalPrompt: String? = null

        val response = McpAgenticLoop.execute(
            initialPrompt = "Tell me about ticket CRP-3203",
            mcpProcessManager = StubMcpManager(),
            callAI = { prompt ->
                callCount++
                if (callCount <= 5) {
                    AIResult.Success(toolCallJson(callCount))
                } else {
                    capturedFinalPrompt = prompt
                    AIResult.Success("Here is the summary of ticket CRP-3203.")
                }
            },
            toResponse = ::toResponse
        )

        assertEquals(6, callCount, "callAI should be invoked 6 times (5 rounds + 1 final)")
        assertNotNull(capturedFinalPrompt, "Final prompt must be captured")
        assertTrue(
            capturedFinalPrompt!!.contains(McpAgenticLoop.FINAL_ROUND_INSTRUCTION),
            "Final prompt must contain FINAL_ROUND_INSTRUCTION"
        )
    }

    /**
     * When AI responds with plain text (not tool call) at round 1,
     * the loop MUST exit immediately — callAI called only ONCE.
     * Validates: Requirement 3.2 (Preservation — early exit unchanged)
     */
    @Test
    fun `early exit when AI responds with text at round 1`() = runBlocking {
        var callCount = 0
        val expectedReply = "Hello! How can I help you today?"

        val response = McpAgenticLoop.execute(
            initialPrompt = "Hello",
            mcpProcessManager = StubMcpManager(),
            callAI = { callCount++; AIResult.Success(expectedReply) },
            toResponse = ::toResponse
        )

        assertEquals(1, callCount, "callAI should be invoked exactly once")
        assertEquals(expectedReply, response.reply, "Response must contain the text reply")
    }
}
