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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Property tests for IncrementalGraphBuilder.
 * Tag: Feature: incremental-graph-rendering
 */
@OptIn(ExperimentalKotest::class)
class IncrementalGraphBuildPropertyTest {

    private fun arbProjectKey(): Arb<String> =
        Arb.string(2, 8, Codepoint.alphanumeric()).map { it.uppercase() }

    private fun arbTicketCount(): Arb<Int> = Arb.int(1, 30)

    private fun arbBatchSize(): Arb<Int> = Arb.int(1, 10)

    private fun buildTicketKeys(projectKey: String, count: Int) =
        (1..count).map { "$projectKey-$it" }

    private fun buildEngine(
        kbRepo: TrackingKBRepoForIncremental,
        ticketKeys: List<String>
    ): BatchScanEngine = BatchScanEngine(
        aiOrchestrator = StubAIOrchestratorForIncremental(),
        kbRepository = kbRepo,
        jiraClientProvider = { StubJiraForIncremental(ticketKeys) },
        featureNetworkMapper = FeatureNetworkMapper(StubAIAgentForIncremental()),
        scanStateRepository = NoOpScanStateRepoForIncremental(),
        scanLogRepository = NoOpScanLogRepoForIncremental(),
        scope = CoroutineScope(Dispatchers.Default)
    )

    /**
     * Property 1: For any random (projectKey, ticketCount), after
     * triggerBuild() + debounce, saveGraphData() is called with a
     * non-empty NetworkGraph derived from all available issues.
     * **Validates: Requirements 1.1, 1.2**
     */
    @Test
    fun `triggerBuild calls saveGraphData after debounce`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 50),
            arbProjectKey(), arbTicketCount()
        ) { projectKey, ticketCount ->
            val ticketKeys = buildTicketKeys(projectKey, ticketCount)
            val kbRepo = TrackingKBRepoForIncremental()
            val engine = buildEngine(kbRepo, ticketKeys)
            val builder = IncrementalGraphBuilder(
                engine = engine,
                scope = CoroutineScope(Dispatchers.Default),
                debounceMs = 50L // short debounce for fast tests
            )

            builder.triggerBuild(projectKey)
            delay(200) // wait for debounce + build

            assertTrue(
                kbRepo.saveGraphCalled,
                "saveGraphData should be called after triggerBuild for $projectKey"
            )
            val graph = kbRepo.graphs[projectKey]
            assertTrue(
                graph != null && graph.nodes.isNotEmpty(),
                "Graph should have nodes for $ticketCount tickets in $projectKey"
            )
        }
    }

    /**
     * Property 1 (batch simulation): For any random (batchSize, ticketCount),
     * calling triggerBuild() after each simulated batch completion results
     * in saveGraphData() being called at least once.
     * **Validates: Requirements 1.1, 1.2**
     */
    @Test
    fun `triggerBuild after each batch results in saveGraphData called`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 50),
            arbProjectKey(), arbTicketCount(), arbBatchSize()
        ) { projectKey, ticketCount, batchSize ->
            val ticketKeys = buildTicketKeys(projectKey, ticketCount)
            val kbRepo = TrackingKBRepoForIncremental()
            val engine = buildEngine(kbRepo, ticketKeys)
            val builder = IncrementalGraphBuilder(
                engine = engine,
                scope = CoroutineScope(Dispatchers.Default),
                debounceMs = 50L
            )

            // Simulate batch completions
            val batches = ticketKeys.chunked(batchSize.coerceAtLeast(1))
            for (batch in batches) {
                builder.triggerBuild(projectKey)
            }
            delay(300) // wait for debounce + build

            assertTrue(
                kbRepo.saveGraphCalled,
                "saveGraphData should be called after batch triggers " +
                    "(${batches.size} batches of ~$batchSize for $projectKey)"
            )
            assertTrue(
                kbRepo.saveGraphCallCount >= 1,
                "saveGraphData should be called at least once"
            )
        }
    }

    /** Property 5: triggerBuild() returns immediately (non-blocking). **Validates: Requirements 4.1** */
    @Test
    fun `triggerBuild returns immediately without blocking`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 50),
            arbProjectKey(), arbTicketCount()
        ) { projectKey, ticketCount ->
            val ticketKeys = buildTicketKeys(projectKey, ticketCount)
            val kbRepo = TrackingKBRepoForIncremental()
            val engine = buildEngine(kbRepo, ticketKeys)
            val builder = IncrementalGraphBuilder(
                engine = engine,
                scope = CoroutineScope(Dispatchers.Default),
                debounceMs = 5000L // long debounce so build won't finish
            )

            val elapsed = measureTime { builder.triggerBuild(projectKey) }

            assertTrue(
                elapsed.inWholeMilliseconds < 50,
                "triggerBuild should return in <50ms but took ${elapsed.inWholeMilliseconds}ms"
            )
            assertFalse(
                kbRepo.saveGraphCalled,
                "saveGraphData should NOT be called yet (build is async with 5s debounce)"
            )
            builder.cancel()
        }
    }

    // --- Test Doubles ---

    class TrackingKBRepoForIncremental : KBRepository {
        var saveGraphCalled = false
        var saveGraphCallCount = 0
        val graphs = mutableMapOf<String, NetworkGraph>()

        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph): Boolean {
            saveGraphCalled = true
            saveGraphCallCount++
            graphs[projectKey] = graph
            return true
        }
        override suspend fun getGraphData(projectKey: String) = graphs[projectKey]
    }

    class StubAIOrchestratorForIncremental : AIOrchestrator {
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

    class StubJiraForIncremental(private val ticketKeys: List<String>) : JiraClient {
        override suspend fun getProjects() = listOf(JiraProject("1", "PROJ", "Test"))
        override suspend fun getIssues(projectKey: String, maxResults: Int) =
            ticketKeys.map { key ->
                JiraIssue(id = key, key = key, fields = JiraIssueFields(summary = "Ticket $key"))
            }
        override suspend fun getIssueDetails(issueKey: String) =
            JiraIssue(id = issueKey, key = issueKey, fields = JiraIssueFields(summary = "Ticket $issueKey"))
    }

    class StubAIAgentForIncremental : AIAgent {
        override suspend fun analyze(prompt: String, context: AIContext?) = AIResult.Success("ok")
        override fun getAgentName() = "StubAgent"
    }

    class NoOpScanStateRepoForIncremental : ScanStateRepository {
        override suspend fun findByProjectKey(key: String): ScanState? = null
        override suspend fun save(state: ScanState) {}
        override suspend fun delete(key: String) {}
        override suspend fun findAllScanning() = emptyList<ScanState>()
    }

    class NoOpScanLogRepoForIncremental : ScanLogRepository {
        override suspend fun addEntry(entry: ScanLogEntry) {}
        override suspend fun getByProjectKey(key: String, limit: Long) = emptyList<ScanLogEntry>()
        override suspend fun getByProjectKeyPaged(key: String, limit: Long, offset: Long) = emptyList<ScanLogEntry>()
        override suspend fun countByProjectKey(key: String) = 0L
        override suspend fun deleteByProjectKey(key: String) {}
    }
}
