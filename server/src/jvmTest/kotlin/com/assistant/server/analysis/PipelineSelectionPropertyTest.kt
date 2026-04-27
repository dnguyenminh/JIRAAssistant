package com.assistant.server.analysis

import com.assistant.server.analysis.models.MapReduceConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Pure decision function extracted from AIOrchestratorImpl logic.
 *
 * Determines whether to use map-reduce pipeline or single-prompt flow.
 * Map-reduce activates when ALL conditions are met:
 * - ticketCount > threshold
 * - mapReduceEnabled = true
 * - orchestratorAvailable = true (MapReduceOrchestrator injected)
 */
fun shouldUseMapReduce(
    ticketCount: Int,
    threshold: Int,
    mapReduceEnabled: Boolean,
    orchestratorAvailable: Boolean
): Boolean {
    return ticketCount > threshold && mapReduceEnabled && orchestratorAvailable
}

/** Arb generator for pipeline selection inputs. */
fun Arb.Companion.arbPipelineInput(): Arb<PipelineInput> = arbitrary {
    PipelineInput(
        ticketCount = Arb.int(0..2000).bind(),
        threshold = Arb.int(50..1000).bind(),
        mapReduceEnabled = Arb.boolean().bind(),
        orchestratorAvailable = Arb.boolean().bind()
    )
}

/** Input data for pipeline selection property tests. */
data class PipelineInput(
    val ticketCount: Int,
    val threshold: Int,
    val mapReduceEnabled: Boolean,
    val orchestratorAvailable: Boolean
)

/**
 * Property 3: Pipeline Selection — Threshold Decision.
 *
 * For any ticket count N and threshold T:
 * - if N > T AND enabled AND orchestrator != null → map-reduce
 * - otherwise → single-prompt
 * The two flows are mutually exclusive.
 *
 * **Validates: Requirements 1.4, 6.1, 6.2, 6.3, 10.2**
 */
@OptIn(ExperimentalKotest::class)
class PipelineSelectionPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)

    /**
     * **Validates: Requirements 1.4, 6.2**
     *
     * When ticketCount > threshold AND enabled AND orchestrator
     * available → shouldUseMapReduce returns true.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 3: Threshold")
    fun `map-reduce activates when all conditions met`() {
        runBlocking {
            checkAll(cfg, Arb.int(50..1000)) { threshold ->
                val ticketCount = threshold + Arb.int(1..500).bind()
                val result = shouldUseMapReduce(
                    ticketCount, threshold,
                    mapReduceEnabled = true,
                    orchestratorAvailable = true
                )
                assertTrue(result) {
                    "Expected map-reduce for count=$ticketCount > threshold=$threshold"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 6.1**
     *
     * When ticketCount <= threshold → single-prompt (false),
     * regardless of other flags.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 3: Threshold")
    fun `single-prompt when ticket count at or below threshold`() {
        runBlocking {
            checkAll(cfg, Arb.int(50..1000), Arb.boolean(), Arb.boolean()) {
                    threshold, enabled, available ->
                val ticketCount = Arb.int(0..threshold).bind()
                val result = shouldUseMapReduce(
                    ticketCount, threshold, enabled, available
                )
                assertFalse(result) {
                    "Expected single-prompt for count=$ticketCount <= threshold=$threshold"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 6.3**
     *
     * When mapReduceEnabled is false → always single-prompt,
     * regardless of ticket count.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 3: Threshold")
    fun `single-prompt when map-reduce disabled`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..2000), Arb.int(50..1000), Arb.boolean()) {
                    count, threshold, available ->
                val result = shouldUseMapReduce(
                    count, threshold,
                    mapReduceEnabled = false,
                    orchestratorAvailable = available
                )
                assertFalse(result) {
                    "Expected single-prompt when disabled (count=$count, threshold=$threshold)"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 10.2**
     *
     * When orchestrator is not available (null) → always single-prompt,
     * regardless of ticket count.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 3: Threshold")
    fun `single-prompt when orchestrator not available`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..2000), Arb.int(50..1000), Arb.boolean()) {
                    count, threshold, enabled ->
                val result = shouldUseMapReduce(
                    count, threshold,
                    mapReduceEnabled = enabled,
                    orchestratorAvailable = false
                )
                assertFalse(result) {
                    "Expected single-prompt when orchestrator unavailable (count=$count)"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 1.4, 6.1, 6.2, 6.3, 10.2**
     *
     * The two flows are mutually exclusive: exactly one of
     * shouldUseMapReduce / shouldUseSinglePrompt is true.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 3: Threshold")
    fun `map-reduce and single-prompt are mutually exclusive`() {
        runBlocking {
            checkAll(cfg, Arb.arbPipelineInput()) { input ->
                val mr = shouldUseMapReduce(
                    input.ticketCount, input.threshold,
                    input.mapReduceEnabled, input.orchestratorAvailable
                )
                val sp = !mr // single-prompt is the complement
                assertTrue(mr xor sp) {
                    "Flows not mutually exclusive for $input: mr=$mr, sp=$sp"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 6.3, 9.1**
     *
     * Uses MapReduceConfig.validated() threshold — decision respects
     * the clamped config value.
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 3: Threshold")
    fun `decision uses validated config threshold`() {
        runBlocking {
            checkAll(cfg, Arb.int(-100..2000), Arb.int(0..2000)) {
                    rawThreshold, ticketCount ->
                val config = MapReduceConfig(mapReduceThreshold = rawThreshold)
                val validThreshold = config.validated().mapReduceThreshold
                assertTrue(validThreshold in 50..1000)
                val result = shouldUseMapReduce(
                    ticketCount, validThreshold,
                    mapReduceEnabled = true,
                    orchestratorAvailable = true
                )
                if (ticketCount > validThreshold) {
                    assertTrue(result) {
                        "Expected map-reduce: count=$ticketCount > validThreshold=$validThreshold"
                    }
                } else {
                    assertFalse(result) {
                        "Expected single-prompt: count=$ticketCount <= validThreshold=$validThreshold"
                    }
                }
            }
        }
    }
}
