package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopConfig
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ProcessMode
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the full agentic loop end-to-end.
 *
 * Mocked [AiBackend]: returns tool call on first prompt,
 * then returns document on second prompt.
 * Mocked [SubprocessProxy]: returns success for tool calls.
 *
 * Verifies: prompt → tool call → tool result → document flow
 * and progress reporting at correct percentages.
 *
 * Requirements: 9.1–9.7, 11.1–11.5
 */
class AgenticLoopIntegrationTest {

    @Test
    fun `full loop - tool call then document`() = runBlocking {
        val backend = twoStepBackend()
        val proxy = successProxy()
        val progress = TrackingReporter()
        val bridge = ToolExecutionBridge(proxy, progress)
        val promptBuilder = AgenticPromptBuilder(proxy)
        val runner = AgenticLoopRunner(bridge, promptBuilder)

        val config = AgenticLoopConfig(
            ticketId = "INT-1", docType = "BRD",
            maxToolCalls = 10, taskTimeoutSeconds = 60,
            processMode = ProcessMode.STATELESS
        )

        val result = runner.runLoop(backend, config, progress)

        assertEquals(1, result.toolCallsExecuted)
        assertEquals(0, result.toolCallsFailed)
        assertTrue(result.document.contains("Final Document"))
        assertEquals(1, result.toolCallLog.size)
        assertTrue(result.toolCallLog[0].success)
    }

    @Test
    fun `progress reported at correct percentages`() = runBlocking {
        val backend = twoStepBackend()
        val proxy = successProxy()
        val progress = TrackingReporter()
        val bridge = ToolExecutionBridge(proxy, progress)
        val promptBuilder = AgenticPromptBuilder(proxy)
        val runner = AgenticLoopRunner(bridge, promptBuilder)

        val config = AgenticLoopConfig(
            ticketId = "INT-2", docType = "BRD",
            maxToolCalls = 10, taskTimeoutSeconds = 60,
            processMode = ProcessMode.STATELESS
        )

        runner.runLoop(backend, config, progress)

        // Tool progress should be between 10% and 90%
        val toolProgress = progress.progressPercents
        assertTrue(
            toolProgress.all { it in 10..90 },
            "Tool progress should be 10-90%, got: $toolProgress"
        )
    }

    @Test
    fun `status is SUCCESS for complete document`() = runBlocking {
        val backend = twoStepBackend()
        val proxy = successProxy()
        val progress = TrackingReporter()
        val bridge = ToolExecutionBridge(proxy, progress)
        val promptBuilder = AgenticPromptBuilder(proxy)
        val runner = AgenticLoopRunner(bridge, promptBuilder)

        val config = AgenticLoopConfig(
            ticketId = "INT-3", docType = "BRD",
            maxToolCalls = 10, taskTimeoutSeconds = 60
        )

        val result = runner.runLoop(backend, config, progress)
        val status = AgenticLoopRunner.determineStatus(result)

        assertEquals(BATaskStatus.SUCCESS, status)
    }

    @Test
    fun `status is PARTIAL when tool call fails`() = runBlocking {
        val backend = twoStepBackend()
        val proxy = failingProxy()
        val progress = TrackingReporter()
        val bridge = ToolExecutionBridge(proxy, progress)
        val promptBuilder = AgenticPromptBuilder(proxy)
        val runner = AgenticLoopRunner(bridge, promptBuilder)

        val config = AgenticLoopConfig(
            ticketId = "INT-4", docType = "BRD",
            maxToolCalls = 10, taskTimeoutSeconds = 60
        )

        val result = runner.runLoop(backend, config, progress)
        val status = AgenticLoopRunner.determineStatus(result)

        assertEquals(BATaskStatus.PARTIAL, status)
        assertEquals(1, result.toolCallsFailed)
    }

    // ── Mock backend ────────────────────────────────────────

    /** Returns tool call on first prompt, document on second. */
    private fun twoStepBackend(): AiBackend {
        var callCount = 0
        return object : AiBackend {
            override val displayName = "IntegrationMock"

            override fun sendPrompt(prompt: String): AiCliResponse {
                callCount++
                return if (callCount == 1) {
                    AiCliResponse(
                        response = """{"type":"tool_call","tool":"get_ticket","params":{"id":"INT-1"}}"""
                    )
                } else {
                    AiCliResponse(response = "# Final Document\nContent here")
                }
            }

            override fun startSession() {}
            override fun sendMessage(msg: String) = sendPrompt(msg)
            override fun endSession() {}
            override fun isSessionActive() = false

            override fun isToolCall(response: String) =
                response.contains("\"type\":\"tool_call\"")

            override fun parseToolCall(response: String): ToolRequest =
                ToolRequest(tool = "get_ticket", params = mapOf("id" to "INT-1"))

            override fun isInstalled() = true
            override fun getInstallInstructions() = ""
        }
    }

    // ── Proxies ─────────────────────────────────────────────

    private fun successProxy(): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(request.id, true, "ticket data", "")

        override fun getAvailableToolDescriptors() = listOf(
            ToolDescriptor("get_ticket", "Get ticket details", listOf("id"))
        )
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun failingProxy(): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(request.id, false, "", "Connection error")

        override fun getAvailableToolDescriptors() = listOf(
            ToolDescriptor("get_ticket", "Get ticket details", listOf("id"))
        )
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    // ── Progress tracker ────────────────────────────────────

    private class TrackingReporter : ProgressReporter {
        val progressPercents = mutableListOf<Int>()
        val toolCalls = mutableListOf<String>()

        override suspend fun reportPhase(
            phaseName: String, phaseIndex: Int, totalPhases: Int
        ) {}

        override suspend fun reportProgress(percent: Int, message: String) {
            progressPercents.add(percent)
        }

        override suspend fun reportToolCall(
            toolName: String, status: String
        ) {
            toolCalls.add("$toolName:$status")
        }
    }
}
