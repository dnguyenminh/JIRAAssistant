package com.assistant.ai

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Test — Duplicate Deep Extraction.
 *
 * Property 1: Bug Condition — When map-reduce is enabled, deep analysis
 * is available, extraction succeeds, and linked ticket count ≤ threshold,
 * `jiraContentExtractor.extract()` MUST be called exactly once.
 *
 * Run on UNFIXED code: expect FAILURE (extractCallCount == 2).
 * Run on FIXED code: expect PASS (extractCallCount == 1).
 *
 * **Validates: Requirements 1.1, 1.2, 1.3**
 */
@OptIn(ExperimentalKotest::class)
class DuplicateExtractionBugConditionTest {

    private val pbtConfig = PropTestConfig(iterations = 50)

    // ── Property-Based Test ──

    /**
     * Property 1: Bug Condition — Duplicate Deep Extraction When Below
     * Map-Reduce Threshold.
     *
     * FOR ALL (linkedCount, threshold, forceReanalyze) WHERE
     *   linkedCount in 0..200, threshold in 1..500,
     *   linkedCount <= threshold (bug condition holds):
     *   extractCallCount MUST equal 1.
     *
     * On UNFIXED code this FAILS with extractCallCount == 2.
     * On FIXED code this PASSES with extractCallCount == 1.
     *
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Test
    fun `property - extract called exactly once for all bug condition inputs`(): Unit =
        runBlocking {
            checkAll(
                pbtConfig,
                Arb.int(0..200),
                Arb.int(1..500),
                Arb.boolean()
            ) { linkedCount, rawThreshold, forceReanalyze ->
                val threshold = maxOf(rawThreshold, linkedCount)
                val spy = SpyJiraContentExtractor(linkedCount = linkedCount)
                val orchestrator = buildBugConditionOrchestrator(spy, threshold)

                val ticketId = "PBT-${linkedCount}-${threshold}"
                val result = orchestrator.analyzeTicket(ticketId, forceReanalyze)

                assertEquals(1, spy.extractCallCount,
                    "extract() called ${spy.extractCallCount} times " +
                        "(expected 1) for linkedCount=$linkedCount, " +
                        "threshold=$threshold, forceReanalyze=$forceReanalyze")
                assertEquals(ticketId, result.ticketId)
            }
        }

    // ── Concrete Unit Tests ──

    @Test
    fun `extract called once when linked count below threshold and forceReanalyze true`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 2)
        val orchestrator = buildBugConditionOrchestrator(spy, threshold = 100)

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        assertEquals(1, spy.extractCallCount,
            "extract() should be called exactly once, but was called ${spy.extractCallCount} times")
        assertEquals("PROJ-1", result.ticketId)
    }

    @Test
    fun `extract called once when linked count at threshold`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 5)
        val orchestrator = buildBugConditionOrchestrator(spy, threshold = 5)

        val result = orchestrator.analyzeTicket("PROJ-2", forceReanalyze = true)

        assertEquals(1, spy.extractCallCount,
            "extract() should be called exactly once at threshold boundary, but was called ${spy.extractCallCount} times")
        assertEquals("PROJ-2", result.ticketId)
    }

    @Test
    fun `extract called once when zero linked tickets`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 0)
        val orchestrator = buildBugConditionOrchestrator(spy, threshold = 50)

        val result = orchestrator.analyzeTicket("PROJ-3", forceReanalyze = true)

        assertEquals(1, spy.extractCallCount,
            "extract() should be called exactly once with zero linked tickets, but was called ${spy.extractCallCount} times")
        assertEquals("PROJ-3", result.ticketId)
    }

    @Test
    fun `extract called once when forceReanalyze false and no cache`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 3)
        val orchestrator = buildBugConditionOrchestrator(spy, threshold = 100)

        val result = orchestrator.analyzeTicket("PROJ-4", forceReanalyze = false)

        assertEquals(1, spy.extractCallCount,
            "extract() should be called exactly once with forceReanalyze=false, but was called ${spy.extractCallCount} times")
        assertEquals("PROJ-4", result.ticketId)
    }

    @Test
    fun `analysis result is valid when extract called once`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 2)
        val orchestrator = buildBugConditionOrchestrator(spy, threshold = 100)

        val result = orchestrator.analyzeTicket("PROJ-5", forceReanalyze = true)

        assertEquals(AnalysisSource.FRESH_AI, result.source)
        assertTrue(result.context.unified.isNotBlank(), "Result should have valid content")
    }

    // ── Helpers ──

    private fun buildBugConditionOrchestrator(
        spy: SpyJiraContentExtractor,
        threshold: Int
    ): AIOrchestratorImpl {
        return AIOrchestratorImpl(
            kbRepository = FakeKBRepository(),
            agents = mapOf("provider-1" to FakeAIAgent()),
            providerConfigs = mutableListOf(activeProvider("provider-1")),
            jiraContentExtractor = spy,
            deepPromptBuilder = FakeDeepPromptBuilder(),
            deepResponseParser = FakeDeepResponseParser(),
            mapReduceAnalyzer = FakeMapReduceAnalyzer(
                isEnabled = true, threshold = threshold
            )
        )
    }
}
