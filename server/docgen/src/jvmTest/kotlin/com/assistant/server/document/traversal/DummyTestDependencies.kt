package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.SectionClassifier
import com.assistant.ai.deepanalysis.models.ClassifiedContent
import com.assistant.jira.*

/**
 * Dummy [JiraClient] that throws on every call.
 * Used only to satisfy [TicketFetcher] constructor — never invoked
 * because [FakeTicketFetcher] overrides [TicketFetcher.fetch].
 */
internal object DummyJiraClient : JiraClient {
    override suspend fun getProjects(): List<JiraProject> = error("dummy")
    override suspend fun getIssues(projectKey: String, maxResults: Int) =
        error("dummy")
    override suspend fun getIssueDetails(issueKey: String): JiraIssue? =
        error("dummy")
}

/**
 * Dummy [SectionClassifier] that returns empty [ClassifiedContent].
 * Used only to satisfy [TicketFetcher] constructor — never invoked
 * because [FakeTicketFetcher] overrides [TicketFetcher.fetch].
 */
internal object DummySectionClassifier : SectionClassifier {
    override fun classify(description: String) = ClassifiedContent()
}
