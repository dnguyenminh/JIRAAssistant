package com.assistant.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Preservation Property Tests — Duplicate Deep Extraction.
 *
 * Property 2: Preservation — For all inputs where the bug condition
 * does NOT hold, behavior is unchanged. Observed on UNFIXED code first.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6
 */
class DuplicateExtractionPreservationTest {

    // ── Req 3.1: Map-reduce delegation when linked count > threshold ──

    @Test
    fun `map-reduce delegated when linked count exceeds threshold`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 51)
        val mapReduce = FakeMapReduceAnalyzer(isEnabled = true, threshold = 50)
        val orchestrator = buildPreservationOrchestrator(
            spy = spy, mapReduce = mapReduce
        )

        val result = orchestrator.analyzeTicket("PROJ-MR", forceReanalyze = true)

        assertTrue(mapReduce.analyzeWithMapReduceCalled,
            "MapReduceAnalyzer should be called when linked count > threshold")
        assertEquals("MapReduce: PROJ-MR", result.context.unified)
    }

    // ── Req 3.2: Skip map-reduce when analyzer is null ──

    @Test
    fun `single-prompt flow when mapReduceAnalyzer is null`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 5)
        val orchestrator = AIOrchestratorImpl(
            kbRepository = FakeKBRepository(),
            agents = mapOf("p1" to FakeAIAgent()),
            providerConfigs = mutableListOf(activeProvider("p1")),
            jiraContentExtractor = spy,
            deepPromptBuilder = FakeDeepPromptBuilder(),
            deepResponseParser = FakeDeepResponseParser(),
            mapReduceAnalyzer = null
        )

        val result = orchestrator.analyzeTicket("PROJ-NULL", forceReanalyze = true)

        assertEquals("PROJ-NULL", result.ticketId)
        assertEquals(AnalysisSource.FRESH_AI, result.source)
    }

    @Test
    fun `single-prompt flow when mapReduceAnalyzer is disabled`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 5)
        val mapReduce = FakeMapReduceAnalyzer(isEnabled = false, threshold = 1)
        val orchestrator = buildPreservationOrchestrator(
            spy = spy, mapReduce = mapReduce
        )

        val result = orchestrator.analyzeTicket("PROJ-DIS", forceReanalyze = true)

        assertFalse(mapReduce.analyzeWithMapReduceCalled,
            "MapReduceAnalyzer should NOT be called when disabled")
        assertEquals("PROJ-DIS", result.ticketId)
    }

    // ── Req 3.3: Legacy fallback when deep analysis not injected ──

    @Test
    fun `legacy fallback when no deep analysis components`() = runTest {
        val agent = FakeAIAgent(response = AIResult.Success(VALID_AI_JSON))
        val orchestrator = AIOrchestratorImpl(
            kbRepository = FakeKBRepository(),
            agents = mapOf("p1" to agent),
            providerConfigs = mutableListOf(activeProvider("p1")),
            jiraContentExtractor = null,
            deepPromptBuilder = null,
            deepResponseParser = null,
            mapReduceAnalyzer = FakeMapReduceAnalyzer(isEnabled = true, threshold = 1)
        )

        val result = orchestrator.analyzeTicket("PROJ-LEG", forceReanalyze = true)

        assertEquals("Legacy summary", result.context.unified)
    }

    // ── Req 3.4: Error handling when extract() throws ──

    @Test
    fun `falls through when extract throws in map-reduce check`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 0, shouldThrow = true)
        val orchestrator = buildPreservationOrchestrator(spy = spy)

        val result = orchestrator.analyzeTicket("PROJ-ERR", forceReanalyze = true)

        assertEquals("PROJ-ERR", result.ticketId)
        // Should still produce a result via single-prompt fallback
        // (which will also fail extract, but that's the existing behavior)
    }

    // ── Req 3.5: KB cache hit returns cached result ──

    @Test
    fun `returns cached result without calling extract`() = runTest {
        val spy = SpyJiraContentExtractor(linkedCount = 5)
        val kb = FakeKBRepository()
        kb.records["PROJ-CACHE"] = cachedKBRecord("PROJ-CACHE")
        val orchestrator = AIOrchestratorImpl(
            kbRepository = kb,
            agents = mapOf("p1" to FakeAIAgent()),
            providerConfigs = mutableListOf(activeProvider("p1")),
            jiraContentExtractor = spy,
            deepPromptBuilder = FakeDeepPromptBuilder(),
            deepResponseParser = FakeDeepResponseParser(),
            mapReduceAnalyzer = FakeMapReduceAnalyzer(isEnabled = true, threshold = 100)
        )

        val result = orchestrator.analyzeTicket("PROJ-CACHE", forceReanalyze = false)

        assertEquals(AnalysisSource.KB_CACHE, result.source)
        assertEquals("Cached summary", result.context.unified)
        assertEquals(0, spy.extractCallCount, "extract() should NOT be called on cache hit")
    }

    // ── Req 3.6: Result saved to KB on success ──

    @Test
    fun `result saved to KB after successful analysis`() = runTest {
        val kb = FakeKBRepository()
        val spy = SpyJiraContentExtractor(linkedCount = 51)
        val mapReduce = FakeMapReduceAnalyzer(isEnabled = true, threshold = 50)
        val orchestrator = AIOrchestratorImpl(
            kbRepository = kb,
            agents = mapOf("p1" to FakeAIAgent()),
            providerConfigs = mutableListOf(activeProvider("p1")),
            jiraContentExtractor = spy,
            deepPromptBuilder = FakeDeepPromptBuilder(),
            deepResponseParser = FakeDeepResponseParser(),
            mapReduceAnalyzer = mapReduce
        )

        orchestrator.analyzeTicket("PROJ-SAVE", forceReanalyze = true)

        assertTrue(kb.overwriteCalled, "Should save to KB on forceReanalyze")
        assertTrue(kb.records.containsKey("PROJ-SAVE"))
    }

    // ── Helper ──

    private fun buildPreservationOrchestrator(
        spy: SpyJiraContentExtractor = SpyJiraContentExtractor(),
        mapReduce: FakeMapReduceAnalyzer = FakeMapReduceAnalyzer()
    ): AIOrchestratorImpl {
        return AIOrchestratorImpl(
            kbRepository = FakeKBRepository(),
            agents = mapOf("p1" to FakeAIAgent()),
            providerConfigs = mutableListOf(activeProvider("p1")),
            jiraContentExtractor = spy,
            deepPromptBuilder = FakeDeepPromptBuilder(),
            deepResponseParser = FakeDeepResponseParser(),
            mapReduceAnalyzer = mapReduce
        )
    }
}
