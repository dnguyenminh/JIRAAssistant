package com.assistant.server.document

import com.assistant.domain.NetworkGraph
import com.assistant.jira.*
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.scan.ScanLogEntry
import com.assistant.scan.ScanLogRepository
import com.assistant.server.document.cache.TraversalCache
import com.assistant.server.document.collection.FakeVectorStore
import com.assistant.server.document.jobs.CollectionJobManager
import com.assistant.server.document.models.*

/**
 * Minimal fakes for [DeepCollector] property tests.
 *
 * Each fake implements the minimum contract needed to run
 * DeepCollector.aggregate() end-to-end without real I/O.
 */

/** Fake [KBRepository] returning pre-configured KBRecords by ticket ID. */
class FakeKBRepository(
    private val records: Map<String, KBRecord> = emptyMap()
) : KBRepository {
    override suspend fun findByTicketId(ticketId: String) = records[ticketId]
    override suspend fun save(record: KBRecord) = true
    override suspend fun overwrite(record: KBRecord) = true
    override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
    override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
}

/** Fake [TraversalCache] that always misses (no caching). */
class NoOpTraversalCache : TraversalCache {
    override suspend fun get(rootTicketId: String, cacheTtlMinutes: Int): TicketGraph? = null
    override suspend fun put(rootTicketId: String, graph: TicketGraph) {}
    override suspend fun invalidate(rootTicketId: String) {}
}

/** Fake [RateLimiter] that always passes. */
class NoOpRateLimiter : com.assistant.server.document.security.RateLimiter {
    override suspend fun check(userId: String) {}
    override suspend fun record(userId: String) {}
}

/** Fake [CollectionJobManager] that creates no jobs. */
class NoOpCollectionJobManager : CollectionJobManager {
    override suspend fun createJobs(
        parentTicketId: String,
        graph: TicketGraph,
        missingKBTicketIds: List<String>,
        unprocessedAttachmentTicketIds: List<String>
    ): List<CollectionJob> = emptyList()

    override suspend fun executeJob(jobId: String) {}
    override suspend fun preemptItem(jobId: String, ticketId: String) {}
    override suspend fun isTicketProcessing(ticketId: String) = false
    override suspend fun preemptPendingForTicket(ticketId: String) {}
}

/** Fake [ScanLogRepository] that discards entries. */
class NoOpScanLogRepository : ScanLogRepository {
    override suspend fun addEntry(entry: ScanLogEntry) {}
    override suspend fun getByProjectKey(projectKey: String, limit: Long) = emptyList<ScanLogEntry>()
    override suspend fun getByProjectKeyPaged(projectKey: String, limit: Long, offset: Long) = emptyList<ScanLogEntry>()
    override suspend fun countByProjectKey(projectKey: String) = 0L
    override suspend fun deleteByProjectKey(projectKey: String) {}
}

/**
 * Fake [JiraClient] that returns [JiraIssue] objects for a set of ticket IDs.
 *
 * Each ticket has configurable issue links to simulate a graph.
 * Comments are always empty (not relevant for backward compat test).
 */
class GraphJiraClient(
    private val ticketLinks: Map<String, List<String>> = emptyMap()
) : JiraClient {
    override suspend fun getProjects() = emptyList<JiraProject>()
    override suspend fun getIssues(projectKey: String, maxResults: Int) = emptyList<JiraIssue>()

    override suspend fun getIssueDetails(issueKey: String): JiraIssue? {
        if (issueKey !in ticketLinks) return null
        val links = ticketLinks[issueKey] ?: emptyList()
        return buildJiraIssue(issueKey, links)
    }

    override suspend fun getIssueComments(
        issueKey: String, startAt: Int, maxResults: Int
    ) = JiraCommentPageResponse(startAt, maxResults, 0, emptyList())
}

/** Build a minimal [JiraIssue] with outward issue links. */
private fun buildJiraIssue(key: String, linkedKeys: List<String>): JiraIssue {
    val issueLinks = linkedKeys.map { targetKey ->
        JiraIssueLink(
            id = "$key->$targetKey",
            type = JiraIssueLinkType("Relates", "relates to", "relates to"),
            outwardIssue = JiraLinkedIssue(
                id = targetKey, key = targetKey,
                fields = JiraLinkedIssueFields(summary = "Linked $targetKey")
            )
        )
    }
    return JiraIssue(
        id = key, key = key,
        fields = JiraIssueFields(
            summary = "Summary of $key",
            status = JiraStatus("Open"),
            issuetype = JiraIssueType("Story"),
            created = "2025-01-01T00:00:00Z",
            updated = "2025-01-10T00:00:00Z",
            issuelinks = issueLinks
        )
    )
}

/** Build a minimal [KBRecord] for a ticket ID. */
fun buildKBRecord(ticketId: String) = KBRecord(
    ticketId = ticketId,
    requirementSummary = "Summary for $ticketId",
    evolutionHistory = emptyList(),
    scrumPoints = 3.0,
    confidenceScore = 0.8,
    rationale = "Rationale",
    similarTicketRefs = emptyList(),
    timestamp = "2025-01-01T00:00:00Z",
    businessSummary = "Business summary for $ticketId"
)
