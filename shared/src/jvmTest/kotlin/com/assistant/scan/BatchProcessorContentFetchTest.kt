package com.assistant.scan

import com.assistant.ai.*
import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.domain.NetworkGraph
import com.assistant.jira.*
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for fetchTicketContentForBatch() content extraction paths.
 * Validates: Requirements 2.1, 2.2
 */
class BatchProcessorContentFetchTest {

    /**
     * Task 3.1: When jiraContentExtractor is available, batch fetch returns
     * non-empty content derived from StructuredTicketContent.
     */
    @Test
    fun `batch fetch returns structured content when extractor available`() = runBlocking {
        val capturedContent = mutableListOf<Pair<String, String>>()
        val extractor = FakeContentExtractor(
            StructuredTicketContent(
                summary = "Implement OAuth2 login",
                description = "Full OAuth2 flow with refresh tokens"
            )
        )
        val engine = buildEngine(
            extractor = extractor,
            batchSize = "2",
            onBatchAnalyze = { tickets, _ ->
                capturedContent.addAll(tickets)
                tickets.associate { (id, _) -> id to realResult(id) }
            }
        )
        engine.processBatchPrompt("PROJ", listOf("PROJ-1"))

        assertTrue(capturedContent.isNotEmpty(), "analyzeTicketBatch should be called")
        val content = capturedContent.first().second
        assertTrue(content.isNotEmpty(), "Content should be non-empty")
        assertTrue(content.contains("Implement OAuth2 login"), "Content should contain summary")
    }

    /**
     * Task 3.2: When jiraContentExtractor is null, batch fetch falls back
     * to legacy fetch (summary + description from JiraClient).
     */
    @Test
    fun `batch fetch uses legacy when extractor is null`() = runBlocking {
        val capturedContent = mutableListOf<Pair<String, String>>()
        val engine = buildEngine(
            extractor = null,
            batchSize = "2",
            onBatchAnalyze = { tickets, _ ->
                capturedContent.addAll(tickets)
                tickets.associate { (id, _) -> id to realResult(id) }
            }
        )
        engine.processBatchPrompt("PROJ", listOf("PROJ-1"))

        assertTrue(capturedContent.isNotEmpty(), "analyzeTicketBatch should be called")
        val content = capturedContent.first().second
        assertTrue(content.contains("Summary:"), "Legacy fetch should include Summary field")
        assertTrue(content.contains("Ticket PROJ-1"), "Legacy fetch should include ticket summary")
    }

    /**
     * Task 3.3: When jiraContentExtractor.extract() throws, batch fetch
     * falls back to legacy fetch gracefully.
     */
    @Test
    fun `batch fetch falls back to legacy when extractor throws`() = runBlocking {
        val capturedContent = mutableListOf<Pair<String, String>>()
        val extractor = ThrowingContentExtractor()
        val engine = buildEngine(
            extractor = extractor,
            batchSize = "2",
            onBatchAnalyze = { tickets, _ ->
                capturedContent.addAll(tickets)
                tickets.associate { (id, _) -> id to realResult(id) }
            }
        )
        engine.processBatchPrompt("PROJ", listOf("PROJ-1"))

        assertTrue(capturedContent.isNotEmpty(), "analyzeTicketBatch should be called")
        val content = capturedContent.first().second
        assertTrue(content.contains("Summary:"), "Should fall back to legacy fetch")
    }

    // --- Test Doubles ---

    private fun realResult(ticketId: String) = AnalysisResult(
        ticketId = ticketId,
        context = RequirementSummary("Real analysis for $ticketId with enough detail"),
        evolution = emptyList(),
        complexity = ComplexityAssessment(3.0, "Medium", emptyList()),
        source = AnalysisSource.FRESH_AI
    )

    private fun buildEngine(
        extractor: JiraContentExtractor?,
        batchSize: String,
        onBatchAnalyze: suspend (List<Pair<String, String>>, Boolean) -> Map<String, AnalysisResult>
    ): BatchScanEngine {
        val jiraClient = StubJiraForBatch()
        return BatchScanEngine(
            aiOrchestrator = CapturingAIOrchestrator(onBatchAnalyze),
            kbRepository = NoOpKBRepo(),
            jiraClientProvider = { jiraClient },
            featureNetworkMapper = FeatureNetworkMapper(StubAIAgentForBatch()),
            scanStateRepository = NoOpScanStateRepo(),
            scanLogRepository = NoOpScanLogRepo(),
            scope = CoroutineScope(Dispatchers.Default),
            settingsRepository = FixedSettingsRepo(batchSize),
            jiraContentExtractor = extractor
        )
    }
}
