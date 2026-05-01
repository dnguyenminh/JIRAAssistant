package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.server.agent.ba.subprocess.pipeline.models.StepResponse
import com.assistant.server.agent.ba.subprocess.pipeline.models.StopReason
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property-based tests for [PipelineStopCondition].
 *
 * Property 3: Pipeline termination guarantee
 * Property 4: Loop detection accuracy
 */
@OptIn(ExperimentalKotest::class)
@Tag("multi-turn-ba-orchestration")
class PipelineStopConditionTest {

    private val condition = PipelineStopCondition()

    // ── Generators ──────────────────────────────────────

    private val arbStepResponse: Arb<StepResponse> = arbitrary {
        StepResponse(
            stepName = Arb.element("analysis", "requirements", "writing", "review").bind(),
            content = Arb.string(0..200).bind(),
            durationMs = Arb.long(0L..5000L).bind(),
            timedOut = Arb.boolean().bind(),
            isEmpty = Arb.boolean().bind()
        )
    }

    // ── Property 3: Pipeline termination guarantee ──────

    /**
     * **Property 3: Pipeline termination guarantee**
     *
     * For any sequence of StepResponses (empty, repeated, low quality),
     * evaluate() returns shouldStop=true within N turns, where N is
     * determined by: quality pass (any turn), 3 consecutive failures,
     * or loop detection (2 identical responses).
     *
     * **Validates: Requirements 2.2, 2.3**
     */
    @Test
    fun `Property 3a - quality pass stops pipeline immediately`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            arbStepResponse,
            Arb.list(arbStepResponse, 0..5),
            Arb.int(0..10)
        ) { current, previous, failures ->
            val decision = condition.evaluate(current, previous, true, failures)
            assertTrue(decision.shouldStop, "Quality passed must always stop")
            assertEquals(StopReason.QUALITY_PASSED, decision.reason)
        }
    }

    @Test
    fun `Property 3b - 3 consecutive failures stops pipeline`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            arbStepResponse,
            Arb.list(arbStepResponse, 0..5),
            Arb.int(3..20)
        ) { current, previous, failures ->
            val decision = condition.evaluate(current, previous, false, failures)
            assertTrue(decision.shouldStop, ">=3 consecutive failures must stop")
            assertEquals(StopReason.CONSECUTIVE_FAILURES, decision.reason)
        }
    }

    @Test
    fun `Property 3c - loop detection stops on identical responses`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.string(10..500)
        ) { content ->
            if (content.isBlank()) return@checkAll
            val current = StepResponse("writing", content, 100L)
            val previous = listOf(StepResponse("writing", content, 100L))
            val decision = condition.evaluate(current, previous, false, 0)
            assertTrue(decision.shouldStop, "Identical responses must trigger loop detection")
            assertEquals(StopReason.LOOP_DETECTED, decision.reason)
        }
    }

    // ── Property 4: Loop detection accuracy ─────────────

    /**
     * **Property 4: Loop detection accuracy**
     *
     * For any two text strings a and b:
     * - If jaccardSimilarity(a, b) > 0.90 → evaluate detects loop
     * - If jaccardSimilarity(a, b) ≤ 0.90 → evaluate does NOT detect loop
     *
     * **Validates: Requirements 2.3, 2.4**
     */
    @Test
    fun `Property 4a - high similarity triggers loop detection`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.string(10..500),
            Arb.string(10..500)
        ) { a, b ->
            if (a.isBlank() || b.isBlank()) return@checkAll
            val similarity = jaccardSimilarity(a, b)
            val current = StepResponse("writing", a, 100L)
            val previous = listOf(StepResponse("writing", b, 100L))
            val decision = condition.evaluate(current, previous, false, 0)

            if (similarity > PipelineStopCondition.SIMILARITY_THRESHOLD) {
                assertTrue(
                    decision.shouldStop && decision.reason == StopReason.LOOP_DETECTED,
                    "Similarity $similarity > threshold must detect loop"
                )
            }
        }
    }

    @Test
    fun `Property 4b - low similarity does not trigger loop`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.string(10..500),
            Arb.string(10..500)
        ) { a, b ->
            if (a.isBlank() || b.isBlank()) return@checkAll
            val similarity = jaccardSimilarity(a, b)
            val current = StepResponse("writing", a, 100L)
            val previous = listOf(StepResponse("writing", b, 100L))
            val decision = condition.evaluate(current, previous, false, 0)

            if (similarity <= PipelineStopCondition.SIMILARITY_THRESHOLD) {
                val isLoopDetected = decision.shouldStop &&
                    decision.reason == StopReason.LOOP_DETECTED
                assertFalse(
                    isLoopDetected,
                    "Similarity $similarity <= threshold must NOT detect loop"
                )
            }
        }
    }
}
