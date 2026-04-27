package com.assistant.server.analysis.models

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Arbitrary generator for [MapReduceConfig] with values that
 * intentionally span well outside the valid clamping ranges.
 */
fun Arb.Companion.arbMapReduceConfig(): Arb<MapReduceConfig> = arbitrary {
    MapReduceConfig(
        maxBatchSize = Arb.int(-50..500).bind(),
        maxConcurrentBatches = Arb.int(-20..50).bind(),
        mapReduceThreshold = Arb.int(-100..2000).bind()
    )
}

/**
 * Property 2: MapReduceConfig Clamping — Valid Ranges.
 *
 * For any integer values for maxBatchSize, maxConcurrentBatches,
 * and mapReduceThreshold, `MapReduceConfig(...).validated()` SHALL
 * return a config with all three clamped fields within valid ranges.
 *
 * **Validates: Requirements 2.7, 9.1, 9.3**
 */
@OptIn(ExperimentalKotest::class)
class MapReduceConfigPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * **Validates: Requirements 2.7, 9.1, 9.3**
     *
     * validated() always clamps maxBatchSize to 5..100,
     * maxConcurrentBatches to 1..5, and mapReduceThreshold
     * to 50..1000, regardless of the original input values.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 2: Clamping")
    fun `validated clamps all fields into valid ranges`() {
        runBlocking {
            checkAll(cfg, Arb.arbMapReduceConfig()) { raw ->
                val v = raw.validated()
                assertTrue(v.maxBatchSize in 5..100) {
                    "maxBatchSize ${v.maxBatchSize} out of 5..100 (input=${raw.maxBatchSize})"
                }
                assertTrue(v.maxConcurrentBatches in 1..5) {
                    "maxConcurrentBatches ${v.maxConcurrentBatches} out of 1..5 (input=${raw.maxConcurrentBatches})"
                }
                assertTrue(v.mapReduceThreshold in 50..1000) {
                    "mapReduceThreshold ${v.mapReduceThreshold} out of 50..1000 (input=${raw.mapReduceThreshold})"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 2.7, 9.1, 9.3**
     *
     * validated() is idempotent — calling it twice yields the same result.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 2: Clamping")
    fun `validated is idempotent`() {
        runBlocking {
            checkAll(cfg, Arb.arbMapReduceConfig()) { raw ->
                val once = raw.validated()
                val twice = once.validated()
                assertTrue(once == twice) {
                    "validated() not idempotent: first=$once, second=$twice"
                }
            }
        }
    }
}
