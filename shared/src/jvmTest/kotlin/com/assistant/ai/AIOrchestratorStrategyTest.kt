package com.assistant.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for AIOrchestratorImpl KB-First strategy, failover, retry, and error handling.
 * Validates: Requirements 21.6
 */
class AIOrchestratorStrategyTest {

    // ── Req 21.6: KB-First — returns cached when available ──

    @Test
    fun `returns cached result when KB hit and forceReanalyze is false`() = runTest {
        val kb = FakeKBRepository()
        kb.records["PROJ-1"] = cachedKBRecord("PROJ-1")
        val agent = FakeAIAgent()
        val orchestrator = buildOrchestrator(agent = agent, kb = kb)

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = false)

        assertEquals(AnalysisSource.KB_CACHE, result.source)
        assertEquals("Cached summary", result.context.unified)
        assertEquals(0, agent.callCount, "AI should NOT be called")
    }

    // ── Req 21.6: KB-First — calls AI when forceReanalyze=true ──

    @Test
    fun `calls AI when forceReanalyze true even if cached`() = runTest {
        val kb = FakeKBRepository()
        kb.records["PROJ-1"] = cachedKBRecord("PROJ-1")
        val agent = FakeAIAgent()
        val orchestrator = buildOrchestrator(agent = agent, kb = kb)

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        assertEquals(1, agent.callCount, "AI should be called")
        assertEquals(AnalysisSource.FRESH_AI, result.source)
        assertTrue(kb.overwriteCalled, "Should overwrite KB on forceReanalyze")
    }

    // ── Req 21.6: Provider failover ──

    @Test
    fun `tries next provider when first fails`() = runTest {
        val failAgent = FakeAIAgent("fail", AIResult.Failure("timeout"))
        val successAgent = FakeAIAgent("success")
        val orchestrator = AIOrchestratorImpl(
            kbRepository = FakeKBRepository(),
            agents = mapOf("p1" to failAgent, "p2" to successAgent),
            providerConfigs = mutableListOf(
                activeProvider("p1", priority = 0),
                activeProvider("p2", priority = 1)
            )
        )

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        assertTrue(failAgent.callCount > 0, "First provider attempted")
        assertTrue(successAgent.callCount > 0, "Second provider attempted")
        assertEquals("PROJ-1", result.ticketId)
    }

    // ── Req 21.6: Retry logic on parse failure ──

    @Test
    fun `retries up to 2 times on parse failure then succeeds`() = runTest {
        val parser = FakeDeepResponseParser()
        parser.failCount = 2 // fail twice, succeed on 3rd
        val agent = FakeAIAgent()
        val orchestrator = buildOrchestrator(
            agent = agent, parser = parser,
            extractor = FakeJiraContentExtractor(),
            promptBuilder = FakeDeepPromptBuilder()
        )

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        // 1 initial + 2 retries = 3 calls
        assertEquals(3, agent.callCount, "Should retry twice after initial failure")
        assertEquals("PROJ-1", result.ticketId)
        assertEquals(AnalysisSource.FRESH_AI, result.source)
    }

    // ── Error handling: all providers fail ──

    @Test
    fun `returns error result when all providers fail`() = runTest {
        val failAgent = FakeAIAgent(response = AIResult.Failure("down"))
        val orchestrator = buildOrchestrator(agent = failAgent)

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        assertTrue(result.context.unified.contains("Error"))
    }

    @Test
    fun `returns error when no active providers configured`() = runTest {
        val orchestrator = AIOrchestratorImpl(
            kbRepository = FakeKBRepository(),
            agents = emptyMap(),
            providerConfigs = mutableListOf()
        )

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        assertTrue(result.context.unified.contains("Error"))
        assertTrue(result.context.unified.contains("offline"))
    }

    @Test
    fun `saves to KB on successful fresh analysis`() = runTest {
        val kb = FakeKBRepository()
        val orchestrator = buildOrchestrator(kb = kb)

        orchestrator.analyzeTicket("PROJ-1", forceReanalyze = false)

        assertTrue(kb.saveCalled, "Should save to KB")
        assertTrue(kb.records.containsKey("PROJ-1"))
    }

    @Test
    fun `uses overwrite on forceReanalyze`() = runTest {
        val kb = FakeKBRepository()
        val orchestrator = buildOrchestrator(kb = kb)

        orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        assertTrue(kb.overwriteCalled, "Should overwrite KB on forceReanalyze")
    }

    // ── Helper ──

    private fun buildOrchestrator(
        agent: FakeAIAgent = FakeAIAgent(),
        kb: FakeKBRepository = FakeKBRepository(),
        extractor: FakeJiraContentExtractor? = null,
        promptBuilder: FakeDeepPromptBuilder? = null,
        parser: FakeDeepResponseParser? = null
    ): AIOrchestratorImpl {
        return AIOrchestratorImpl(
            kbRepository = kb,
            agents = mapOf("provider-1" to agent),
            providerConfigs = mutableListOf(activeProvider("provider-1")),
            jiraContentExtractor = extractor,
            deepPromptBuilder = promptBuilder,
            deepResponseParser = parser
        )
    }
}
