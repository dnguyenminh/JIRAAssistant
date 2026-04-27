package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Feature: poc-agent-replacement, Property 7: Tool call bridge conversion and logging

/**
 * Property 7: Tool call bridge conversion and logging
 *
 * For any ToolRequest with a non-empty tool name and arbitrary params,
 * ToolExecutionBridge.execute() SHALL produce a ToolBridgeResult where:
 * (a) logEntry.toolName matches the request's tool name,
 * (b) logEntry.durationMs >= 0,
 * (c) logEntry.success matches the SubprocessProxy response's success field,
 * (d) a valid correlation UUID was generated for the ToolCallRequest.
 *
 * **Validates: Requirements 10.1, 10.6**
 */
class ToolExecutionBridgePropertyTest {

    @Test
    fun `Property 7 - bridge conversion produces correct log entries`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbToolName(),
                arbParamMap(),
                Arb.boolean()
            ) { toolName, params, proxySuccess ->
                val capturedRequests = mutableListOf<ToolCallRequest>()
                val proxy = mockProxy(proxySuccess, capturedRequests)
                val reporter = noOpReporter()
                val bridge = ToolExecutionBridge(proxy, reporter)

                val request = ToolRequest(tool = toolName, params = params)
                val result = bridge.execute(request)

                // (a) logEntry.toolName matches
                assertEquals(toolName, result.logEntry.toolName)

                // (b) durationMs >= 0
                assertTrue(result.logEntry.durationMs >= 0)

                // (c) success matches proxy response
                assertEquals(proxySuccess, result.logEntry.success)

                // (d) valid UUID was generated
                assertTrue(capturedRequests.isNotEmpty())
                val uuid = capturedRequests.last().id
                assertNotNull(uuid)
                assertValidUuid(uuid)
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbToolName(): Arb<String> =
        Arb.string(1..20, Codepoint.alphanumeric())

    private fun arbParamMap(): Arb<Map<String, String>> = arbitrary {
        val size = Arb.int(0..5).bind()
        (0 until size).associate {
            val key = Arb.string(1..10, Codepoint.alphanumeric()).bind()
            val value = Arb.string(0..20, Codepoint.alphanumeric()).bind()
            key to value
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private fun assertValidUuid(uuid: String) {
        val uuidRegex = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
        assertTrue(
            uuidRegex.matches(uuid),
            "Expected valid UUID but got: $uuid"
        )
    }

    private fun mockProxy(
        success: Boolean,
        captured: MutableList<ToolCallRequest>
    ): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ): ToolCallResponse {
            captured.add(request)
            return if (success) {
                ToolCallResponse(request.id, true, "data", "")
            } else {
                ToolCallResponse(request.id, false, "", "error")
            }
        }
        override fun getAvailableToolDescriptors() =
            emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun noOpReporter(): ProgressReporter = object : ProgressReporter {
        override suspend fun reportPhase(
            phaseName: String, phaseIndex: Int, totalPhases: Int
        ) {}
        override suspend fun reportProgress(percent: Int, message: String) {}
        override suspend fun reportToolCall(
            toolName: String, status: String
        ) {}
    }
}
