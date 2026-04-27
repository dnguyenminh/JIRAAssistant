package com.assistant.server.document.cache

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.jira.*
import com.assistant.server.document.models.*

/**
 * Test infrastructure for TraversalCache property tests.
 *
 * Provides fake JiraClient and graph builder helpers.
 */

/** Build a minimal [TicketGraph] with a single root node. */
fun buildTestGraph(rootId: String, updatedAt: String): TicketGraph {
    val node = TicketNode(
        ticketId = rootId,
        depth = 0,
        discoveredVia = RelationshipType.ROOT,
        parentDiscoveryId = rootId,
        issue = StructuredTicketContent(
            summary = "Root ticket",
            updatedDate = updatedAt
        )
    )
    return TicketGraph(
        rootTicketId = rootId,
        nodes = mapOf(rootId to node),
        edges = emptyList(),
        metadata = TraversalMetadata(
            totalDiscovered = 1,
            totalFetched = 1,
            totalSkipped = 0,
            maxDepthReached = 0,
            traversalTimeMs = 100
        )
    )
}

/**
 * Fake [JiraClient] that returns a ticket with a configurable updated_at.
 * Used to test cache TTL validation against root ticket freshness.
 */
class FakeCacheJiraClient(
    private val updatedAt: String
) : JiraClient {
    override suspend fun getProjects() = emptyList<JiraProject>()
    override suspend fun getIssues(projectKey: String, maxResults: Int) =
        emptyList<JiraIssue>()

    override suspend fun getIssueDetails(issueKey: String): JiraIssue {
        return JiraIssue(
            id = issueKey, key = issueKey,
            fields = JiraIssueFields(
                summary = "Test",
                status = JiraStatus("Open"),
                issuetype = JiraIssueType("Story"),
                updated = updatedAt
            )
        )
    }

    override suspend fun getIssueComments(
        issueKey: String, startAt: Int, maxResults: Int
    ) = JiraCommentPageResponse()
}
