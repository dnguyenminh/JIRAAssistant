package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopConfig
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AiCliResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ProcessMode
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 6: maxToolCalls enforcement

/**
 * Property 6: maxToolCalls enforcement
 *
 * For any maxToolCalls value M > 0 and an AI backend that always
 * requests tool calls, the agentic loop SHALL execute at most M
 * tool calls before requesting the final document.
 *
 * The mock backend returns a tool call JSON for the first M calls,
 * then after receiving the "produce final document" message, returns
 * a document.
 *
 * **Validates: Requirements 9.5**
 */
class MaxToolCallsPropertyTest {

    @Test
    fun `Property 6 - loop executes at most M tool calls`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 50),
                Arb.int(1..50)
            ) { maxToolCalls ->
                val messagesReceived = mutableListOf<String>()
                val backend = alwaysToolCallBackend(messagesReceived)
                val proxy = successProxy()
                val reporter = noOpReporter()
                val bridge = ToolExecutionBridge(proxy, reporter)
                val promptBuilder = stubPromptBuilder()
                val runner = AgenticLoopRunner(bridge, promptBuilder)

                val config = AgenticLoopConfig(
                    ticketId = "TEST-1",
                    docType = "BRD",
                    maxToolCalls = maxToolCalls,
                    taskTimeoutSeconds = 60,
                    processMode = ProcessMode.STATELESS
                )

                val result = runner.runLoop(backend, config, reporter)

                // Loop must execute at most M tool calls
                assertTrue(
                    result.toolCallsExecuted <= maxToolCalls,
                    "Expected at most $maxToolCalls tool calls " +
                    "but got ${result.toolCallsExecuted}"
                )

                // Loop must have exactly M tool calls (backend
                // always returns tool calls until forced)
                assertEquals(
                    maxToolCalls,
                    result.toolCallsExecuted,
                    "Expected exactly $maxToolCalls tool calls"
                )

                // The "produce final document" message must have
                // been sent after M tool calls
                val limitMsg = AgenticLoopRunner.PRODUCE_FINAL_DOC_MSG
                assertTrue(
                    messagesReceived.any { it.contains(limitMsg) },
                    "Expected final document request message"
                )

                // Document should be non-empty
                assertTrue(
                    result.document.isNotBlank(),
                    "Expected non-empty document"
                )
            }
        }
    }

    // ── Mock backend ────────────────────────────────────────

    /**
     * Backend that always returns tool calls until it receives
     * the "produce final document" message, then returns a doc.
     */
    private fun alwaysToolCallBackend(
        messagesReceived: MutableList<String>
    ): AiBackend = object : AiBackend {
        override val displayName = "MockBackend"

        override suspend fun sendPrompt(prompt: String): AiCliResponse {
            messagesReceived.add(prompt)
            return respondBasedOn(prompt)
        }

        override fun startSession() {}
        override suspend fun sendMessage(msg: String): AiCliResponse {
            messagesReceived.add(msg)
            return respondBasedOn(msg)
        }
        override fun endSession() {}
        override fun isSessionActive() = false

        override fun isToolCall(response: String): Boolean =
            response.contains("\"type\":\"tool_call\"")

        override fun parseToolCall(response: String): ToolRequest =
            ToolRequest(tool = "mock_tool", params = mapOf("k" to "v"))

        override fun isInstalled() = true
        override fun getInstallInstructions() = ""

        private fun respondBasedOn(msg: String): AiCliResponse {
            // Match only the exact limit-reached message from
            // AgenticLoopRunner, not the generic continuation
            // instruction which also mentions "final document".
            val limitMsg = AgenticLoopRunner.PRODUCE_FINAL_DOC_MSG
            return if (msg.contains(limitMsg)) {
                AiCliResponse(response = "# Final Document\nContent")
            } else {
                AiCliResponse(
                    response = """{"type":"tool_call","tool":"mock","params":{}}"""
                )
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun successProxy(): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ): ToolCallResponse =
            ToolCallResponse(request.id, true, "ok", "")

        override fun getAvailableToolDescriptors() =
            listOf(
                ToolDescriptor("mock_tool", "A mock tool", listOf("k"))
            )
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun stubPromptBuilder(): AgenticPromptBuilder {
        val proxy = successProxy()
        return AgenticPromptBuilder(proxy)
    }

    private fun noOpReporter(): ProgressReporter = object : ProgressReporter {
        override suspend fun reportPhase(
            phaseName: String, phaseIndex: Int, totalPhases: Int
        ) {}
        override suspend fun reportProgress(
            percent: Int, message: String
        ) {}
        override suspend fun reportToolCall(
            toolName: String, status: String
        ) {}
    }
}
