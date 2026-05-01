package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.SectionClassifier
import com.assistant.ai.deepanalysis.models.*
import com.assistant.jira.JiraClient

/**
 * Fake [TicketFetcher] for property tests.
 *
 * Simulates a graph of tickets with configurable links, 403 responses,
 * and large content for early termination testing.
 */
class FakeTicketFetcher(
    private val tickets: Map<String, StructuredTicketContent>,
    private val permissionDeniedIds: Set<String> = emptySet()
) : TicketFetcher(DummyJiraClient, DummySectionClassifier) {

    override suspend fun fetch(ticketId: String): FetchResult {
        if (ticketId in permissionDeniedIds) {
            return FetchResult.PermissionDenied(ticketId)
        }
        val content = tickets[ticketId]
            ?: return FetchResult.Failed(ticketId, "Not found")
        return FetchResult.Success(content)
    }
}

/** Build a minimal [StructuredTicketContent] with given links. */
fun buildTicketContent(
    summary: String = "Summary",
    description: String = "Description",
    status: String = "Open",
    updatedDate: String = "2025-01-01T00:00:00Z",
    parentKey: String = "",
    issueLinks: List<IssueLinkInfo> = emptyList(),
    subTasks: List<SubTaskInfo> = emptyList(),
    comments: List<CommentInfo> = emptyList()
): StructuredTicketContent = StructuredTicketContent(
    summary = summary,
    description = description,
    status = status,
    updatedDate = updatedDate,
    parentKey = parentKey,
    issueLinks = issueLinks,
    subTasks = subTasks,
    comments = comments
)

/** Build content with large text to trigger early termination. */
fun buildLargeTicketContent(charCount: Int): StructuredTicketContent {
    val bigText = "X".repeat(charCount)
    return buildTicketContent(summary = bigText, description = bigText)
}
