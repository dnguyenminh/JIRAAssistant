package com.assistant.scan

import com.assistant.ai.*
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.domain.NetworkGraph
import com.assistant.jira.*
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.settings.SettingsRepository
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
import kotlin.test.assertNotNull

/**
 * Property 3: Final graph build on scan completion.
 * Tag: Feature: incremental-graph-rendering, Property 3: Final graph build
 * **Validates: Requirements 1.4, 5.3**
 */
@OptIn(ExperimentalKotest::class)
class CompleteScanFinalGraphPropertyTest {

    private fun arbTicketCount(): Arb<Int> = Arb.int(1, 30)

    private fun arbProjectKey(): Arb<String> =
        Arb.string(2, 8, Codepoint.alphanumeric()).map { it.uppercase() }

    /**
     * Property 3: For any project with N tickets (1..30), when scan
     * completes, getGraphData(projectKey) SHALL return non-null.
     * Wires IncrementalGraphBuilder to verify incremental builds
     * don't interfere with the final build in completeScan().
     * **Validates: Requirements 1.4, 5.3**
     */
    @Test
    fun `getGraphData returns non-null after scan completes`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 50),
            arbProjectKey(), arbTicketCount()
        ) { projectKey, ticketCount ->
            val ticketKeys = (1..ticketCount).map { "$projectKey-$it" }
            val scanStateRepo = InMemoryScanStateForFinalGraph()
            val scanLogRepo = NoOpScanLogForFinalGraph()
            val kbRepo = TrackingKBRepoForFinalGraph()

            val engine = buildFinalGraphEngine(
                kbRepo, scanStateRepo, scanLogRepo, ticketKeys
            )
            wireIncrementalBuilder(engine)

            engine.startScan(projectKey)
            awaitScanDone(scanStateRepo, projectKey)

            val finalState = scanStateRepo.findByProjectKey(projectKey)
            assertEquals(
                ScanStatus.COMPLETED, finalState?.status,
                "Scan must reach COMPLETED ($projectKey, $ticketCount tickets)"
            )

            val graph = kbRepo.getGraphData(projectKey)
            assertNotNull(graph, "getGraphData must return non-null after scan completes ($projectKey)")
        }
    }
}

// --- Engine builder & helpers ---

private fun buildFinalGraphEngine(
    kbRepo: KBRepository,
    scanStateRepo: ScanStateRepository,
    scanLogRepo: ScanLogRepository,
    ticketKeys: List<String>
): BatchScanEngine = BatchScanEngine(
    aiOrchestrator = StubAIForFinalGraph(),
    kbRepository = kbRepo,
    jiraClientProvider = { StubJiraForFinalGraph(ticketKeys) },
    featureNetworkMapper = FeatureNetworkMapper(StubAIAgentForFinalGraph()),
    scanStateRepository = scanStateRepo,
    scanLogRepository = scanLogRepo,
    scope = CoroutineScope(Dispatchers.Default),
    settingsRepository = NoOpSettingsForFinalGraph()
)

private fun wireIncrementalBuilder(engine: BatchScanEngine) {
    val builder = IncrementalGraphBuilder(
        engine = engine,
        scope = CoroutineScope(Dispatchers.Default),
        debounceMs = 10L
    )
    engine.incrementalGraphBuilder = builder
}

private suspend fun awaitScanDone(
    repo: InMemoryScanStateForFinalGraph,
    projectKey: String,
    timeoutMs: Long = 30_000L
) {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMs) {
        val state = repo.findByProjectKey(projectKey)
        if (state?.status == ScanStatus.COMPLETED) return
        if (state?.status == ScanStatus.CANCELLED) return
        delay(50)
    }
}

// --- Test Doubles ---

private class TrackingKBRepoForFinalGraph : KBRepository {
    private val graphs = mutableMapOf<String, NetworkGraph>()
    override suspend fun findByTicketId(ticketId: String): KBRecord? = null
    override suspend fun save(record: KBRecord) = true
    override suspend fun overwrite(record: KBRecord) = true
    override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean {
        graphs[projectKey] = graph; return true
    }
    override suspend fun getGraphData(projectKey: String) = graphs[projectKey]
}

private class StubAIForFinalGraph : AIOrchestrator {
    override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean) =
        AnalysisResult(
            ticketId, RequirementSummary("Summary"),
            emptyList(), ComplexityAssessment(3.0, "Medium", emptyList()),
            AnalysisSource.FRESH_AI
        )
    override suspend fun testProvider(id: String) = ProviderTestResult(id, true, 100, "OK")
    override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
    override fun setFailoverOrder(ids: List<String>) {}
    override fun analyzeBottlenecks(t: Int, r: Int, c: Double, b: Int) = emptyList<BottleneckAlert>()
    override fun generateVelocityTrend(t: Int, r: Int) = emptyList<SprintVelocity>()
    override fun calculateAIVelocity(t: Int, r: Int, c: Double) = 0.5
}

private class StubJiraForFinalGraph(private val keys: List<String>) : JiraClient {
    override suspend fun getProjects() = listOf(JiraProject("1", "PROJ", "Test"))
    override suspend fun getIssues(projectKey: String, maxResults: Int) =
        keys.map { JiraIssue(id = it, key = it, fields = JiraIssueFields(summary = "Ticket $it")) }
    override suspend fun getIssueDetails(issueKey: String) =
        JiraIssue(id = issueKey, key = issueKey, fields = JiraIssueFields(summary = "Ticket $issueKey"))
}

private class StubAIAgentForFinalGraph : AIAgent {
    override suspend fun analyze(prompt: String, context: AIContext?) = AIResult.Success("ok")
    override fun getAgentName() = "StubAgent"
}

private class InMemoryScanStateForFinalGraph : ScanStateRepository {
    private val states = mutableMapOf<String, ScanState>()
    override suspend fun findByProjectKey(key: String) = states[key]
    override suspend fun save(state: ScanState) { states[state.projectKey] = state }
    override suspend fun delete(key: String) { states.remove(key) }
    override suspend fun findAllScanning() = states.values.filter { it.status == ScanStatus.SCANNING }
}

private class NoOpScanLogForFinalGraph : ScanLogRepository {
    override suspend fun addEntry(entry: ScanLogEntry) {}
    override suspend fun getByProjectKey(key: String, limit: Long) = emptyList<ScanLogEntry>()
    override suspend fun getByProjectKeyPaged(key: String, limit: Long, offset: Long) = emptyList<ScanLogEntry>()
    override suspend fun countByProjectKey(key: String) = 0L
    override suspend fun deleteByProjectKey(key: String) {}
}

private class NoOpSettingsForFinalGraph : SettingsRepository {
    override suspend fun getAll() = emptyMap<String, String>()
    override suspend fun get(key: String): String? = null
    override suspend fun put(key: String, value: String) {}
    override suspend fun putAll(settings: Map<String, String>) {}
}
