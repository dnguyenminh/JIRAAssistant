package com.assistant.ai.deepanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ScrumPointsValidator.
 * Validates: Requirements 25.4
 */
class ScrumPointsValidatorTest {

    // ── Req 25.4: Valid values pass through unchanged ──

    @Test
    fun `normalize returns 0 for 0`() {
        assertEquals(0.0, ScrumPointsValidator.normalize(0.0))
    }

    @Test
    fun `normalize returns 0_5 for 0_5`() {
        assertEquals(0.5, ScrumPointsValidator.normalize(0.5))
    }

    @Test
    fun `normalize returns exact valid values unchanged`() {
        val validPoints = listOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0)
        validPoints.forEach { point ->
            assertEquals(point, ScrumPointsValidator.normalize(point), "Expected $point unchanged")
        }
    }

    // ── Req 25.4: Invalid values rounded to nearest valid ──

    @Test
    fun `normalize rounds 0_3 to 0_5`() {
        assertEquals(0.5, ScrumPointsValidator.normalize(0.3))
    }

    @Test
    fun `normalize rounds 1_5 to nearest valid (1 or 2)`() {
        val result = ScrumPointsValidator.normalize(1.5)
        assertTrue(result == 1.0 || result == 2.0, "1.5 should round to 1.0 or 2.0, got $result")
    }

    @Test
    fun `normalize rounds 4 to nearest valid (3 or 5)`() {
        val result = ScrumPointsValidator.normalize(4.0)
        assertTrue(result == 3.0 || result == 5.0)
    }

    @Test
    fun `normalize rounds 6 to 5`() {
        assertEquals(5.0, ScrumPointsValidator.normalize(6.0))
    }

    @Test
    fun `normalize rounds 10 to 8`() {
        assertEquals(8.0, ScrumPointsValidator.normalize(10.0))
    }

    @Test
    fun `normalize rounds 17 to nearest valid (13 or 21)`() {
        val result = ScrumPointsValidator.normalize(17.0)
        assertTrue(result == 13.0 || result == 21.0)
    }

    @Test
    fun `normalize rounds 30 to 21`() {
        assertEquals(21.0, ScrumPointsValidator.normalize(30.0))
    }

    @Test
    fun `normalize rounds 50 to 40`() {
        assertEquals(40.0, ScrumPointsValidator.normalize(50.0))
    }

    @Test
    fun `normalize rounds 100 to 40`() {
        assertEquals(40.0, ScrumPointsValidator.normalize(100.0))
    }

    // ── Edge cases: negative values ──

    @Test
    fun `normalize clamps negative to 0`() {
        assertEquals(0.0, ScrumPointsValidator.normalize(-1.0))
    }

    @Test
    fun `normalize clamps large negative to 0`() {
        assertEquals(0.0, ScrumPointsValidator.normalize(-100.0))
    }

    // ── isValid ──

    @Test
    fun `isValid returns true for all valid points`() {
        val validPoints = listOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0)
        validPoints.forEach { point ->
            assertTrue(ScrumPointsValidator.isValid(point), "$point should be valid")
        }
    }

    @Test
    fun `isValid returns false for invalid values`() {
        listOf(0.3, 1.5, 4.0, 6.0, 10.0, 15.0, 25.0, 50.0).forEach { point ->
            assertFalse(ScrumPointsValidator.isValid(point), "$point should be invalid")
        }
    }

    @Test
    fun `isValid returns false for negative values`() {
        assertFalse(ScrumPointsValidator.isValid(-1.0))
    }

    // ── Scrum Points validation in parser pipeline ──

    @Test
    fun `parser normalizes invalid scrum points in response`() {
        val parser = DeepAnalysisResponseParserImpl()
        val json = """{"complexity":{"scrumPoints":6.5,"description":"test"}}"""
        val result = parser.parse("T-1", json)
        assertTrue(ScrumPointsValidator.isValid(result.complexity.scrumPoints))
    }
}
