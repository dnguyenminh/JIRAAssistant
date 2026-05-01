package com.assistant.server.jobs

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 4: Progress mapping stays within [35, 85] range.
 *
 * For any streaming progress value in [0, 100], the mapped job
 * progress `35 + (streamingProgress * 50) / 100` SHALL always
 * be in the range [35, 85].
 *
 * **Validates: Requirements 2.2, 3.4**
 *
 * Feature: streaming-generation-progress,
 * Property 4: Progress mapping stays within [35, 85] range
 */
@OptIn(ExperimentalKotest::class)
class ProgressMappingRangePropertyTest {

    private val cfg = PropTestConfig(iterations = 200)

    /**
     * For any streaming progress in [0, 100], mapped job
     * progress must be in [35, 85].
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 4: Progress mapping stays within [35, 85] range")
    fun `Property 4a - mapped progress is always in 35 to 85`() = runTest {
        checkAll(cfg, Arb.int(0..100)) { streamingProgress ->
            val jobProgress = mapStreamingToJobProgress(streamingProgress)
            assertTrue(
                jobProgress in 35..85,
                "jobProgress=$jobProgress for streaming=$streamingProgress " +
                    "must be in [35, 85]"
            )
        }
    }

    /**
     * Boundary: streaming 0 maps to exactly 35.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 4: Progress mapping stays within [35, 85] range")
    fun `Property 4b - streaming 0 maps to 35`() {
        assertEquals(35, mapStreamingToJobProgress(0))
    }

    /**
     * Boundary: streaming 100 maps to exactly 85.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 4: Progress mapping stays within [35, 85] range")
    fun `Property 4c - streaming 100 maps to 85`() {
        assertEquals(85, mapStreamingToJobProgress(100))
    }

    /**
     * Mapping is monotonically non-decreasing: higher streaming
     * progress never produces lower job progress.
     */
    @Test
    @Tag("Feature: streaming-generation-progress, Property 4: Progress mapping stays within [35, 85] range")
    fun `Property 4d - mapping is monotonically non-decreasing`() = runTest {
        checkAll(
            cfg,
            Arb.int(0..99)
        ) { streamingProgress ->
            val current = mapStreamingToJobProgress(streamingProgress)
            val next = mapStreamingToJobProgress(streamingProgress + 1)
            assertTrue(
                next >= current,
                "Mapping decreased: streaming $streamingProgress→" +
                    "${streamingProgress + 1} gave $current→$next"
            )
        }
    }
}
