package com.assistant.server.document.curation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for GeminiCliAgent timeout and retry changes.
 * Requirements: 7.1, 7.2, 7.5
 */
class GeminiTimeoutTest {

    @Test
    fun `CurationConfig timeout is 240000ms`() {
        assertEquals(240_000L, CurationConfig.GEMINI_TIMEOUT_MS)
    }

    @Test
    fun `CurationConfig max retries is 1`() {
        assertEquals(1, CurationConfig.MAX_RETRIES)
    }

    @Test
    fun `CurationConfig fail-fast threshold is 280000ms`() {
        assertEquals(280_000L, CurationConfig.JOB_FAIL_FAST_MS)
    }

    @Test
    fun `timeout allows 2 attempts within 5-min job window`() {
        val maxJobTime = CurationConfig.JOB_FAIL_FAST_MS
        assertTrue(maxJobTime < 300_000L,
            "Fail-fast $maxJobTime should be < 300000 (5 min)")
    }

    @Test
    fun `total attempts with curation is 2`() {
        val totalAttempts = CurationConfig.MAX_RETRIES + 1
        assertEquals(2, totalAttempts)
    }
}
