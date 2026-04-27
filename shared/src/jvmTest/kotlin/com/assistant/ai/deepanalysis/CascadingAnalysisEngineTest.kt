package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for CascadingAnalysisEngineImpl.
 * Validates: Requirements 26.1-26.7, log entries, CascadeResult counters.
 */
class CascadingAnalysisEngineTest {

    private val orchestrator = FakeCascadeOrchestrator()
    private val extractor = FakeCascadeExtractor()
    private val kbRepo = FakeCascadeKBRepo()
    private val semaphore = Semaphore(1)

    private fun engine(maxTickets: Int = 50) = CascadingAnalysisEngineImpl(
        aiOrchestrator = orchestrator,
        jiraContentExtractor = extractor,
        kbRepository = kbRepo,
        aiSemaphore = semaphore,
        maxTickets = maxTickets
    )

    // ── Req 26.3: Uses AIOrchestrator.analyzeTicket() ──

    @Test
    fun `cascade analyzes seed ticket via AIOrchestrator`() = runTest {
        val result = engine().cascade("PROJ-1")
        assertTrue(orchestrator.analyzedTickets.contains("PROJ-1"))
        assertEquals(CascadeStatus.COMPLETED, result.status)
    }

    // ── Req 26.2: Skips tickets already in KB ──

    @Test
    fun `cascade skips ticket already in KB`() = runTest {
        kbRepo.existing.add("PROJ-1")
        val result = engine().cascade("PROJ-1")
        assertTrue(orchestrator.analyzedTickets.isEmpty())
        assertEquals(0, result.completedTickets)
        val skipped = result.logEntries.any {
            it.status == CascadeLogStatus.SKIPPED && it.ticketKey == "PROJ-1"
        }
        assertTrue(skipped, "Expected SKIPPED log for PROJ-1")
    }

    // ── Req 26.1: Discovers related tickets ──

    @Test
    fun `cascade discovers and analyzes related tickets`() = runTest {
        extractor.contentMap["PROJ-1"] = contentWithLinks("PROJ-2", "PROJ-3")
        val result = engine().cascade("PROJ-1")
        assertTrue(orchestrator.analyzedTickets.containsAll(listOf("PROJ-1", "PROJ-2", "PROJ-3")))
        assertEquals(3, result.totalTickets)
        assertEquals(3, result.completedTickets)
    }

    // ── Req 26.5: BFS recursive — continues until no more ──

    @Test
    fun `cascade follows BFS recursively through chain`() = runTest {
        extractor.contentMap["A-1"] = contentWithLinks("A-2")
        extractor.contentMap["A-2"] = contentWithLinks("A-3")
        // A-3 has no further links
        val result = engine().cascade("A-1")
        assertEquals(listOf("A-1", "A-2", "A-3"), orchestrator.analyzedTickets)
        assertEquals(3, result.completedTickets)
    }

    // ── Req 26.6: Visited set prevents loops ──

    @Test
    fun `cascade does not revisit tickets in a cycle`() = runTest {
        extractor.contentMap["C-1"] = contentWithLinks("C-2")
        extractor.contentMap["C-2"] = contentWithLinks("C-1") // cycle back
        val result = engine().cascade("C-1")
        assertEquals(2, orchestrator.analyzedTickets.size)
        assertEquals(2, result.completedTickets)
    }

    // ── Req 26.6: Visited set prevents duplicates ──

    @Test
    fun `cascade deduplicates tickets discovered from multiple parents`() = runTest {
        extractor.contentMap["D-1"] = contentWithLinks("D-2", "D-3")
        extractor.contentMap["D-2"] = contentWithLinks("D-3") // D-3 again
        val result = engine().cascade("D-1")
        val d3Count = orchestrator.analyzedTickets.count { it == "D-3" }
        assertEquals(1, d3Count, "D-3 should be analyzed exactly once")
        assertEquals(3, result.totalTickets)
    }
}
