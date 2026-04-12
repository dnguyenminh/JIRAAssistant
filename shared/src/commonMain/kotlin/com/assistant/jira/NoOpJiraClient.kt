package com.assistant.jira

/**
 * No-op implementation of [JiraClient] that returns empty results.
 * Used when Jira credentials have not been configured yet.
 */
class NoOpJiraClient : JiraClient {

    override suspend fun getProjects(): List<JiraProject> = emptyList()

    override suspend fun getIssues(projectKey: String, maxResults: Int): List<JiraIssue> = emptyList()

    override suspend fun getIssueDetails(issueKey: String): JiraIssue? = null
}
