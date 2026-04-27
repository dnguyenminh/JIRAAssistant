package com.assistant.scan

import com.assistant.ai.*
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.domain.NetworkGraph
import com.assistant.jira.*
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property tests for debounce coalescing in IncrementalGraphBuilder.
 * Tag: Feature: incremental-graph-rendering, Property 6: Debounce coalescing
 */
@OptIn(ExperimentalKotest::class)
class IncrementalGraphDebouncePropertyTest {

    private val projectKey = "DEBNC"
    private val ticketKeys = (1..5).map { "$projectKey-$it" }

    private fun buildEngine(kbRepo: DebounceKBRepo): BatchScanEngine =
        BatchScanEngine(
            aiOrchestrator = DebounceAIOrchestrator(),
            kbRepository = kbRepo,
            jiraClientProvider = { DebounceJiraClient(ticketKeys) },
            featureNetworkMapper = FeatureNetworkMapper(DebounceAIAgent()),
            scanStateRepository = DebounceNoOpScanStateRepo(),
            scanLogRepository = DebounceNoOpScanLogRepo(),
            scope = CoroutineScope(Dispatchers.Default)
        )

    /**
     * Property 6: Debounce coalesces rapid triggers.
     * For any N (2..20) rapid triggerBuild() calls with no delay,
     * at most 1 build executes after debounce completes.
     * **Validates: Requirements 4.2, 4.3**
     */
    @Test
    fun `rapid triggers coalesce into single build`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 50), Arb.int(2..20)) { n ->
            val kbRepo = DebounceKBRepo()
            val engine = buildEngine(kbRepo)
            val builder = IncrementalGraphBuilder(
                engine = engine,
                scope = CoroutineScope(Dispatchers.Default),
                debounceMs = 100L
            )

            repeat(n) { builder.triggerBuild(projectKey) }
            delay(400) // wait for debounce (100ms) + build

            assertEquals(
                1, kbRepo.saveGraphCallCount,
                "$n rapid triggers should coalesce into exactly 1 build"
            )
            builder.cancel()
        }
    }

    // --- Test Doubles (independent from other test files) ---

    class DebounceKBRepo : KBRepository {
        var saveGraphCallCount = 0
        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean {
            saveGraphCallCount++
            return true
        }
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
    }

    class DebounceAIOrchestrator : AIOrchestrator {
        override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean) =
            AnalysisResult(
                ticketId, RequirementSummary("S"), emptyList(),
                ComplexityAssessment(1.0, "Low", emptyList()), AnalysisSource.FRESH_AI
            )
        override suspend fun testProvider(providerId: String) =
            ProviderTestResult(providerId, true, 50, "OK")
        override fun setFailoverOrder(providerIds: List<String>) {}
        override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
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

    class DebounceJiraClient(private val keys: List<String>) : JiraClient {
        override suspend fun getProjects() = listOf(JiraProject("1", "P", "P"))
        override suspend fun getIssues(projectKey: String, maxResults: Int) =
            keys.map { JiraIssue(it, it, JiraIssueFields(summary = "T $it")) }
        override suspend fun getIssueDetails(issueKey: String) =
            JiraIssue(issueKey, issueKey, JiraIssueFields(summary = "T $issueKey"))
    }

    class DebounceAIAgent : AIAgent {
        override suspend fun analyze(prompt: String, context: AIContext?) =
            AIResult.Success("ok")
        override fun getAgentName() = "DebounceStub"
    }

    class DebounceNoOpScanStateRepo : ScanStateRepository {
        override suspend fun findByProjectKey(projectKey: String): ScanState? = null
        override suspend fun save(state: ScanState) {}
        override suspend fun delete(projectKey: String) {}
        override suspend fun findAllScanning() = emptyList<ScanState>()
    }

    class DebounceNoOpScanLogRepo : ScanLogRepository {
        override suspend fun addEntry(entry: ScanLogEntry) {}
        override suspend fun getByProjectKey(projectKey: String, limit: Long) =
            emptyList<ScanLogEntry>()
        override suspend fun getByProjectKeyPaged(
            projectKey: String, limit: Long, offset: Long
        ) = emptyList<ScanLogEntry>()
        override suspend fun countByProjectKey(projectKey: String) = 0L
        override suspend fun deleteByProjectKey(projectKey: String) {}
    }
}
