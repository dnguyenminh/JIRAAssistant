package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.server.agent.ba.subprocess.pipeline.cli.models.InteractiveSessionContext
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Represents an operation that can be applied to an [InteractiveSessionContext].
 */
sealed class SessionOp {
    data class RecordToolCall(
        val toolName: String,
        val durationMs: Long,
        val success: Boolean,
        val resultSizeChars: Int
    ) : SessionOp()

    data class AppendLine(val line: String) : SessionOp()
    data object RecordFailure : SessionOp()
    data object ResetFailures : SessionOp()
}

/**
 * Arbitrary generator for a single [SessionOp].
 */
fun Arb.Companion.sessionOp(): Arb<SessionOp> = arbitrary {
    when (Arb.int(0..3).bind()) {
        0 -> SessionOp.RecordToolCall(
            toolName = Arb.string(1..20, Codepoint.alphanumeric()).bind(),
            durationMs = Arb.long(0L..10_000L).bind(),
            success = Arb.boolean().bind(),
            resultSizeChars = Arb.int(0..5000).bind()
        )
        1 -> SessionOp.AppendLine(
            Arb.string(0..100, Codepoint.printableAscii()).bind()
        )
        2 -> SessionOp.RecordFailure
        else -> SessionOp.ResetFailures
    }
}

/**
 * Arbitrary generator for a list of [SessionOp]s (1..50 operations).
 */
fun Arb.Companion.sessionOps(): Arb<List<SessionOp>> =
    Arb.list(Arb.sessionOp(), 1..50)

/**
 * Property 7: Session context accurately tracks all state.
 *
 * Generate random sequences of recordToolCall, appendDocumentLine,
 * recordConsecutiveFailure, and resetConsecutiveFailures, then
 * assert toSummary() produces correct metrics.
 *
 * **Validates: Requirements 8.1, 8.2, 8.4, 8.5**
 */
@OptIn(ExperimentalKotest::class)
class InteractiveSessionContextPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)

    /**
     * **Validates: Requirements 8.1, 8.2, 8.4, 8.5**
     *
     * For any random sequence of session operations, toSummary()
     * returns a SessionSummary with correct totalToolCalls,
     * failedToolCalls, documentSizeChars, and consecutiveFailures.
     */
    @Test
    @Tag("cli-interactive-ba-agent")
    fun `toSummary accurately reflects all recorded operations`() {
        runBlocking {
            checkAll(cfg, Arb.sessionOps()) { ops ->
                val ctx = InteractiveSessionContext()

                var expectedTotal = 0
                var expectedFailed = 0
                var consecutiveFailures = 0
                val lines = mutableListOf<String>()

                for (op in ops) {
                    when (op) {
                        is SessionOp.RecordToolCall -> {
                            ctx.recordToolCall(
                                ToolCallLogEntry(
                                    toolName = op.toolName,
                                    durationMs = op.durationMs,
                                    success = op.success,
                                    resultSizeChars = op.resultSizeChars
                                )
                            )
                            expectedTotal++
                            if (!op.success) {
                                expectedFailed++
                                consecutiveFailures++
                            } else {
                                consecutiveFailures = 0
                            }
                        }
                        is SessionOp.AppendLine -> {
                            ctx.appendDocumentLine(op.line)
                            lines.add(op.line)
                        }
                        is SessionOp.RecordFailure -> {
                            ctx.recordConsecutiveFailure()
                            consecutiveFailures++
                        }
                        is SessionOp.ResetFailures -> {
                            ctx.resetConsecutiveFailures()
                            consecutiveFailures = 0
                        }
                    }
                }

                val expectedDocSize = if (lines.isEmpty()) 0
                    else lines.joinToString("\n").length

                val summary = ctx.toSummary()

                assertEquals(expectedTotal, summary.totalToolCalls) {
                    "totalToolCalls mismatch for ops=$ops"
                }
                assertEquals(expectedFailed, summary.failedToolCalls) {
                    "failedToolCalls mismatch for ops=$ops"
                }
                assertEquals(expectedDocSize, summary.documentSizeChars) {
                    "documentSizeChars mismatch for ops=$ops"
                }
                assertEquals(consecutiveFailures, summary.consecutiveFailures) {
                    "consecutiveFailures mismatch for ops=$ops"
                }
            }
        }
    }
}
