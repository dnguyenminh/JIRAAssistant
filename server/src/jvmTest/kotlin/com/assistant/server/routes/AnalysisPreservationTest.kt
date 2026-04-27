package com.assistant.server.routes

import com.assistant.ai.AnalysisPhase
import com.assistant.ai.AnalysisStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Preservation property tests — Non-Attachment and Failure Cases Unchanged.
 *
 * Validates: Requirements 3.1, 3.2, 3.4
 *
 * These tests MUST PASS on unfixed code — they capture baseline behavior
 * that must be preserved after the attachment processing fix.
 *
 * Approach: Since `runAnalysis()` is private, we use:
 * - Structural tests: verify source code patterns in AnalysisRoutes.kt
 * - Behavioral tests: verify AnalysisStatusTracker (public API) directly
 */
class AnalysisPreservationTest {

    private val sourceFile = java.io.File(
        "src/jvmMain/kotlin/com/assistant/server/routes/AnalysisRoutes.kt"
    )

    @BeforeTest
    fun setup() {
        assertTrue(sourceFile.exists(), "AnalysisRoutes.kt should exist")
    }

    // ---------------------------------------------------------------
    // Property 2a: No-attachment tickets complete analysis normally
    // Validates: Requirement 3.2
    // ---------------------------------------------------------------

    /**
     * Structural: runAnalysis() calls orchestrator.analyzeTicket() and
     * returns its result. This is the core analysis flow for ALL tickets
     * (with or without attachments).
     */
    @Test
    fun `runAnalysis calls orchestrator analyzeTicket and returns result`() {
        val source = sourceFile.readText()

        assertTrue(
            source.contains("orchestrator.analyzeTicket("),
            "runAnalysis() must call orchestrator.analyzeTicket() — " +
                "this is the core analysis path for all tickets"
        )
        assertTrue(
            source.contains("return result"),
            "runAnalysis() must return the analysis result from orchestrator"
        )
    }

    /**
     * Structural: runAnalysis() accepts ticketId, orchestrator, and
     * forceReanalyze — no attachment-related parameters required for
     * the basic analysis flow.
     */
    @Test
    fun `runAnalysis signature includes ticketId orchestrator and forceReanalyze`() {
        val source = sourceFile.readText()

        assertTrue(
            source.contains("fun runAnalysis("),
            "runAnalysis function must exist in AnalysisRoutes.kt"
        )
        assertTrue(
            source.contains("ticketId: String") && source.contains("orchestrator: AIOrchestrator"),
            "runAnalysis must accept ticketId and orchestrator parameters"
        )
    }

    // ---------------------------------------------------------------
    // Property 2b: AI analysis failure propagates errors
    // Validates: Requirement 3.4
    // ---------------------------------------------------------------

    /**
     * Structural: runAnalysis() uses try/finally — exceptions from
     * orchestrator.analyzeTicket() propagate to the caller (no catch
     * block swallowing the error in the main flow).
     */
    @Test
    fun `runAnalysis uses try-finally so orchestrator exceptions propagate`() {
        val source = sourceFile.readText()

        // The function uses try { ... } finally { ... } — NOT try-catch
        // This means orchestrator exceptions propagate to the caller
        assertTrue(
            source.contains("} finally {"),
            "runAnalysis() must use try/finally to ensure status cleanup " +
                "while letting orchestrator exceptions propagate"
        )
    }

    /**
     * Structural: The finally block calls AnalysisStatusTracker.remove()
     * to clean up status even when orchestrator throws.
     */
    @Test
    fun `finally block removes status tracking on both success and failure`() {
        val source = sourceFile.readText()

        // Extract the finally block content
        val finallyIdx = source.indexOf("} finally {")
        assertTrue(finallyIdx > 0, "finally block must exist")

        val afterFinally = source.substring(finallyIdx)
        assertTrue(
            afterFinally.contains("AnalysisStatusTracker.remove(ticketId)"),
            "finally block must call AnalysisStatusTracker.remove(ticketId) " +
                "to clean up status on both success and failure paths"
        )
    }

    // ---------------------------------------------------------------
    // Property 2c: Status tracking phases fire in correct order
    // Validates: Design "Analysis response format và status tracking không thay đổi"
    // ---------------------------------------------------------------

