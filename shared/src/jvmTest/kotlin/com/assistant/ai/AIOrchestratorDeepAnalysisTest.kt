package com.assistant.ai

import com.assistant.ai.deepanalysis.DeepAnalysisPromptBuilder
import com.assistant.ai.deepanalysis.DeepAnalysisResponseParser
import com.assistant.ai.deepanalysis.JiraContentExtractor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for AIOrchestratorImpl deep analysis pipeline vs legacy fallback.
 * Validates: Requirements 21.1, 21.2
 */
class AIOrchestratorDeepAnalysisTest {

    // ── Req 21.1: Deep analysis pipeline when components injected ──

    @Test
    fun `analyzeTicket uses deep pipeline when all components injected`() = runTest {
        val extractor = FakeJiraContentExtractor()
        val promptBuilder = FakeDeepPromptBuilder()
        val parser = FakeDeepResponseParser()
        val agent = FakeAIAgent()
        val orchestrator = buildOrchestrator(
            agent = agent, extractor = extractor,
            promptBuilder = promptBuilder, parser = parser
        )

        val result = orchestrator.analyzeTicket("PROJ-1", forceReanalyze = true)

        assertTrue(extractor.extractCalled, "Should call JiraContentExtractor")
        assertTrue(promptBuilder.buildCalled, "Should call DeepAnalysisPromptBuilder")
        assertTrue(parser.parseCalled, "Should call DeepAnalysisResponseParser")
        assertEquals("PROJ-1", extractor.lastTicketId)
        assertEquals(AnalysisSource.FRESH_AI, result.source)
    }

    @Test
    fun `deep pipeline passes extractor output to prompt builder`() = runTest {
        val agent = FakeAIAgent()
        val extractor = FakeJiraContentExtractor()
        val promptBuilder = FakeDeepPromptBuilder()
        val parser = FakeDeepResponseParser()
        val orchestrator = buildOrchestrator(
            agent = agent, extractor = extractor,
            promptBuilder = promptBuilder, parser = parser
        )

        orchestrator.analyzeTicket("PROJ-42", forceReanalyze = true)

        // FakeDeepPromptBuilder includes summary in prompt
        assertTrue(agent.lastPrompt!!.contains("Test ticket PROJ-42"))
    }

    // ── Req 21.1: Legacy fallback when components NOT injected ──

    @Test
    fun `analyzeTicket uses legacy pipeline when no deep components`() = runTest {
        val agent = FakeAIAgent(response = AIResult.Success(VALID_AI_JSON))
        val orchestrator = buildOrchestrator(agent = agent)

        val result = orchestrator.analyzeTicket("PROJ-2", forceReanalyze = true)

        assertEquals("PROJ-2", result.ticketId)
        // Legacy parser produces "Legacy summary" from VALID_AI_JSON
        assertEquals("Legacy summary", result.context.unified)
    }

    @Test
    fun `legacy fallback when only extractor injected but not parser`() = runTest {
        val agent = FakeAIAgent(response = AIResult.Success(VALID_AI_JSON))
        val orchestrator = buildOrchestrator(
            agent = agent,
            extractor = FakeJiraContentExtractor(),
            promptBuilder = null, parser = null
        )

        val result = orchestrator.analyzeTicket("PROJ-3", forceReanalyze = true)

        assertEquals("Legacy summary", result.context.unified)
    }

    @Test
    fun `legacy fallback when only parser injected but not extractor`() = runTest {
        val agent = FakeAIAgent(response = AIResult.Success(VALID_AI_JSON))
        val orchestrator = buildOrchestrator(
            agent = agent,
            extractor = null, promptBuilder = FakeDeepPromptBuilder(),
            parser = FakeDeepResponseParser()
        )

        val result = orchestrator.analyzeTicket("PROJ-4", forceReanalyze = true)

        assertEquals("Legacy summary", result.context.unified)
    }

    // ── Req 21.2: BatchScanEngine uses same upgraded analyzeTicket ──

    @Test
    fun `analyzeTicket with content param also uses deep pipeline`() = runTest {
        val extractor = FakeJiraContentExtractor()
        val parser = FakeDeepResponseParser()
        val orchestrator = buildOrchestrator(
            agent = FakeAIAgent(), extractor = extractor,
            promptBuilder = FakeDeepPromptBuilder(), parser = parser
        )

        // This overload is what BatchScanEngine calls
        val result = orchestrator.analyzeTicket("PROJ-5", "ignored content", false)

        assertTrue(extractor.extractCalled, "Deep pipeline used even with content param")
        assertTrue(parser.parseCalled)
        assertEquals("PROJ-5", result.ticketId)
    }

    // ── Helper ──

    private fun buildOrchestrator(
        agent: FakeAIAgent = FakeAIAgent(),
        kb: FakeKBRepository = FakeKBRepository(),
        extractor: JiraContentExtractor? = null,
        promptBuilder: DeepAnalysisPromptBuilder? = null,
        parser: DeepAnalysisResponseParser? = null
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
