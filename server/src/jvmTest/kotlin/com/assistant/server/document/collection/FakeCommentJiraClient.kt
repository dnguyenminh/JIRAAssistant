package com.assistant.server.document.collection

import com.assistant.jira.*

/**
 * Fake [JiraClient] that simulates a ticket with N comments,
 * returned in pages of configurable size.
 *
 * Comments have sequential `created` timestamps so ordering can be verified.
 * Tracks how many API calls were made for pagination assertions.
 *
 * @param totalComments Total number of comments the fake ticket has
 * @param pageSize Max comments returned per page (mirrors Jira maxResults)
 */
class FakeCommentJiraClient(
    private val totalComments: Int,
    private val pageSize: Int = 50
) : JiraClient {

    /** Number of times [getIssueComments] was called. */
    var apiCallCount: Int = 0
        private set

    private val allComments: List<JiraComment> = buildComments()

    override suspend fun getProjects(): List<JiraProject> = emptyList()
    override suspend fun getIssues(projectKey: String, maxResults: Int) =
        emptyList<JiraIssue>()

    override suspend fun getIssueDetails(issueKey: String): JiraIssue? = null

    override suspend fun getIssueComments(
        issueKey: String,
        startAt: Int,
        maxResults: Int
    ): JiraCommentPageResponse {
        apiCallCount++
        val effectivePageSize = minOf(maxResults, pageSize)
        val page = allComments.drop(startAt).take(effectivePageSize)
        return JiraCommentPageResponse(
            startAt = startAt,
            maxResults = effectivePageSize,
            total = totalComments,
            comments = page
        )
    }

    private fun buildComments(): List<JiraComment> = List(totalComments) { i ->
        JiraComment(
            id = "comment-$i",
            author = JiraUser(displayName = "User-${i % 5}"),
            created = "2024-01-${(i + 1).toString().padStart(2, '0')}T10:00:00Z",
            updated = null,
            body = kotlinx.serialization.json.JsonPrimitive("Body of comment $i")
        )
    }
}
