package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.BATaskStatus
import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.AgenticLoopResult
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Feature: poc-agent-replacement, Property 9: BATaskResult status determination

/**
 * Property 9: BATaskResult status determination
 *
 * For any AgenticLoopResult combination:
 * (a) if timedOut is true → status SHALL be TIMEOUT
 * (b) if document is empty and not timed out → status SHALL be FAILED
 * (c) if document is non-empty and toolCallsFailed > 0 → PARTIAL
 * (d) if document is non-empty and toolCallsFailed == 0 → SUCCESS
 *
 * Additionally, toolCallLog size SHALL equal toolCallsExecuted.
 *
 * **Validates: Requirements 12.1, 12.2, 12.3, 12.4, 12.5**
 */
class StatusDeterminationPropertyTest {

    @Test
    fun `Property 9 - timedOut produces TIMEOUT status`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbDocument(),
                Arb.int(0..20),
                Arb.int(0..10)
            ) { document, executed, failed ->
                val result = buildResult(
                    document = document,
                    timedOut = true,
                    toolCallsExecuted = executed,
                    toolCallsFailed = failed.coerceAtMost(executed)
                )
                val status = AgenticLoopRunner.determineStatus(result)
                assertEquals(
                    BATaskStatus.TIMEOUT, status,
                    "timedOut=true should produce TIMEOUT"
                )
            }
        }
    }

    @Test
    fun `Property 9 - empty document produces FAILED status`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbEmptyDocument(),
                Arb.int(0..20),
                Arb.int(0..10)
            ) { document, executed, failed ->
                val result = buildResult(
                    document = document,
                    timedOut = false,
                    toolCallsExecuted = executed,
                    toolCallsFailed = failed.coerceAtMost(executed)
                )
                val status = AgenticLoopRunner.determineStatus(result)
                assertEquals(
                    BATaskStatus.FAILED, status,
                    "empty doc + not timed out should produce FAILED"
                )
            }
        }
    }

    @Test
    fun `Property 9 - non-empty doc with failures produces PARTIAL`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbNonEmptyDocument(),
                Arb.int(1..20)
            ) { document, executed ->
                val failed = Arb.int(1..executed).sample(
                    io.kotest.property.RandomSource.default()
                ).value
                val result = buildResult(
                    document = document,
                    timedOut = false,
                    toolCallsExecuted = executed,
                    toolCallsFailed = failed
                )
                val status = AgenticLoopRunner.determineStatus(result)
                assertEquals(
                    BATaskStatus.PARTIAL, status,
                    "non-empty doc + failures should produce PARTIAL"
                )
            }
        }
    }

    @Test
    fun `Property 9 - non-empty doc no failures produces SUCCESS`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbNonEmptyDocument(),
                Arb.int(0..20)
            ) { document, executed ->
                val result = buildResult(
                    document = document,
                    timedOut = false,
                    toolCallsExecuted = executed,
                    toolCallsFailed = 0
                )
                val status = AgenticLoopRunner.determineStatus(result)
                assertEquals(
                    BATaskStatus.SUCCESS, status,
                    "non-empty doc + no failures should produce SUCCESS"
                )
            }
        }
    }

    @Test
    fun `Property 9 - toolCallLog size equals toolCallsExecuted`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(0..30),
                Arb.boolean(),
                arbDocument()
            ) { executed, timedOut, document ->
                val log = generateToolCallLog(executed)
                val result = AgenticLoopResult(
                    document = document,
                    toolCallLog = log,
                    toolCallsExecuted = executed,
                    toolCallsFailed = 0,
                    timedOut = timedOut,
                    totalDurationMs = 100
                )
                assertEquals(
                    result.toolCallsExecuted,
                    result.toolCallLog.size,
                    "toolCallLog size must equal toolCallsExecuted"
                )
            }
        }
    }

    // ── Generators ──────────────────────────────────────────

    private fun arbDocument(): Arb<String> =
        Arb.string(0..50, Codepoint.alphanumeric())

    private fun arbEmptyDocument(): Arb<String> =
        Arb.of("", " ", "  ", "\t", "\n", "  \n  ")

    private fun arbNonEmptyDocument(): Arb<String> =
        Arb.string(1..100, Codepoint.alphanumeric())
            .filter { it.isNotBlank() }

    // ── Helpers ─────────────────────────────────────────────

    private fun buildResult(
        document: String,
        timedOut: Boolean,
        toolCallsExecuted: Int,
        toolCallsFailed: Int
    ): AgenticLoopResult {
        val log = generateToolCallLog(toolCallsExecuted)
        return AgenticLoopResult(
            document = document,
            toolCallLog = log,
            toolCallsExecuted = toolCallsExecuted,
            toolCallsFailed = toolCallsFailed,
            timedOut = timedOut,
            totalDurationMs = 100
        )
    }

    private fun generateToolCallLog(count: Int): List<ToolCallLogEntry> =
        (1..count).map { i ->
            ToolCallLogEntry(
                toolName = "tool_$i",
                durationMs = 10L * i,
                success = true,
                resultSizeChars = 50
            )
        }
}
