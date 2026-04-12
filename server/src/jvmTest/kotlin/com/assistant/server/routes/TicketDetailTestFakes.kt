package com.assistant.server.routes

import com.assistant.jira.*

/**
 * Fake JiraClient for testing TicketDetailRoutes mapping logic.
 * Returns pre-configured JiraIssue or null for unknown keys.
 */
class FakeJiraClient(
    private val issues: Map<String, JiraIssue> = emptyMap()
) : JiraClient {

    override suspend fun getProjects(): List<JiraProject> = emptyList()

    override suspend fun getIssues(projectKey: String, maxResults: Int): List<JiraIssue> = emptyList()

    override suspend fun getIssueDetails(issueKey: String): JiraIssue? = issues[issueKey]
}

/** Fake JiraClient that always throws on getIssueDetails. */
class ThrowingJiraClient : JiraClient {

    override suspend fun getProjects(): List<JiraProject> = emptyList()

    override suspend fun getIssues(projectKey: String, maxResults: Int): List<JiraIssue> = emptyList()

    override suspend fun getIssueDetails(issueKey: String): JiraIssue? {
        throw RuntimeException("Jira connection failed")
    }
}

/** Helper to build a JiraIssue with links and subtasks. */
fun buildJiraIssue(
    key: String,
    issuelinks: List<JiraIssueLink>? = null,
    subtasks: List<JiraSubtask>? = null
): JiraIssue = JiraIssue(
    id = key.hashCode().toString(),
    key = key,
    fields = JiraIssueFields(
        summary = "Summary for $key",
        issuelinks = issuelinks,
        subtasks = subtasks
    )
)
