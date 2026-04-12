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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Preservation Property Tests — capture baseline behavior that MUST NOT change after fix.
 * These tests PASS on unfixed code (they test non-buggy paths).
 * **Validates: Requirements 3.1, 3.2, 3.3**
 */
@OptIn(ExperimentalKotest::class)
class CompleteScanPreservationPropertyTest {

    private fun arbProjectKey(): Arb<String> =
        Arb.string(2, 10, Codepoint.alphanumeric()).map { it.uppercase() }

    private fun arbTicketId(): Arb<String> =
        Arb.string(3, 8, Codepoint.alphanumeric()).map { it.uppercase() }

    private fun buildEngine(
        kbRepo: TrackingKBRepoForPreservation,
        scanStateRepo: InMemoryScanStateRepoForPreservation,
        scanLogRepo: InMemoryScanLogRepoForPreservation,
        jiraClient: JiraClient
    ): BatchScanEngine = BatchScanEngine(
        aiOrchestrator = StubAIOrchestratorForPreservation(),
        kbRepository = kbRepo,
        jiraClientProvider = { jiraClient },
        featureNetworkMapper = FeatureNetworkMapper(StubAIAgentForPreservation()),
        scanStateRepository = scanStateRepo,
        scanLogRepository = scanLogRepo,
        scope = CoroutineScope(Dispatchers.Default)
    )

