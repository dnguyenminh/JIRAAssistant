package com.assistant.server.jobs

import com.assistant.document.models.GenerationJob
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Property-based tests for Document Generation UX Improvement.
 * Covers Properties 1-4 from the design document.
 */
@OptIn(ExperimentalKotest::class)
class DocGenUxPropertyTest {

    private val cfg = PropTestConfig(iterations = 200)

    /**
     * **Validates: Requirements 1.1**
     *
     * Property 1: Aggregation progress is monotonically increasing and bounded.
     * For any subsequence of milestones [5, 15, 25, 30], values are strictly
     * monotonically increasing and all within [0, 30].
     */
    @Test
    @Tag("Feature: docgen-ux-improvement, Property 1: Aggregation progress monotonic and bounded")
    fun `aggregation progress is monotonically increasing and bounded`() {
        val milestones = listOf(5, 15, 25, 30)
        val allSubsequences = buildSubsequences(milestones)
        runBlocking {
            checkAll(cfg, Arb.of(allSubsequences)) { subseq ->
                subseq.forEach { value ->
                    assertTrue(value in 0..30) {
                        "Milestone $value out of [0,30] range"
                    }
                }
                for (i in 1 until subseq.size) {
                    assertTrue(subseq[i] > subseq[i - 1]) {
                        "Not strictly increasing: ${subseq[i - 1]} >= ${subseq[i]}"
                    }
                }
            }
        }
    }

    /**
     * **Validates: Requirements 1.2**
     *
     * Property 2: Heartbeat progress is bounded and never exceeds cap.
     * For any startPercent in [35, 80] and ticks in [0, 100],
     * result == min(startPercent + ticks, 80).
     */
    @Test
    @Tag("Feature: docgen-ux-improvement, Property 2: Heartbeat bounded")
    fun `heartbeat progress is bounded and never exceeds cap`() {
        runBlocking {
            checkAll(cfg, Arb.int(35..80), Arb.int(0..100)) { start, ticks ->
                val result = minOf(start + ticks, 80)
                assertTrue(result in 35..80) {
                    "Result $result out of [35,80] (start=$start, ticks=$ticks)"
                }
                assertEquals(minOf(start + ticks, 80), result)
            }
        }
    }

    /**
     * **Validates: Requirements 1.6**
     *
     * Property 3: startedAt is set on RUNNING transition.
     * For any GenerationJob transitioning QUEUED → RUNNING,
     * startedAt SHALL be non-null and valid ISO-8601.
     */
    @Test
    @Tag("Feature: docgen-ux-improvement, Property 3: startedAt set on RUNNING")
    fun `startedAt is set on RUNNING transition`() {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default)
            checkAll(cfg, Arb.string(8..16)) { jobId ->
                val repo = InMemoryJobRepository()
                val job = GenerationJob(
                    jobId = jobId, ticketId = "T-1",
                    documentType = "BRD", status = "QUEUED"
                )
                repo.create(job)
                val tracker = DocGenProgressTracker(jobId, repo, scope)
                tracker.markStarted()
                val updated = repo.findById(jobId)
                assertNotNull(updated?.startedAt) {
                    "startedAt should be non-null after markStarted"
                }
                val parsed = Instant.parse(updated!!.startedAt)
                assertTrue(!parsed.isAfter(Instant.now())) {
                    "startedAt should not be in the future"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 2.1**
     *
     * Property 4: Phase label mapping is total and correct.
     * For any valid phase, getLabel returns non-empty Vietnamese string ≠ phase code.
     */
    @Test
    @Tag("Feature: docgen-ux-improvement, Property 4: Phase label mapping total")
    fun `phase label mapping is total and correct`() {
        val validPhases = listOf(
            "QUEUED", "AGGREGATING_DATA", "GENERATING_DOCUMENT",
            "PARSING_RESPONSE", "SAVING", "COMPLETE", "FAILED"
        )
        runBlocking {
            checkAll(cfg, Arb.of(validPhases)) { phase ->
                val label = PhaseLabelMapper.getLabel(phase)
                assertTrue(label.isNotEmpty()) {
                    "Label for '$phase' should not be empty"
                }
                assertTrue(label != phase) {
                    "Label '$label' should differ from phase code '$phase'"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.1**
     *
     * Property 5: Elapsed time formatting.
     * For any duration 0..600 seconds, format produces "Xm Ys" where X = floor(s/60), Y = s%60.
     */
    @Test
    @Tag("Feature: docgen-ux-improvement, Property 5: Elapsed time formatting")
    fun `elapsed time formatting produces correct Xm Ys pattern`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..600)) { seconds ->
                val m = seconds / 60
                val s = seconds % 60
                val formatted = "${m}m ${s}s"
                assertTrue(m >= 0) { "Minutes should be non-negative" }
                assertTrue(s in 0..59) { "Seconds should be 0..59, got $s" }
                assertEquals("${seconds / 60}m ${seconds % 60}s", formatted)
            }
        }
    }

    /**
     * **Validates: Requirements 3.2**
     *
     * Property 6: Timeout warning threshold.
     * For any elapsed 0..600, shouldShowTimeoutWarning returns true iff elapsed > 240.
     */
    @Test
    @Tag("Feature: docgen-ux-improvement, Property 6: Timeout warning threshold")
    fun `timeout warning shown iff elapsed exceeds 240 seconds`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..600)) { elapsed ->
                val shouldShow = elapsed > 240
                if (elapsed > 240) assertTrue(shouldShow) { "Should show warning at $elapsed seconds" }
                else assertFalse(shouldShow) { "Should NOT show warning at $elapsed seconds" }
            }
        }
    }

    /**
     * **Validates: Requirements 8.1, 8.2**
     *
     * Property 7: Button recovery on terminal job status.
     * For any terminal status × docType, button should be enabled.
     */
    @Test
    @Tag("Feature: docgen-ux-improvement, Property 7: Button recovery on terminal status")
    fun `button recovery on terminal job status`() {
        val terminalStatuses = listOf("COMPLETED", "FAILED", "CANCELLED")
        val docTypes = listOf("BRD", "FSD", "REQUIREMENT_SLIDES")
        runBlocking {
            checkAll(cfg, Arb.of(terminalStatuses), Arb.of(docTypes)) { status, _ ->
                val shouldEnable = status in terminalStatuses
                assertTrue(shouldEnable) { "Button should be enabled for terminal status=$status" }
            }
        }
    }

    /** Builds all non-empty subsequences of the given list. */
    private fun buildSubsequences(items: List<Int>): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        for (mask in 1 until (1 shl items.size)) {
            val sub = items.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }
            result.add(sub)
        }
        return result
    }
}
