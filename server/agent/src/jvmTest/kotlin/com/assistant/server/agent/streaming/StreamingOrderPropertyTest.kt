package com.assistant.server.agent.streaming

import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.streaming.StreamingCallback
import com.assistant.agent.streaming.StreamingConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Property 27 — Streaming output order preservation.
 *
 * For any subprocess that produces N response chunks on stdout
 * in order [c₁, c₂, ..., cₙ], the streaming adapter SHALL
 * deliver chunks to callbacks in the same order with no chunks
 * lost or reordered.
 *
 * **Validates: Requirements 13.5, 17.1**
 */
@OptIn(ExperimentalKotest::class)
@Tag("generic-agent-framework")
class StreamingOrderPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)
    private val safeStr =
        Arb.string(1..30, Codepoint.alphanumeric())

    /**
     * Property 27: Streaming output order preservation.
     *
     * Generates a random list of chunks, feeds them through
     * [StreamingOutputAdapter.processStream], and verifies
     * the callback receives them in the original order.
     *
     * Uses bufferSize=1 to force immediate forwarding of
     * each chunk, avoiding batching that would merge chunks.
     *
     * **Validates: Requirements 13.5, 17.1**
     */
    @Test
    @Tag("Property-27")
    fun `chunks are delivered in original order`() {
        runBlocking {
            checkAll(cfg, arbChunkList()) { chunks ->
                val received = CopyOnWriteArrayList<String>()
                val adapter = StreamingOutputAdapter(
                    reporter = NoOpReporter(),
                    config = StreamingConfig(
                        enabled = true,
                        bufferSize = 1
                    )
                )
                adapter.registerCallback(
                    StreamingCallback { chunk, _ ->
                        received.add(chunk)
                    }
                )
                adapter.processStream(chunks.asFlow())
                received.size shouldBe chunks.size
                received.forEachIndexed { i, chunk ->
                    chunk shouldBe chunks[i]
                }
            }
        }
    }

    /**
     * Property 27 (extended): no chunks lost when streaming
     * is enabled.
     *
     * **Validates: Requirements 17.1**
     */
    @Test
    @Tag("Property-27")
    fun `no chunks are lost during streaming`() {
        runBlocking {
            checkAll(cfg, arbChunkList()) { chunks ->
                val received = CopyOnWriteArrayList<String>()
                val adapter = StreamingOutputAdapter(
                    reporter = NoOpReporter(),
                    config = StreamingConfig(
                        enabled = true,
                        bufferSize = 1
                    )
                )
                adapter.registerCallback(
                    StreamingCallback { chunk, _ ->
                        received.add(chunk)
                    }
                )
                adapter.processStream(chunks.asFlow())
                val joinedReceived = received.joinToString("")
                val joinedOriginal = chunks.joinToString("")
                joinedReceived shouldBe joinedOriginal
            }
        }
    }

    // ── Generators ──────────────────────────────────────

    private fun arbChunkList(): Arb<List<String>> = arbitrary {
        val n = Arb.int(1..20).bind()
        (1..n).map { safeStr.bind() }
    }

    // ── Test doubles ────────────────────────────────────

    private class NoOpReporter : ProgressReporter {
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
}