    /**
     * Property: For any random projectKey, pauseScan() on SCANNING state → status == PAUSED.
     * **Validates: Requirements 3.2**
     */
    @Test
    fun `pauseScan on SCANNING state transitions to PAUSED`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 20), arbProjectKey()) { projectKey ->
            val scanStateRepo = InMemoryScanStateRepoForPreservation()
            val scanLogRepo = InMemoryScanLogRepoForPreservation()
            val kbRepo = TrackingKBRepoForPreservation()
            val ticketKeys = (1..3).map { "$projectKey-$it" }
            val engine = buildEngine(kbRepo, scanStateRepo, scanLogRepo, SlowJiraClientForPreservation(ticketKeys))

            engine.startScan(projectKey)
            delay(50) // let scan start processing
            val result = engine.pauseScan(projectKey)

            assertEquals(ScanStatus.PAUSED, result.status, "pauseScan should set PAUSED for $projectKey")
            val stored = scanStateRepo.findByProjectKey(projectKey)
            assertNotNull(stored, "State should be persisted for $projectKey")
            assertEquals(ScanStatus.PAUSED, stored.status)
        }
    }

    /**
     * Property: For any random projectKey, cancelScan() on SCANNING state → status == CANCELLED.
     * **Validates: Requirements 3.2**
     */
    @Test
    fun `cancelScan on SCANNING state transitions to CANCELLED`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 20), arbProjectKey()) { projectKey ->
            val scanStateRepo = InMemoryScanStateRepoForPreservation()
            val scanLogRepo = InMemoryScanLogRepoForPreservation()
            val kbRepo = TrackingKBRepoForPreservation()
            val ticketKeys = (1..3).map { "$projectKey-$it" }
            val engine = buildEngine(kbRepo, scanStateRepo, scanLogRepo, SlowJiraClientForPreservation(ticketKeys))

            engine.startScan(projectKey)
            delay(50)
            val result = engine.cancelScan(projectKey)

            assertEquals(ScanStatus.CANCELLED, result.status, "cancelScan should set CANCELLED for $projectKey")
            val stored = scanStateRepo.findByProjectKey(projectKey)
            assertNotNull(stored, "State should be persisted for $projectKey")
            assertEquals(ScanStatus.CANCELLED, stored.status)
        }
    }

    /**
     * Property: For any random projectKey with 0 tickets, empty project handling →
     * status == COMPLETED, totalTickets == 0, saveGraphData NOT called.
     * **Validates: Requirements 3.3**
     */
    @Test
    fun `empty project completes without building graph`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 20), arbProjectKey()) { projectKey ->
            val scanStateRepo = InMemoryScanStateRepoForPreservation()
            val scanLogRepo = InMemoryScanLogRepoForPreservation()
            val kbRepo = TrackingKBRepoForPreservation()
            val engine = buildEngine(kbRepo, scanStateRepo, scanLogRepo, EmptyJiraClientForPreservation())

            val result = engine.startScan(projectKey)

            assertEquals(ScanStatus.COMPLETED, result.status, "Empty project should be COMPLETED")
            assertEquals(0, result.totalTickets, "Empty project should have 0 tickets")
            assertFalse(kbRepo.saveGraphCalled, "saveGraphData should NOT be called for empty project $projectKey")
        }
    }

    /**
     * Property: For any random (projectKey, ticketId), processTicket() → KB record saved.
     * **Validates: Requirements 3.1**
     */
    @Test
    fun `processTicket saves KB record`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 20), arbProjectKey(), arbTicketId()) { projectKey, ticketId ->
            val scanStateRepo = InMemoryScanStateRepoForPreservation()
            val scanLogRepo = InMemoryScanLogRepoForPreservation()
            val kbRepo = TrackingKBRepoForPreservation()
            val fullTicketId = "$projectKey-$ticketId"
            val engine = buildEngine(kbRepo, scanStateRepo, scanLogRepo, StubJiraClientForPreservation(listOf(fullTicketId)))

            engine.processTicket(projectKey, fullTicketId)

            val saved = kbRepo.savedRecords.any { it.ticketId == fullTicketId }
            assert(saved) { "KB record should be saved for ticket $fullTicketId" }
        }
    }

    // --- Test Doubles ---

    class TrackingKBRepoForPreservation : KBRepository {
        val savedRecords = mutableListOf<KBRecord>()
        var saveGraphCalled = false
        private val graphs = mutableMapOf<String, NetworkGraph>()

        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord): Boolean { savedRecords.add(record); return true }
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean {
            saveGraphCalled = true; graphs[projectKey] = graph; return true
        }
        override suspend fun getGraphData(projectKey: String) = graphs[projectKey]
    }

    class StubAIOrchestratorForPreservation : AIOrchestrator {
        override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean) = AnalysisResult(
            ticketId, RequirementSummary("Summary"), emptyList(),
            ComplexityAssessment(3.0, "Medium", emptyList()), AnalysisSource.FRESH_AI
        )
        override suspend fun testProvider(providerId: String) = ProviderTestResult(providerId, true, 100, "OK")
        override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
        override fun setFailoverOrder(providerIds: List<String>) {}
        override fun analyzeBottlenecks(totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double, blockedCount: Int) = emptyList<BottleneckAlert>()
        override fun generateVelocityTrend(totalTickets: Int, resolvedCount: Int) = emptyList<SprintVelocity>()
        override fun calculateAIVelocity(totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double) = 0.5
    }

    /** Jira client that returns tickets but adds delay to simulate slow processing. */
    class SlowJiraClientForPreservation(private val ticketKeys: List<String>) : JiraClient {
        override suspend fun getProjects() = listOf(JiraProject("1", "PROJ", "Test"))
        override suspend fun getIssues(projectKey: String, maxResults: Int) = ticketKeys.map { key ->
            JiraIssue(id = key, key = key, fields = JiraIssueFields(summary = "Ticket $key"))
        }
        override suspend fun getIssueDetails(issueKey: String): JiraIssue {
            delay(200) // slow enough so pause/cancel can fire
            return JiraIssue(id = issueKey, key = issueKey, fields = JiraIssueFields(summary = "Ticket $issueKey"))
        }
    }

    class StubJiraClientForPreservation(private val ticketKeys: List<String>) : JiraClient {
        override suspend fun getProjects() = listOf(JiraProject("1", "PROJ", "Test"))
        override suspend fun getIssues(projectKey: String, maxResults: Int) = ticketKeys.map { key ->
            JiraIssue(id = key, key = key, fields = JiraIssueFields(summary = "Ticket $key"))
        }
        override suspend fun getIssueDetails(issueKey: String) =
            JiraIssue(id = issueKey, key = issueKey, fields = JiraIssueFields(summary = "Ticket $issueKey"))
    }

    class EmptyJiraClientForPreservation : JiraClient {
        override suspend fun getProjects() = listOf(JiraProject("1", "PROJ", "Test"))
        override suspend fun getIssues(projectKey: String, maxResults: Int) = emptyList<JiraIssue>()
        override suspend fun getIssueDetails(issueKey: String): JiraIssue? = null
    }

    class StubAIAgentForPreservation : AIAgent {
        override suspend fun analyze(prompt: String, context: AIContext?) = AIResult.Success("ok")
        override fun getAgentName() = "StubAgent"
    }

    class InMemoryScanStateRepoForPreservation : ScanStateRepository {
        private val states = mutableMapOf<String, ScanState>()
        override suspend fun findByProjectKey(projectKey: String) = states[projectKey]
        override suspend fun save(state: ScanState) { states[state.projectKey] = state }
        override suspend fun delete(projectKey: String) { states.remove(projectKey) }
        override suspend fun findAllScanning() = states.values.filter { it.status == ScanStatus.SCANNING }
    }

    class InMemoryScanLogRepoForPreservation : ScanLogRepository {
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