    /**
     * Behavioral: AnalysisStatusTracker.updatePhase() correctly stores
     * phase and progress for each AnalysisPhase value.
     */
    @Test
    fun `AnalysisStatusTracker updatePhase stores correct phase and progress`() {
        val ticketId = "TEST-100"

        for (phase in AnalysisPhase.values()) {
            AnalysisStatusTracker.updatePhase(ticketId, phase)
            val status = AnalysisStatusTracker.get(ticketId)
            assertNotNull(status)
            assertEquals(phase.name, status.phase)
            assertEquals(phase.startPercent, status.progressPercent)
        }

        // Cleanup
        AnalysisStatusTracker.remove(ticketId)
    }

    /**
     * Behavioral: AnalysisStatusTracker.remove() clears the status,
     * simulating the finally block behavior in runAnalysis().
     */
    @Test
    fun `AnalysisStatusTracker remove clears status after completion`() {
        val ticketId = "TEST-200"

        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.COMPLETE)
        assertNotNull(AnalysisStatusTracker.get(ticketId))

        AnalysisStatusTracker.remove(ticketId)
        assertNull(AnalysisStatusTracker.get(ticketId))
    }

    /**
     * Structural: runAnalysis() calls updatePhase in the correct order:
     * FETCHING_JIRA → EXTRACTING_CONTENT → AI_ANALYZING → (analyzeTicket)
     * → KB_SYNCING → COMPLETE, then remove in finally.
     */
    @Test
    fun `runAnalysis fires status phases in correct order`() {
        val source = sourceFile.readText()

        // Extract the runAnalysis function body
        val fnStart = source.indexOf("fun runAnalysis(")
        assertTrue(fnStart > 0, "runAnalysis function must exist")
        val fnBody = source.substring(fnStart)

        // Verify phase order by checking relative positions
        val phases = listOf(
            "AnalysisPhase.FETCHING_JIRA",
            "AnalysisPhase.EXTRACTING_CONTENT",
            "AnalysisPhase.AI_ANALYZING",
            "orchestrator.analyzeTicket(",
            "AnalysisPhase.KB_SYNCING",
            "AnalysisPhase.COMPLETE"
        )

        var lastIdx = -1
        for (phase in phases) {
            val idx = fnBody.indexOf(phase)
            assertTrue(
                idx > lastIdx,
                "Phase '$phase' must appear after previous phase in runAnalysis(). " +
                    "Expected order: FETCHING_JIRA → EXTRACTING_CONTENT → AI_ANALYZING → " +
                    "analyzeTicket → KB_SYNCING → COMPLETE"
            )
            lastIdx = idx
        }
    }

    /**
     * Behavioral: Simulates the full phase sequence that runAnalysis()
     * performs, verifying each phase is trackable and the final remove
     * cleans up — matching the observed behavior on unfixed code.
     */
    @Test
    fun `full phase sequence matches runAnalysis behavior`() {
        val ticketId = "TEST-300"

        // Simulate runAnalysis() phase sequence
        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.FETCHING_JIRA)
        assertEquals("FETCHING_JIRA", AnalysisStatusTracker.get(ticketId)?.phase)

        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.EXTRACTING_CONTENT)
        assertEquals("EXTRACTING_CONTENT", AnalysisStatusTracker.get(ticketId)?.phase)

        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.AI_ANALYZING)
        assertEquals("AI_ANALYZING", AnalysisStatusTracker.get(ticketId)?.phase)

        // orchestrator.analyzeTicket() would run here

        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.KB_SYNCING)
        assertEquals("KB_SYNCING", AnalysisStatusTracker.get(ticketId)?.phase)

        AnalysisStatusTracker.updatePhase(ticketId, AnalysisPhase.COMPLETE)
        assertEquals("COMPLETE", AnalysisStatusTracker.get(ticketId)?.phase)
        assertEquals(100, AnalysisStatusTracker.get(ticketId)?.progressPercent)

        // finally block
        AnalysisStatusTracker.remove(ticketId)
        assertNull(
            AnalysisStatusTracker.get(ticketId),
            "Status must be removed after completion (finally block)"
        )
    }
}
