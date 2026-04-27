package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for CascadingAnalysisEngineImpl — safety limit, failure handling,
 * log entries, and CascadeResult counters.
 * Validates: Requirements 26.7, 26.2, log statuses, counters.
 */
class CascadingAnalysisLimitAndLogTest {

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

    // ── Req 26.7: Safety limit stops cascade ──

    @Test
    fun `cascade stops at safety limit`() = runTest {
        // Build chain: L-1 → L-2 → L-3 → L-4 → ...
        for (i in 1..10) {
            extractor.contentMap["L-$i"] = contentWithLinks("L-${i + 1}")
        }
        val result = engine(maxTickets = 3).cascade("L-1")
        assertTrue(orchestrator.analyzedTickets.size <= 4, // 3 limit + 1 seed
            "Should stop near safety limit, got ${orchestrator.analyzedTickets.size}")
        val cascadeLog = result.logEntries.any {
            it.status == CascadeLogStatus.CASCADE && it.message.contains("Safety limit")
        }
        assertTrue(cascadeLog, "Expected CASCADE log about safety limit")
    }

    // ── Failure handling ──

    @Test
    fun `cascade records failed ticket and continues`() = runTest {
        orchestrator.shouldFail = setOf("F-2")
        extractor.contentMap["F-1"] = contentWithLinks("F-2", "F-3")
        val result = engine().cascade("F-1")
        assertEquals(2, result.completedTickets) // F-1 and F-3
        assertEquals(1, result.failedTickets)    // F-2
        val failLog = result.logEntries.any {
            it.status == CascadeLogStatus.FAILED && it.ticketKey == "F-2"
        }
        assertTrue(failLog, "Expected FAILED log for F-2")
    }

    @Test
    fun `cascade status is FAILED when all tickets fail`() = runTest {
        orchestrator.shouldFail = setOf("X-1")
        val result = engine().cascade("X-1")
        assertEquals(CascadeStatus.FAILED, result.status)
        assertEquals(0, result.completedTickets)
        assertEquals(1, result.failedTickets)
    }

    // ── Log entries ──

    @Test
    fun `cascade emits CASCADE log at start`() = runTest {
        val result = engine().cascade("PROJ-1")
        val startLog = result.logEntries.first()
        assertEquals(CascadeLogStatus.CASCADE, startLog.status)
        assertTrue(startLog.message.contains("Cascade started"))
    }

    @Test
    fun `cascade emits ANALYZING and COMPLETED for analyzed ticket`() = runTest {
        val result = engine().cascade("PROJ-1")
        val analyzing = result.logEntries.any {
            it.status == CascadeLogStatus.ANALYZING && it.ticketKey == "PROJ-1"
        }
        val completed = result.logEntries.any {
            it.status == CascadeLogStatus.COMPLETED && it.ticketKey == "PROJ-1"
        }
        assertTrue(analyzing, "Expected ANALYZING log")
        assertTrue(completed, "Expected COMPLETED log")
    }

    @Test
    fun `cascade emits DISCOVERED for newly found related tickets`() = runTest {
        extractor.contentMap["PROJ-1"] = contentWithLinks("PROJ-2")
        val result = engine().cascade("PROJ-1")
        val discovered = result.logEntries.any {
            it.status == CascadeLogStatus.DISCOVERED && it.ticketKey == "PROJ-2"
        }
        assertTrue(discovered, "Expected DISCOVERED log for PROJ-2")
    }

    @Test
    fun `cascade emits DONE log at end`() = runTest {
        val result = engine().cascade("PROJ-1")
        val done = result.logEntries.last()
        assertEquals(CascadeLogStatus.DONE, done.status)
        assertTrue(done.message.contains("Cascade done"))
    }

    // ── CascadeResult counters ──

    @Test
    fun `cascade result counters are accurate`() = runTest {
        kbRepo.existing.add("S-3") // will be skipped
        orchestrator.shouldFail = setOf("S-2") // will fail
        extractor.contentMap["S-1"] = contentWithLinks("S-2", "S-3")
        val result = engine().cascade("S-1")
        assertEquals(1, result.completedTickets) // S-1
        assertEquals(1, result.failedTickets)    // S-2
        assertEquals(3, result.totalTickets)     // S-1, S-2, S-3
    }

    // ── Req 26.2: Mixed KB skip + analyze ──

    @Test
    fun `cascade skips KB tickets and analyzes missing ones`() = runTest {
        kbRepo.existing.add("M-2")
        extractor.contentMap["M-1"] = contentWithLinks("M-2", "M-3")
        val result = engine().cascade("M-1")
        assertTrue("M-2" !in orchestrator.analyzedTickets, "M-2 should be skipped")
        assertTrue("M-3" in orchestrator.analyzedTickets, "M-3 should be analyzed")
        val skipped = result.logEntries.any {
            it.status == CascadeLogStatus.SKIPPED && it.ticketKey == "M-2"
        }
        assertTrue(skipped)
    }

    // ── Sub-tasks discovery ──

    @Test
    fun `cascade discovers sub-tasks`() = runTest {
        extractor.contentMap["P-1"] = contentWithSubTasks("P-SUB-1", "P-SUB-2")
        val result = engine().cascade("P-1")
        assertTrue(orchestrator.analyzedTickets.containsAll(listOf("P-SUB-1", "P-SUB-2")))
        assertEquals(3, result.completedTickets)
    }
}
