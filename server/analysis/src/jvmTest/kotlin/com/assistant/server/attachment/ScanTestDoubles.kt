package com.assistant.server.attachment

import com.assistant.ai.*
import com.assistant.domain.NetworkGraph
import com.assistant.domain.FeatureNetworkMapper
import com.assistant.jira.*
import com.assistant.scan.*

/** Stub AIOrchestrator returning canned analysis results. */
class StubAIOrchestrator : AIOrchestrator {
    override suspend fun analyzeTicket(ticketId: String, forceReanalyze: Boolean) =
        AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary("Summary for $ticketId"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(3.0, "Medium", emptyList()),
            source = AnalysisSource.FRESH_AI
        )

    override suspend fun testProvider(providerId: String) =
        ProviderTestResult(providerId, true, 100, "OK")

    override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
    override fun setFailoverOrder(providerIds: List<String>) {}

    override fun analyzeBottlenecks(
        totalTickets: Int, resolvedCount: Int,
        cycleTimeDays: Double, blockedCount: Int
    ) = emptyList<BottleneckAlert>()

    override fun generateVelocityTrend(totalTickets: Int, resolvedCount: Int) =
        emptyList<SprintVelocity>()

    override fun calculateAIVelocity(
        totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double
    ) = 0.5
}

/** Stub JiraClient returning configurable tickets with attachments. */
class StubJiraClient(
    private val ticketKeys: List<String>,
    private val ticketAttachments: Map<String, List<JiraAttachment>> = emptyMap()
) : JiraClient {

    override suspend fun getProjects() = listOf(
        JiraProject("1", "PROJ", "Test Project")
    )

    override suspend fun getIssues(projectKey: String, maxResults: Int) =
        ticketKeys.map { key ->
            JiraIssue(id = key, key = key, fields = JiraIssueFields(summary = "Ticket $key"))
        }

    override suspend fun getIssueDetails(issueKey: String) =
        JiraIssue(
            id = issueKey, key = issueKey,
            fields = JiraIssueFields(
                summary = "Ticket $issueKey",
                attachment = ticketAttachments[issueKey]
            )
        )
}

/** FeatureNetworkMapper with stub AI agent for testing. */
fun stubNetworkMapper() = FeatureNetworkMapper(aiAgent = StubAIAgent())

/** Minimal AIAgent stub for FeatureNetworkMapper. */
class StubAIAgent : AIAgent {
    override suspend fun analyze(prompt: String, context: AIContext?) =
        AIResult.Success("ok")
    override fun getAgentName() = "StubAgent"
}

/** In-memory ScanStateRepository for testing. */
class InMemoryScanStateRepo : ScanStateRepository {
    private val states = mutableMapOf<String, ScanState>()

    override suspend fun findByProjectKey(projectKey: String) =
        states[projectKey]

    override suspend fun save(state: ScanState) {
        states[state.projectKey] = state
    }

    override suspend fun delete(projectKey: String) {
        states.remove(projectKey)
    }

    override suspend fun findAllScanning() =
        states.values.filter { it.status == ScanStatus.SCANNING }
}
