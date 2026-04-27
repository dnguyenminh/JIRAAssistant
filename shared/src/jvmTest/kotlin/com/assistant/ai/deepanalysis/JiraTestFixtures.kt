package com.assistant.ai.deepanalysis

import com.assistant.jira.*

/**
 * Shared test fixtures for JiraContentExtractor tests.
 * Provides FakeJiraClient and sample JiraIssue builders.
 */
class FakeJiraClient(
    private val issue: JiraIssue?
) : JiraClient {
    override suspend fun getProjects(): List<JiraProject> = emptyList()
    override suspend fun getIssues(projectKey: String, maxResults: Int) = emptyList<JiraIssue>()
    override suspend fun getIssueDetails(issueKey: String): JiraIssue? = issue
}

/** Build a fully populated JiraIssue for testing. */
fun buildFullJiraIssue(): JiraIssue {
    val fields = JiraIssueFields(
        summary = "Implement login",
        status = JiraStatus("In Progress"),
        priority = JiraPriority("High"),
        storyPoints = 5.0,
        issuetype = JiraIssueType("Story"),
        assignee = JiraUser("Alice Dev"),
        reporter = JiraUser("Bob PM"),
        created = "2024-01-01T00:00:00Z",
        updated = "2024-01-15T00:00:00Z",
        labels = listOf("backend", "auth"),
        components = listOf(
            JiraComponent("1", "Auth Module"),
            JiraComponent("2", "API Gateway")
        ),
        subtasks = listOf(
            JiraSubtask("s1", "PROJ-101", JiraSubtaskFields("Setup OAuth", JiraStatus("Done")))
        ),
        issuelinks = listOf(
            JiraIssueLink(
                id = "l1",
                type = JiraIssueLinkType("Blocks", "is blocked by", "blocks"),
                outwardIssue = JiraLinkedIssue("50", "PROJ-50", JiraLinkedIssueFields("DB setup"))
            )
        ),
        attachment = listOf(
            JiraAttachment("a1", "mockup.png", "image/png", 102400)
        ),
        comment = JiraCommentWrapper(
            comments = listOf(
                JiraComment("c1", JiraUser("Reviewer"), "2024-01-10T08:00:00Z")
            ),
            total = 1
        ),
        description = kotlinx.serialization.json.JsonPrimitive(
            "## As-Is\nManual login.\n## To-Be\nOAuth 2.0 login."
        )
    )
    return JiraIssue(
        id = "100",
        key = "PROJ-100",
        fields = fields,
        changelog = JiraChangelog(
            histories = listOf(
                JiraChangeHistory("h1", JiraUser("Admin"), "2024-01-05", listOf(
                    JiraChangeItem("status", "Open", "In Progress")
                ))
            )
        )
    )
}
