package com.assistant.scan

import com.assistant.ai.*
import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.domain.NetworkGraph
import com.assistant.jira.*
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.settings.SettingsRepository

/** Fake JiraContentExtractor returning a fixed StructuredTicketContent. */
class FakeContentExtractor(
    private val content: StructuredTicketContent
) : JiraContentExtractor {
    override suspend fun extract(ticketId: String) = content
}

/** JiraContentExtractor that always throws. */
class ThrowingContentExtractor : JiraContentExtractor {
    override suspend fun extract(ticketId: String): StructuredTicketContent {
        throw RuntimeException("Extraction failed for $ticketId")
    }
}

/** AIOrchestrator that captures analyzeTicketBatch calls. */
class CapturingAIOrchestrator(
    private val onBatch: suspend (List<Pair<String, String>>, Boolean) -> Map<String, AnalysisResult>
) : AIOrchestrator {
    override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean) =
        AnalysisResult(
            ticketId, RequirementSummary("Summary for $ticketId"),
            emptyList(), ComplexityAssessment(3.0, "Medium", emptyList()),
            AnalysisSource.FRESH_AI
        )

    override suspend fun analyzeTicketBatch(
        tickets: List<Pair<String, String>>,
        forceReanalyze: Boolean
    ) = onBatch(tickets, forceReanalyze)

    override suspend fun testProvider(id: String) = ProviderTestResult(id, true, 100, "OK")
    override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
    override fun setFailoverOrder(ids: List<String>) {}
    override fun analyzeBottlenecks(t: Int, r: Int, c: Double, b: Int) = emptyList<BottleneckAlert>()
    override fun generateVelocityTrend(t: Int, r: Int) = emptyList<SprintVelocity>()
    override fun calculateAIVelocity(t: Int, r: Int, c: Double) = 0.5
}

/** Stub JiraClient for batch processor tests. */
class StubJiraForBatch : JiraClient {
    override suspend fun getProjects() = listOf(JiraProject("1", "PROJ", "Test"))
    override suspend fun getIssues(projectKey: String, maxResults: Int) =
        listOf(JiraIssue(id = "PROJ-1", key = "PROJ-1", fields = JiraIssueFields(summary = "Ticket PROJ-1")))

    override suspend fun getIssueDetails(issueKey: String) =
        JiraIssue(id = issueKey, key = issueKey, fields = JiraIssueFields(summary = "Ticket $issueKey"))
}

/** KBRepository that tracks saves. */
class TrackingKBRepoForBatch : KBRepository {
    val savedRecords = mutableListOf<KBRecord>()
    override suspend fun findByTicketId(ticketId: String): KBRecord? = null
    override suspend fun save(record: KBRecord): Boolean { savedRecords.add(record); return true }
    override suspend fun overwrite(record: KBRecord) = true
    override suspend fun saveGraphData(key: String, graph: NetworkGraph) = true
    override suspend fun getGraphData(key: String): NetworkGraph? = null
}

/** No-op KBRepository. */
class NoOpKBRepo : KBRepository {
    override suspend fun findByTicketId(ticketId: String): KBRecord? = null
    override suspend fun save(record: KBRecord) = true
    override suspend fun overwrite(record: KBRecord) = true
    override suspend fun saveGraphData(key: String, graph: NetworkGraph) = true
    override suspend fun getGraphData(key: String): NetworkGraph? = null
}

/** Fixed SettingsRepository returning a configured batch_prompt_size. */
class FixedSettingsRepo(private val batchSize: String) : SettingsRepository {
    override suspend fun getAll() = mapOf("batch_prompt_size" to batchSize)
    override suspend fun get(key: String) = if (key == "batch_prompt_size") batchSize else null
    override suspend fun put(key: String, value: String) {}
    override suspend fun putAll(settings: Map<String, String>) {}
}

/** No-op ScanStateRepository. */
class NoOpScanStateRepo : ScanStateRepository {
    override suspend fun findByProjectKey(key: String): ScanState? = null
    override suspend fun save(state: ScanState) {}
    override suspend fun delete(key: String) {}
    override suspend fun findAllScanning() = emptyList<ScanState>()
}

/** No-op ScanLogRepository. */
class NoOpScanLogRepo : ScanLogRepository {
    override suspend fun addEntry(entry: ScanLogEntry) {}
    override suspend fun getByProjectKey(key: String, limit: Long) = emptyList<ScanLogEntry>()
    override suspend fun getByProjectKeyPaged(key: String, limit: Long, offset: Long) = emptyList<ScanLogEntry>()
    override suspend fun countByProjectKey(key: String) = 0L
    override suspend fun deleteByProjectKey(key: String) {}
}

/** Stub AIAgent for FeatureNetworkMapper. */
class StubAIAgentForBatch : AIAgent {
    override suspend fun analyze(prompt: String, context: AIContext?) = AIResult.Success("ok")
    override fun getAgentName() = "StubAgent"
}
