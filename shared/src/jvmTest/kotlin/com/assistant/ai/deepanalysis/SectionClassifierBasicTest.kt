package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.ExtractionConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SectionClassifierImpl — basic classification,
 * As-Is/To-Be heading recognition, fallback behavior, confidence levels.
 * Requirements: 17.1, 17.6, 25.6
 */
class SectionClassifierBasicTest {

    private val classifier = SectionClassifierImpl()

    // ── Req 17.1: As-Is / To-Be heading recognition ──

    @Test
    fun `classify extracts As-Is section under heading`() {
        val desc = """
            ## As-Is
            Current system uses polling every 5 seconds.
            ## To-Be
            Switch to WebSocket for real-time updates.
        """.trimIndent()
        val result = classifier.classify(desc)
        assertTrue(result.asIsState.contains("polling every 5 seconds"))
    }

    @Test
    fun `classify extracts To-Be section under heading`() {
        val desc = """
            ## As-Is
            Manual CSV export.
            ## To-Be
            Automated PDF report generation.
        """.trimIndent()
        val result = classifier.classify(desc)
        assertTrue(result.toBeState.contains("Automated PDF report"))
    }

    @Test
    fun `classify recognizes Current State heading as As-Is`() {
        val desc = """
            ## Current State
            Users must log in each session.
        """.trimIndent()
        val result = classifier.classify(desc)
        assertTrue(result.asIsState.contains("log in each session"))
    }

    @Test
    fun `classify recognizes Expected State heading as To-Be`() {
        val desc = """
            ## Expected State
            SSO integration with SAML 2.0.
        """.trimIndent()
        val result = classifier.classify(desc)
        assertTrue(result.toBeState.contains("SSO integration"))
    }

    // ── Req 17.6: Fallback behavior ──

    @Test
    fun `classify blank description returns LOW confidence`() {
        val result = classifier.classify("")
        assertEquals(ExtractionConfidence.LOW, result.extractionConfidence)
    }

    @Test
    fun `classify blank description preserves raw description`() {
        val result = classifier.classify("   ")
        assertEquals("   ", result.rawDescription)
    }

    @Test
    fun `classify unstructured text returns LOW confidence with raw preserved`() {
        val desc = "Just a plain text description with no sections."
        val result = classifier.classify(desc)
        assertEquals(ExtractionConfidence.LOW, result.extractionConfidence)
        assertEquals(desc, result.rawDescription)
    }

    // ── Confidence levels: HIGH (≥4), MEDIUM (2-3), LOW (0-1) ──

    @Test
    fun `confidence is HIGH when 4 or more sections found`() {
        val desc = """
            ## As-Is
            Old system.
            ## To-Be
            New system.
            ## API
            POST /api/users Create user
            ## Database
            CREATE TABLE users (id INT)
            ## Integration
            https://auth.service.com/oauth
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(ExtractionConfidence.HIGH, result.extractionConfidence)
    }

    @Test
    fun `confidence is MEDIUM when 2-3 sections found`() {
        val desc = """
            ## As-Is
            Old flow.
            ## To-Be
            New flow.
            POST /api/orders Create order
        """.trimIndent()
        val result = classifier.classify(desc)
        assertEquals(ExtractionConfidence.MEDIUM, result.extractionConfidence)
    }

    @Test
    fun `confidence is LOW when 0-1 sections found`() {
        val desc = "Simple description without any structured sections."
        val result = classifier.classify(desc)
        assertEquals(ExtractionConfidence.LOW, result.extractionConfidence)
    }

    // ── countSections / computeConfidence helpers ──

    @Test
    fun `countSections counts asIs and toBe as one section`() {
        val count = countSections("as-is text", "to-be text", 0, 0, 0, 0)
        assertEquals(1, count)
    }

    @Test
    fun `computeConfidence returns correct levels`() {
        assertEquals(ExtractionConfidence.LOW, computeConfidence(0))
        assertEquals(ExtractionConfidence.LOW, computeConfidence(1))
        assertEquals(ExtractionConfidence.MEDIUM, computeConfidence(2))
        assertEquals(ExtractionConfidence.MEDIUM, computeConfidence(3))
        assertEquals(ExtractionConfidence.HIGH, computeConfidence(4))
        assertEquals(ExtractionConfidence.HIGH, computeConfidence(5))
    }
}
