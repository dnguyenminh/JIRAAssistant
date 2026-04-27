package com.assistant.scan

import com.assistant.ai.*
import com.assistant.domain.FeatureNetworkMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for placeholder detection in saveBatchResults() and
 * single-ticket fallback in processSingleBatchPrompt().
 * Validates: Requirements 2.4
 */
class BatchProcessorPlaceholderTest {

    /**
     * Task 3.5: saveBatchResults() skips placeholder results — they are NOT
     * saved with placeholder content. The fallback processTicket() saves a
     * real result instead. Verified by checking no KB record has placeholder text.
     */
    @Test
    fun `placeholder results are replaced by real fallback results`() = runBlocking {
        val kbRepo = TrackingKBRepoForBatch()
        val engine = buildEngine(
            kbRepo = kbRepo,
            batchSize = "3",
            batchResults = mapOf(
                "PROJ-1" to realResult("PROJ-1"),
                "PROJ-2" to placeholderResult("PROJ-2", "..."),
                "PROJ-3" to realResult("PROJ-3")
            )
        )
        engine.processBatchPrompt("PROJ", listOf("PROJ-1", "PROJ-2", "PROJ-3"))

        // Placeholder "..." should never appear in saved records
        val placeholderSaved = kbRepo.savedRecords.any { it.requirementSummary == "..." }
        assertTrue(!placeholderSaved, "Placeholder '...' should NOT be saved to KB")
        // PROJ-2 should have a real result from fallback
        val proj2Record = kbRepo.savedRecords.find { it.ticketId == "PROJ-2" }
        assertTrue(proj2Record != null, "PROJ-2 should be saved via fallback")
        assertTrue(
            proj2Record!!.requirementSummary.contains("Real detailed analysis"),
            "PROJ-2 should have real content from single-ticket fallback"
        )
    }

    /**
     * Task 3.5: saveBatchResults() skips short-string placeholders.
     */
    @Test
    fun `short string placeholder is not saved from batch path`() = runBlocking {
        val kbRepo = TrackingKBRepoForBatch()
        val engine = buildEngine(
            kbRepo = kbRepo,
            batchSize = "2",
            batchResults = mapOf(
                "PROJ-1" to placeholderResult("PROJ-1", "short"),
                "PROJ-2" to realResult("PROJ-2")
            )
        )
        engine.processBatchPrompt("PROJ", listOf("PROJ-1", "PROJ-2"))

        // "short" placeholder should not appear in saved records
        val shortSaved = kbRepo.savedRecords.any { it.requirementSummary == "short" }
        assertTrue(!shortSaved, "Short placeholder should NOT be saved to KB")
        // PROJ-1 should have a real result from fallback
        val proj1Record = kbRepo.savedRecords.find { it.ticketId == "PROJ-1" }
        assertTrue(proj1Record != null, "PROJ-1 should be saved via fallback")
    }

    /**
     * Task 3.6: processSingleBatchPrompt() triggers single-ticket
     * fallback for placeholder ticket IDs via processTicket().
     * Verified by tracking analyzeTicket calls.
     */
    @Test
    fun `placeholder tickets trigger single-ticket fallback`() = runBlocking {
        val kbRepo = TrackingKBRepoForBatch()
        val fallbackIds = mutableListOf<String>()
        val engine = buildEngine(
            kbRepo = kbRepo,
            batchSize = "2",
            batchResults = mapOf(
                "PROJ-1" to placeholderResult("PROJ-1", "..."),
                "PROJ-2" to realResult("PROJ-2")
            ),
            onSingleAnalyze = { ticketId, _ ->
                fallbackIds.add(ticketId)
                realResult(ticketId)
            }
        )
        engine.processBatchPrompt("PROJ", listOf("PROJ-1", "PROJ-2"))

        assertTrue("PROJ-1" in fallbackIds, "Placeholder PROJ-1 should trigger fallback")
        assertTrue("PROJ-2" !in fallbackIds, "Real PROJ-2 should NOT trigger fallback")
    }

    /**
     * Task 3.6: All placeholder tickets in a batch trigger fallback.
     */
    @Test
    fun `all placeholder tickets trigger fallback`() = runBlocking {
        val fallbackIds = mutableListOf<String>()
        val engine = buildEngine(
            kbRepo = TrackingKBRepoForBatch(),
            batchSize = "3",
            batchResults = mapOf(
                "PROJ-1" to placeholderResult("PROJ-1", "..."),
                "PROJ-2" to placeholderResult("PROJ-2", "Placeholder text"),
                "PROJ-3" to placeholderResult("PROJ-3", "tiny")
            ),
            onSingleAnalyze = { ticketId, _ ->
                fallbackIds.add(ticketId)
                realResult(ticketId)
            }
        )
        engine.processBatchPrompt("PROJ", listOf("PROJ-1", "PROJ-2", "PROJ-3"))

        assertEquals(3, fallbackIds.size, "All 3 placeholder tickets should trigger fallback")
    }

    // --- Helpers ---

    private fun realResult(ticketId: String) = AnalysisResult(
        ticketId = ticketId,
        context = RequirementSummary("Real detailed analysis for $ticketId covering requirements"),
        evolution = emptyList(),
        complexity = ComplexityAssessment(3.0, "Medium", emptyList()),
        source = AnalysisSource.FRESH_AI
    )

    private fun placeholderResult(ticketId: String, summary: String) = AnalysisResult(
        ticketId = ticketId,
        context = RequirementSummary(summary),
        evolution = emptyList(),
        complexity = ComplexityAssessment(1.0, "Low", emptyList()),
        source = AnalysisSource.FRESH_AI
    )

    private fun buildEngine(
        kbRepo: TrackingKBRepoForBatch,
        batchSize: String,
        batchResults: Map<String, AnalysisResult>,
        onSingleAnalyze: (suspend (String, Boolean) -> AnalysisResult)? = null
    ): BatchScanEngine {
        val orchestrator = object : AIOrchestrator {
            override suspend fun analyzeTicket(
                ticketId: String, forceReanalyze: Boolean
            ): AnalysisResult {
                return onSingleAnalyze?.invoke(ticketId, forceReanalyze)
                    ?: realResult(ticketId)
            }

            override suspend fun analyzeTicketBatch(
                tickets: List<Pair<String, String>>,
                forceReanalyze: Boolean
            ) = batchResults

            override suspend fun testProvider(providerId: String) =
                ProviderTestResult(providerId, true, 100, "OK")
            override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
            override fun setFailoverOrder(providerIds: List<String>) {}
            override fun analyzeBottlenecks(
                totalTickets: Int, resolvedCount: Int,
                cycleTimeDays: Double, blockedCount: Int
            ) = emptyList<BottleneckAlert>()
            override fun generateVelocityTrend(
                totalTickets: Int, resolvedCount: Int
            ) = emptyList<SprintVelocity>()
            override fun calculateAIVelocity(
                totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double
            ) = 0.5
        }
        return BatchScanEngine(
            aiOrchestrator = orchestrator,
            kbRepository = kbRepo,
            jiraClientProvider = { StubJiraForBatch() },
            featureNetworkMapper = FeatureNetworkMapper(StubAIAgentForBatch()),
            scanStateRepository = NoOpScanStateRepo(),
            scanLogRepository = NoOpScanLogRepo(),
            scope = CoroutineScope(Dispatchers.Default),
            settingsRepository = FixedSettingsRepo(batchSize)
        )
    }
}
