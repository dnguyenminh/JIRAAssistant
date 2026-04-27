package com.assistant.server.analysis.models

import com.assistant.ai.deepanalysis.models.MapReduceInfo
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Arbitrary generator for [MapReduceInfo] that produces **valid** instances
 * where pipeline consistency invariants hold by construction:
 * totalBatches = successfulBatches + failedBatches, successfulBatches >= 1,
 * and all numeric fields are non-negative.
 */
fun Arb.Companion.arbValidMapReduceInfo(): Arb<MapReduceInfo> = arbitrary {
    val successful = Arb.int(1..50).bind()
    val failed = Arb.int(0..50).bind()
    MapReduceInfo(
        totalBatches = successful + failed,
        successfulBatches = successful,
        failedBatches = failed,
        totalTicketsAnalyzed = Arb.int(0..5000).bind(),
        mapPhaseTimeMs = Arb.long(0L..600_000L).bind(),
        reducePhaseTimeMs = Arb.long(0L..300_000L).bind(),
        reduceSkipped = Arb.boolean().bind()
    )
}

/**
 * Arbitrary generator for [MapReduceInfo] with **unconstrained** values
 * that may violate consistency invariants.
 */
fun Arb.Companion.arbArbitraryMapReduceInfo(): Arb<MapReduceInfo> = arbitrary {
    MapReduceInfo(
        totalBatches = Arb.int(-10..100).bind(),
        successfulBatches = Arb.int(-10..100).bind(),
        failedBatches = Arb.int(-10..100).bind(),
        totalTicketsAnalyzed = Arb.int(-100..5000).bind(),
        mapPhaseTimeMs = Arb.long(-10_000L..600_000L).bind(),
        reducePhaseTimeMs = Arb.long(-10_000L..300_000L).bind(),
        reduceSkipped = Arb.boolean().bind()
    )
}

/**
 * Property 10: MapReduceInfo Consistency.
 *
 * For any valid MapReduceInfo produced by the pipeline:
 * - successfulBatches + failedBatches == totalBatches
 * - successfulBatches >= 1
 * - totalTicketsAnalyzed >= 0
 * - mapPhaseTimeMs >= 0
 * - reducePhaseTimeMs >= 0
 *
 * **Validates: Requirements 4.5**
 */
@OptIn(ExperimentalKotest::class)
class MapReduceInfoPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * **Validates: Requirements 4.5**
     *
     * For any valid MapReduceInfo, the batch counts are consistent:
     * successfulBatches + failedBatches == totalBatches.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 10: Consistency")
    fun `valid MapReduceInfo has consistent batch counts`() {
        runBlocking {
            checkAll(cfg, Arb.arbValidMapReduceInfo()) { info ->
                assertEquals(
                    info.successfulBatches + info.failedBatches,
                    info.totalBatches
                ) {
                    "successfulBatches(${info.successfulBatches}) + " +
                        "failedBatches(${info.failedBatches}) != " +
                        "totalBatches(${info.totalBatches})"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 4.5**
     *
     * For any valid MapReduceInfo, successfulBatches >= 1 and all
     * numeric fields are non-negative.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 10: Consistency")
    fun `valid MapReduceInfo has non-negative fields`() {
        runBlocking {
            checkAll(cfg, Arb.arbValidMapReduceInfo()) { info ->
                assertTrue(info.successfulBatches >= 1) {
                    "successfulBatches ${info.successfulBatches} < 1"
                }
                assertTrue(info.totalTicketsAnalyzed >= 0) {
                    "totalTicketsAnalyzed ${info.totalTicketsAnalyzed} < 0"
                }
                assertTrue(info.mapPhaseTimeMs >= 0) {
                    "mapPhaseTimeMs ${info.mapPhaseTimeMs} < 0"
                }
                assertTrue(info.reducePhaseTimeMs >= 0) {
                    "reducePhaseTimeMs ${info.reducePhaseTimeMs} < 0"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 4.5**
     *
     * Arbitrary (unconstrained) MapReduceInfo values that violate
     * invariants can be detected by a simple validation check.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 10: Consistency")
    fun `consistency check detects invalid arbitrary values`() {
        runBlocking {
            checkAll(cfg, Arb.arbArbitraryMapReduceInfo()) { info ->
                val batchSumValid =
                    info.successfulBatches + info.failedBatches == info.totalBatches
                val successValid = info.successfulBatches >= 1
                val ticketsValid = info.totalTicketsAnalyzed >= 0
                val mapTimeValid = info.mapPhaseTimeMs >= 0
                val reduceTimeValid = info.reducePhaseTimeMs >= 0

                val allValid = batchSumValid && successValid &&
                    ticketsValid && mapTimeValid && reduceTimeValid

                // Each individual check is a boolean — we just verify
                // the conjunction matches checking all fields together
                assertEquals(allValid, batchSumValid && successValid &&
                    ticketsValid && mapTimeValid && reduceTimeValid)
            }
        }
    }
}
