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
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Bug Condition Exploration — Property 1: Graph not built on scan completion.
 * EXPECTED: FAILS on unfixed code (confirms `buildAndSaveGraph()` never called).
 * **Validates: Requirements 1.1, 2.1, 2.2**
 */
@OptIn(ExperimentalKotest::class)
class CompleteScanBugConditionPropertyTest {

    private var scanStateRepo = InMemoryScanStateRepo()
    private var scanLogRepo = InMemoryScanLogRepo()
    private var kbRepo = TrackingKBRepository()

    private fun arbProjectKey(): Arb<String> =
        Arb.string(2, 10, Codepoint.alphanumeric()).map { it.uppercase() }

    /**
     * For any projectKey with ≥1 ticket, after scan completes:
     * `kbRepository.getGraphData(projectKey)` must be non-null.
     * FAILS on unfixed code — `completeScan()` skips `buildAndSaveGraph()`.
     * **Validates: Requirements 1.1, 2.1, 2.2**
     */
    @Test
    fun `graph data available after scan completes`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 20), arbProjectKey(), Arb.int(1..10)) { projectKey, ticketCount ->
            scanStateRepo = InMemoryScanStateRepo()
            scanLogRepo = InMemoryScanLogRepo()
            kbRepo = TrackingKBRepository()

            val ticketKeys = (1..ticketCount).map { "$projectKey-$it" }
            val engine = BatchScanEngine(
                aiOrchestrator = StubAIOrchestratorForScan(),
                kbRepository = kbRepo,
                jiraClientProvider = { StubJiraClientForScan(ticketKeys) },
                featureNetworkMapper = FeatureNetworkMapper(StubAIAgentForScan()),
                scanStateRepository = scanStateRepo,
                scanLogRepository = scanLogRepo,
                scope = CoroutineScope(Dispatchers.Default)
            )
            engine.startScan(projectKey)
            awaitScanComplete(projectKey)

            val state = scanStateRepo.findByProjectKey(projectKey)
            assertNotNull(state, "Scan state should exist for $projectKey")
            assertEquals(ScanStatus.COMPLETED, state!!.status, "Scan should be COMPLETED")
            // BUG CONDITION: FAILS on unfixed code
            assertNotNull(
                kbRepo.getGraphData(projectKey),
                "getGraphData($projectKey) should be non-null after scan with $ticketCount tickets"
            )
        }
    }

    private suspend fun awaitScanComplete(projectKey: String) {
        repeat(200) {
            if (scanStateRepo.findByProjectKey(projectKey)?.status == ScanStatus.COMPLETED) return
            delay(50)
        }
        fail("Scan did not complete within timeout for $projectKey")
    }

    // --- Inline Test Doubles (shared module can't access server test doubles) ---

    class TrackingKBRepository : KBRepository {
        private val graphs = mutableMapOf<String, NetworkGraph>()
        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean {
            graphs[projectKey] = graph; return true
        }
        override suspend fun getGraphData(projectKey: String) = graphs[projectKey]
    }

    class StubAIOrchestratorForScan : AIOrchestrator {
        override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean) = AnalysisResult(
            ticketId, RequirementSummary("Summary"), emptyList(),
            ComplexityAssessment(3.0, "Medium", emptyList()), AnalysisSource.FRESH_AI
        )
        override suspend fun testProvider(providerId: String) = ProviderTestResult(providerId, true, 100, "OK")
        override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
        override fun setFailoverOrder(providerIds: List<String>) {}
        override fun analyzeBottlenecks(t: Int, r: Int, c: Double, b: Int) = emptyList<BottleneckAlert>()
        override fun generateVelocityTrend(t: Int, r: Int) = emptyList<SprintVelocity>()
        override fun calculateAIVelocity(t: Int, r: Int, c: Double) = 0.5
    }

    class StubJiraClientForScan(private val ticketKeys: List<String>) : JiraClient {
        override suspend fun getProjects() = listOf(JiraProject("1", "PROJ", "Test Project"))
        override suspend fun getIssues(projectKey: String, maxResults: Int) = ticketKeys.map { key ->
            JiraIssue(id = key, key = key, fields = JiraIssueFields(summary = "Ticket $key"))
        }
        override suspend fun getIssueDetails(issueKey: String) =
            JiraIssue(id = issueKey, key = issueKey, fields = JiraIssueFields(summary = "Ticket $issueKey"))
    }

    class StubAIAgentForScan : AIAgent {
        override suspend fun analyze(prompt: String, context: AIContext?) = AIResult.Success("ok")
        override fun getAgentName() = "StubAgent"
    }

    class InMemoryScanStateRepo : ScanStateRepository {
        private val states = mutableMapOf<String, ScanState>()
        override suspend fun findByProjectKey(projectKey: String) = states[projectKey]
        override suspend fun save(state: ScanState) { states[state.projectKey] = state }
        override suspend fun delete(projectKey: String) { states.remove(projectKey) }
        override suspend fun findAllScanning() = states.values.filter { it.status == ScanStatus.SCANNING }
    }

    class InMemoryScanLogRepo : ScanLogRepository {
        private val entries = mutableListOf<ScanLogEntry>()
        override suspend fun addEntry(entry: ScanLogEntry) { entries.add(entry) }
        override suspend fun getByProjectKey(projectKey: String, limit: Long) =
            entries.filter { it.projectKey == projectKey }.take(limit.toInt())
        override suspend fun getByProjectKeyPaged(projectKey: String, limit: Long, offset: Long) =
            entries.filter { it.projectKey == projectKey }.drop(offset.toInt()).take(limit.toInt())
        override suspend fun countByProjectKey(projectKey: String) =
            entries.count { it.projectKey == projectKey }.toLong()
        override suspend fun deleteByProjectKey(projectKey: String) { entries.removeAll { it.projectKey == projectKey } }
    }
}
