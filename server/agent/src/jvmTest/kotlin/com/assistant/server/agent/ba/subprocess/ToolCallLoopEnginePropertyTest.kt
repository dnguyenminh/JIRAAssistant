package com.assistant.server.agent.ba.subprocess

import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.agent.models.ToolDescriptor
import com.assistant.config.JsonConfig
import com.assistant.server.agent.subprocess.MessageProtocol
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Feature: agent-subprocess-orchestration
 * Properties 4, 5, 6 for ToolCallLoopEngine
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.8**
 */
@OptIn(ExperimentalKotest::class)
@Tag("agent-subprocess-orchestration")
class ToolCallLoopEnginePropertyTest {

    private val json = JsonConfig.instance

    /**
     * Property 4: Tool call proxying with correlation ID matching
     *
     * For any N (1–30) ToolCallRequest messages with unique IDs,
     * exactly N ToolCallResponse messages are written back, and
     * each response ID matches exactly one request ID (bijection).
     *
     * **Validates: Requirements 3.1, 3.4, 3.8**
     */
    @Test
    fun `Property 4 - correlation ID matching`() {
        runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.int(1..30)
        ) { n ->
            val requests = (1..n).map { buildToolCallRequest() }
            val lines = requests.map { formatToolCallLine(it) } +
                MessageProtocol.DELIMITER
            val written = CopyOnWriteArrayList<String>()
            val proxy = captureProxy()
            val engine = ToolCallLoopEngine(proxy, NoOpReporter)

            val result = engine.runLoop(
                stdoutFlow = flowOf(*lines.toTypedArray()),
                stdinWriter = { written.add(it) },
                maxToolCalls = n + 10
            )

            assertEquals(n, written.size, "Expected $n responses")
            val responseIds = written.map { extractResponseId(it) }
            val requestIds = requests.map { it.id }.toSet()
            assertEquals(
                requestIds, responseIds.toSet(),
                "Response IDs must match request IDs"
            )
        }
        }
    }

    /**
     * Property 5: Plain text accumulation preserves content and order
     *
     * For any K (1–50) plain text lines, the returned document
     * contains all K lines in original order.
     *
     * **Validates: Requirements 3.2, 3.3**
     */
    @Test
    fun `Property 5 - plain text accumulation`() {
        runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(arbPlainTextLine(), 1..50)
        ) { textLines ->
            val lines = textLines + MessageProtocol.DELIMITER
            val engine = ToolCallLoopEngine(captureProxy(), NoOpReporter)

            val result = engine.runLoop(
                stdoutFlow = flowOf(*lines.toTypedArray()),
                stdinWriter = { }
            )

            for ((index, expected) in textLines.withIndex()) {
                val docLines = result.document.split("\n")
                assertTrue(
                    index < docLines.size &&
                        docLines[index] == expected,
                    "Line $index mismatch: expected '$expected'"
                )
            }
        }
        }
    }

    /**
     * Property 6: Max tool call limit enforcement
     *
     * For any limit N (1–10) and N+K (K=1–5) requests,
     * SubprocessProxy is invoked exactly N times; remaining K
     * get error responses with success=false.
     *
     * **Validates: Requirements 3.5**
     */
    @Test
    fun `Property 6 - max tool call limit`() {
        runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.int(1..10),
            Arb.int(1..5)
        ) { n, k ->
            val total = n + k
            val requests = (1..total).map { buildToolCallRequest() }
            val lines = requests.map { formatToolCallLine(it) } +
                MessageProtocol.DELIMITER
            val proxyInvocations = CopyOnWriteArrayList<String>()
            val proxy = countingProxy(proxyInvocations)
            val written = CopyOnWriteArrayList<String>()
            val engine = ToolCallLoopEngine(proxy, NoOpReporter)

            engine.runLoop(
                stdoutFlow = flowOf(*lines.toTypedArray()),
                stdinWriter = { written.add(it) },
                maxToolCalls = n
            )

            assertEquals(
                n, proxyInvocations.size,
                "Proxy should be invoked exactly $n times"
            )
            assertEquals(
                total, written.size,
                "All $total requests should get responses"
            )
            // Last k responses should have success=false
            val lastK = written.drop(n)
            for (resp in lastK) {
                assertFalse(
                    extractResponseSuccess(resp),
                    "Over-limit response should be success=false"
                )
            }
        }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun buildToolCallRequest() = ToolCallRequest(
        id = UUID.randomUUID().toString(),
        name = "testTool",
        arguments = mapOf("key" to "value")
    )

    private fun formatToolCallLine(req: ToolCallRequest): String {
        val msg = com.assistant.agent.subprocess.SubprocessMessage(
            type = "toolCall", toolCall = req
        )
        return json.encodeToString(
            com.assistant.agent.subprocess.SubprocessMessage
                .serializer(), msg
        )
    }

    private fun extractResponseId(written: String): String {
        val msg = json.decodeFromString(
            com.assistant.agent.subprocess.SubprocessMessage
                .serializer(),
            written.lines().first { it.trim().startsWith("{") }
        )
        return msg.toolResult!!.id
    }

    private fun extractResponseSuccess(written: String): Boolean {
        val msg = json.decodeFromString(
            com.assistant.agent.subprocess.SubprocessMessage
                .serializer(),
            written.lines().first { it.trim().startsWith("{") }
        )
        return msg.toolResult!!.success
    }

    private fun captureProxy() = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ) = ToolCallResponse(
            id = request.id, success = true, data = "ok"
        )
        override fun getAvailableToolDescriptors() =
            emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun countingProxy(
        invocations: MutableList<String>
    ) = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            request: ToolCallRequest
        ): ToolCallResponse {
            invocations.add(request.id)
            return ToolCallResponse(
                id = request.id, success = true, data = "ok"
            )
        }
        override fun getAvailableToolDescriptors() =
            emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun arbPlainTextLine(): Arb<String> = arbitrary {
        val text = Arb.string(
            1..60, Codepoint.alphanumeric()
        ).bind()
        // Ensure not JSON-like and not delimiter
        "text: $text"
    }
}

/** No-op ProgressReporter for tests. */
private object NoOpReporter : ProgressReporter {
    override suspend fun reportPhase(
        phaseName: String, phaseIndex: Int, totalPhases: Int
    ) = Unit
    override suspend fun reportProgress(
        percent: Int, message: String
    ) = Unit
    override suspend fun reportToolCall(
        toolName: String, status: String
    ) = Unit
}
