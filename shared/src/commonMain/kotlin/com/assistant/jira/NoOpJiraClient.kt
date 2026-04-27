package com.assistant.jira

/**
 * No-op implementation of [JiraClient] that returns empty results.
 * Used when Jira credentials have not been configured yet.
 */
class NoOpJiraClient : JiraClient {

    override suspend fun getProjects(): List<JiraProject> {
        println("[NoOpJiraClient] getProjects called — Jira credentials not configured")
        return emptyList()
    }

    override suspend fun getIssues(projectKey: String, maxResults: Int): List<JiraIssue> {
        println("[NoOpJiraClient] getIssues called for $projectKey — Jira credentials not configured")
        return emptyList()
    }

    override suspend fun getIssueDetails(issueKey: String): JiraIssue? {
        println("[NoOpJiraClient] getIssueDetails called for $issueKey — Jira credentials not configured")
        return null
    }

    override suspend fun getIssueComments(
        issueKey: String,
        startAt: Int,
        maxResults: Int
    ): JiraCommentPageResponse {
        println("[NoOpJiraClient] getIssueComments called for $issueKey — Jira credentials not configured")
        return JiraCommentPageResponse()
    }
}
