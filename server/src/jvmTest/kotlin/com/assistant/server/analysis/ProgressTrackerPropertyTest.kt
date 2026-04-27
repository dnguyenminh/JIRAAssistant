package com.assistant.server.analysis

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Data holder for map progress test inputs.
 */
data class MapProgressInput(
    val completedBatches: Int,
    val totalBatches: Int
)

/**
 * Arbitrary generator for valid [MapProgressInput] where
 * totalBatches >= 1 and completedBatches in 0..totalBatches.
 */
fun Arb.Companion.arbMapProgressInput(): Arb<MapProgressInput> = arbitrary {
    val total = Arb.int(1..500).bind()
    val completed = Arb.int(0..total).bind()
    MapProgressInput(completed, total)
}

/**
 * Property 11: Progress Calculation — Map Phase.
 *
 * For any completedBatches (0..total) and totalBatches (≥1):
 * progress = `20 + (completedBatches * 60 / totalBatches)`,
 * always in range 20..80.
 *
 * **Validates: Requirements 5.1, 5.2**
 */
@OptIn(ExperimentalKotest::class)
class ProgressTrackerPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)

    /**
     * **Validates: Requirements 5.1, 5.2**
     *
     * For any valid completedBatches in 0..totalBatches and
     * totalBatches >= 1, calculateMapProgress always returns
     * a value in the range 20..80.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 11: Progress")
    fun `calculateMapProgress always returns value in 20 to 80`() {
        runBlocking {
            checkAll(cfg, Arb.arbMapProgressInput()) { input ->
                val progress = ProgressTracker.calculateMapProgress(
                    input.completedBatches,
                    input.totalBatches
                )
                assertTrue(progress in 20..80) {
                    "progress $progress out of 20..80 " +
                        "(completed=${input.completedBatches}, " +
                        "total=${input.totalBatches})"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 5.1, 5.2**
     *
     * When completedBatches = 0, progress is always 20.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 11: Progress")
    fun `calculateMapProgress returns 20 when no batches completed`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..500)) { totalBatches ->
                val progress = ProgressTracker.calculateMapProgress(
                    0, totalBatches
                )
                assertEquals(20, progress) {
                    "Expected 20 when completedBatches=0, " +
                        "got $progress (total=$totalBatches)"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 5.1, 5.2**
     *
     * When completedBatches = totalBatches, progress is always 80.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 11: Progress")
    fun `calculateMapProgress returns 80 when all batches completed`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..500)) { totalBatches ->
                val progress = ProgressTracker.calculateMapProgress(
                    totalBatches, totalBatches
                )
                assertEquals(80, progress) {
                    "Expected 80 when completedBatches=totalBatches, " +
                        "got $progress (total=$totalBatches)"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 5.1, 5.2**
     *
     * Progress is monotonically non-decreasing as completedBatches
     * increases from 0 to totalBatches.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 11: Progress")
    fun `calculateMapProgress is monotonically non-decreasing`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..200)) { totalBatches ->
                var prev = ProgressTracker.calculateMapProgress(
                    0, totalBatches
                )
                for (completed in 1..totalBatches) {
                    val curr = ProgressTracker.calculateMapProgress(
                        completed, totalBatches
                    )
                    assertTrue(curr >= prev) {
                        "Progress decreased: $prev -> $curr " +
                            "(completed=$completed, " +
                            "total=$totalBatches)"
                    }
                    prev = curr
                }
            }
        }
    }
}
