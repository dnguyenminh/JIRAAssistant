package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.InteractiveSessionContext
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.LoopConfig
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.LoopResult
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.*

/**
 * Property 4: Interactive loop correctly separates tool calls from content.
 *
 * Generates random sequences of tool call lines, content lines, and a
 * terminating `---END---`. Uses PipedInputStream/PipedOutputStream to
 * simulate CLI process I/O and verifies the LoopResult matches expected
 * tool call counts and document content.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
 */
@OptIn(ExperimentalKotest::class)
class CliInteractiveEnginePropertyTest {

    private val cfg = PropTestConfig(iterations = 75)

    // -- SimLine sealed class --

    sealed class SimLine {
        data class ToolCall(val name: String, val args: Map<String, String>) : SimLine()
        data class Content(val text: String) : SimLine()
    }

    // -- Generators --

    private fun arbToolName(): Arb<String> =
        Arb.string(1..20, Codepoint.alphanumeric())

    private fun arbArgMap(): Arb<Map<String, String>> =
        Arb.map(
            keyArb = Arb.string(1..10, Codepoint.alphanumeric()),
            valueArb = Arb.string(0..15, Codepoint.alphanumeric()),
            minSize = 0, maxSize = 3
        )

    private fun arbContentLine(): Arb<String> =
        Arb.string(1..60, Codepoint.alphanumeric())
            .filter { !it.contains("toolCall") && !it.contains("---END---") }

    private fun arbSimLine(): Arb<SimLine> = arbitrary {
        if (Arb.boolean().bind()) {
            SimLine.ToolCall(arbToolName().bind(), arbArgMap().bind())
        } else {
            SimLine.Content(arbContentLine().bind())
        }
    }

    private fun arbSimSequence(): Arb<List<SimLine>> =
        Arb.list(arbSimLine(), 0..15)

    // -- Helpers --

    private fun buildToolCallLine(tc: SimLine.ToolCall): String {
        val argsJson = tc.args.entries.joinToString(",") { (k, v) ->
            "\"$k\":\"$v\""
        }
        return "{\"toolCall\":{\"name\":\"${tc.name}\",\"arguments\":{$argsJson}}}"
    }

    private fun proxyAlternating(): SubprocessProxy {
        var callCount = 0
        return object : SubprocessProxy {
            override suspend fun handleToolCallRequest(
                request: ToolCallRequest
            ): ToolCallResponse {
                val idx = callCount++
                val success = idx % 2 == 0
                return ToolCallResponse(
                    id = request.id, success = success,
                    data = if (success) "ok" else "",
                    error = if (success) "" else "fail"
                )
            }
            override fun getAvailableToolDescriptors(): List<ToolDescriptor> = emptyList()
            override fun buildToolListMessage(): String = ""
            override fun buildToolsUpdatedMessage(): String = ""
        }
    }

    // -- Property 4 --

    /**
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `P4 - interactive loop correctly separates tool calls from content`() {
        runBlocking {
            checkAll(cfg, arbSimSequence()) { lines ->
                verifyLoopResult(lines)
            }
        }
    }

    private suspend fun verifyLoopResult(lines: List<SimLine>) {
        val result = runBlocking(Dispatchers.IO) {
            val pipeOut = PipedOutputStream()
            val pipeIn = PipedInputStream(pipeOut, 1 shl 16)
            val sinkOut = PipedOutputStream()
            val sinkIn = PipedInputStream(sinkOut, 1 shl 16)

            val reader = BufferedReader(InputStreamReader(pipeIn))
            val writer = BufferedWriter(OutputStreamWriter(sinkOut))
            val engine = CliInteractiveEngine()
            val executor = CliToolExecutor(proxyAlternating())
            val ctx = InteractiveSessionContext()
            val config = LoopConfig(maxToolCalls = 100, timeoutSeconds = 10)

            // Drain sink in background to prevent buffer deadlock
            val sinkJob = launch {
                BufferedReader(InputStreamReader(sinkIn)).use { sr ->
                    try { while (sr.readLine() != null) { } } catch (_: IOException) { }
                }
            }

            // Feed lines in background
            val writerJob = launch {
                BufferedWriter(OutputStreamWriter(pipeOut)).use { pw ->
                    for (sim in lines) {
                        val text = when (sim) {
                            is SimLine.ToolCall -> buildToolCallLine(sim)
                            is SimLine.Content -> sim.text
                        }
                        pw.write(text); pw.newLine(); pw.flush()
                    }
                    pw.write("---END---"); pw.newLine(); pw.flush()
                }
            }

            val loopResult = async {
                engine.runInteractiveLoop(reader, writer, executor, ctx, config)
            }

            val res = loopResult.await()
            writerJob.join()
            writer.close()
            sinkJob.join()
            res
        }

        val toolCalls = lines.filterIsInstance<SimLine.ToolCall>()
        val contentLines = lines.filterIsInstance<SimLine.Content>()
        val expectedDoc = contentLines.joinToString("\n") { it.text }
        val expectedFailed = toolCalls.indices.count { it % 2 != 0 }

        assertFalse(result.timedOut) { "Loop should not time out" }
        assertEquals(toolCalls.size, result.toolCallsExecuted) {
            "toolCallsExecuted mismatch"
        }
        assertEquals(expectedFailed, result.toolCallsFailed) {
            "toolCallsFailed mismatch"
        }
        assertEquals(expectedDoc, result.document) {
            "Document content mismatch"
        }
    }
}
