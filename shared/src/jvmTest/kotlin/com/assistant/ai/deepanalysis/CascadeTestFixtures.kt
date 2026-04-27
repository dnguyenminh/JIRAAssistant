package com.assistant.ai.deepanalysis

import com.assistant.ai.*
import com.assistant.ai.deepanalysis.models.*
import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository

/**
 * Test fakes for CascadingAnalysisEngine unit tests.
 * Validates: Requirements 26.1-26.7
 */

/** Fake AIOrchestrator — records analyzeTicket calls. */
class FakeCascadeOrchestrator : AIOrchestrator {
    val analyzedTickets = mutableListOf<String>()
    var shouldFail: Set<String> = emptySet()

    override suspend fun analyzeTicket(
        ticketId: String, forceReanalyze: Boolean
    ): AnalysisResult {
        analyzedTickets.add(ticketId)
        if (ticketId in shouldFail) error("Simulated failure for $ticketId")
        return AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(unified = "Summary $ticketId"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 3.0, description = "test"),
            source = AnalysisSource.FRESH_AI
        )
    }

    override suspend fun testProvider(providerId: String) =
        ProviderTestResult(providerId, true, 100, "ok")
    override suspend fun getProviderStatuses() = emptyList<ProviderStatus>()
    override fun setFailoverOrder(providerIds: List<String>) {}
    override fun analyzeBottlenecks(
        totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double, blockedCount: Int
    ) = emptyList<BottleneckAlert>()
    override fun generateVelocityTrend(totalTickets: Int, resolvedCount: Int) =
        emptyList<SprintVelocity>()
    override fun calculateAIVelocity(
        totalTickets: Int, resolvedCount: Int, cycleTimeDays: Double
    ) = 0.0
}

/** Fake KBRepository — in-memory store for cascade tests. */
class FakeCascadeKBRepo : KBRepository {
    val existing = mutableSetOf<String>()
    override suspend fun findByTicketId(ticketId: String): KBRecord? =
        if (ticketId in existing) cachedRecord(ticketId) else null
    override suspend fun save(record: KBRecord) = true
    override suspend fun overwrite(record: KBRecord) = true
    override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
    override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
}

/** Fake JiraContentExtractor — returns configurable related tickets. */
class FakeCascadeExtractor : JiraContentExtractor {
    /** Map of ticketId → StructuredTicketContent to return. */
    val contentMap = mutableMapOf<String, StructuredTicketContent>()

    override suspend fun extract(ticketId: String): StructuredTicketContent =
        contentMap[ticketId] ?: StructuredTicketContent(summary = "Ticket $ticketId")
}

/** Helper to build a minimal KBRecord. */
internal fun cachedRecord(ticketId: String) = KBRecord(
    ticketId = ticketId,
    requirementSummary = "Cached",
    evolutionHistory = emptyList(),
    scrumPoints = 5.0,
    confidenceScore = 0.9,
    rationale = "cached",
    similarTicketRefs = emptyList(),
    timestamp = "1000"
)

/** Helper to build StructuredTicketContent with issue links. */
internal fun contentWithLinks(
    vararg linkedKeys: String
): StructuredTicketContent = StructuredTicketContent(
    summary = "Test",
    issueLinks = linkedKeys.map { IssueLinkInfo(key = it, summary = "Link $it") }
)

/** Helper to build StructuredTicketContent with sub-tasks. */
internal fun contentWithSubTasks(
    vararg subKeys: String
): StructuredTicketContent = StructuredTicketContent(
    summary = "Test",
    subTasks = subKeys.map { SubTaskInfo(key = it, summary = "Sub $it") }
)
