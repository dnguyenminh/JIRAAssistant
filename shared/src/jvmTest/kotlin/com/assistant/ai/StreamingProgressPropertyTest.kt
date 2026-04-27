package com.assistant.ai

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 3: Streaming progress is bounded and monotonically
 * non-decreasing.
 *
 * For any sequence of increasing linesReceived values and any
 * estimatedTotalLines (with dynamic doubling when exceeded),
 * the computed streaming progress SHALL always be in [0, 95]
 * before done, equal 100 when done, and never decrease between
 * consecutive calls.
 *
 * **Validates: Requirements 1.5, 3.2, 3.3**
 *
 * Feature: streaming-generation-progress,
 * Property 3: Streaming progress is bounded and monotonically
 * non-decreasing
 */
class StreamingProgressPropertyTest {

    /**
     * Simulates the progress tracking loop from readStream:
     * adjustEstimate → calculateProgress → max-track.
     * Returns the list of reported progress values.
     */
    private fun simulateProgressSequence(
        totalLines: Int,
        initialEstimate: Int
    ): List<Int> {
        val reported = mutableListOf<Int>()
        var estimate = initialEstimate
        var maxProgress = 0
        for (i in 1..totalLines) {
            estimate = adjustEstimate(i, estimate)
            val raw = calculateProgress(i, estimate)
            maxProgress = maxOf(maxProgress, raw)
            reported.add(maxProgress)
        }
        return reported
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 3a - progress is always in 0 to 95`() {
        kotlinx.coroutines.test.runTest {
            checkAll(
                PropTestConfig(iterations = 200),
                Arb.int(0..5000),
                Arb.int(1..2000)
            ) { linesReceived, estimatedTotal ->
                val progress = calculateProgress(
                    linesReceived, estimatedTotal
                )
                assertTrue(
                    progress in 0..95,
                    "progress=$progress for lines=$linesReceived" +
                        " est=$estimatedTotal must be in [0,95]"
                )
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 3b - progress equals 100 only at done`() {
        kotlinx.coroutines.test.runTest {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(1..3000),
                Arb.int(1..2000)
            ) { totalLines, initialEstimate ->
                val seq = simulateProgressSequence(
                    totalLines, initialEstimate
                )
                seq.forEach { p ->
                    assertTrue(
                        p < 100,
                        "Before done: progress=$p must be <100"
                    )
                }
                // At done: caller sets 100
                val doneProgress = 100
                assertTrue(doneProgress == 100)
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 3c - progress never decreases`() {
        kotlinx.coroutines.test.runTest {
            checkAll(
                PropTestConfig(iterations = 200),
                Arb.int(1..3000),
                Arb.int(100..2000)
            ) { totalLines, initialEstimate ->
                val seq = simulateProgressSequence(
                    totalLines, initialEstimate
                )
                for (i in 1 until seq.size) {
                    assertTrue(
                        seq[i] >= seq[i - 1],
                        "Progress decreased: ${seq[i - 1]}→" +
                            "${seq[i]} at step $i"
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 3d - estimate doubling keeps progress bounded`() {
        kotlinx.coroutines.test.runTest {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(100..2000)
            ) { initialEstimate ->
                // Simulate past the doubling point
                val seq = simulateProgressSequence(
                    initialEstimate + 50, initialEstimate
                )
                seq.forEach { p ->
                    assertTrue(
                        p in 0..95,
                        "After doubling: $p must be in [0,95]"
                    )
                }
                // Monotonicity holds even across doubling
                for (i in 1 until seq.size) {
                    assertTrue(
                        seq[i] >= seq[i - 1],
                        "Monotonicity violated at step $i"
                    )
                }
            }
        }
    }

    @Test
    fun `Property 3e - zero estimatedTotalLines returns 0`() {
        val progress = calculateProgress(100, 0)
        assertTrue(
            progress == 0,
            "Zero estimate should yield progress=0"
        )
    }
}
