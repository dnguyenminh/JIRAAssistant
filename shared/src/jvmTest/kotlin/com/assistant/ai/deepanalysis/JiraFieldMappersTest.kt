package com.assistant.ai.deepanalysis

import com.assistant.jira.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for JiraFieldMappers — pure functions, no mocking.
 * Requirements: 16.3, 16.4, 16.5, 16.6, 16.7
 */
class JiraFieldMappersTest {

    // ── Req 16.3: Sub-tasks mapping ──

    @Test
    fun `mapSubTasks extracts summary and status`() {
        val subtasks = listOf(
            JiraSubtask("1", "SUB-1", JiraSubtaskFields("Setup DB", JiraStatus("Done"))),
            JiraSubtask("2", "SUB-2", JiraSubtaskFields("Write API", JiraStatus("In Progress")))
        )
        val result = JiraFieldMappers.mapSubTasks(subtasks)
        assertEquals(2, result.size)
        assertEquals("Setup DB", result[0].summary)
        assertEquals("Done", result[0].status)
        assertEquals("Write API", result[1].summary)
        assertEquals("In Progress", result[1].status)
    }

    @Test
    fun `mapSubTasks returns empty for null input`() {
        assertEquals(emptyList(), JiraFieldMappers.mapSubTasks(null))
    }

    // ── Req 16.4: Issue links mapping ──

    @Test
    fun `mapIssueLinks extracts outward link`() {
        val links = listOf(
            JiraIssueLink(
                id = "1",
                type = JiraIssueLinkType("Blocks", "is blocked by", "blocks"),
                outwardIssue = JiraLinkedIssue("10", "PROJ-10", JiraLinkedIssueFields("Auth module"))
            )
        )
        val result = JiraFieldMappers.mapIssueLinks(links)
        assertEquals(1, result.size)
        assertEquals("PROJ-10", result[0].key)
        assertEquals("Auth module", result[0].summary)
        assertEquals("blocks", result[0].relationshipType)
    }

    @Test
    fun `mapIssueLinks extracts inward link`() {
        val links = listOf(
            JiraIssueLink(
                id = "2",
                type = JiraIssueLinkType("Blocks", "is blocked by", "blocks"),
                inwardIssue = JiraLinkedIssue("20", "PROJ-20", JiraLinkedIssueFields("DB migration"))
            )
        )
        val result = JiraFieldMappers.mapIssueLinks(links)
        assertEquals(1, result.size)
        assertEquals("PROJ-20", result[0].key)
        assertEquals("is blocked by", result[0].relationshipType)
    }

    @Test
    fun `mapIssueLinks returns empty for null input`() {
        assertEquals(emptyList(), JiraFieldMappers.mapIssueLinks(null))
    }

    // ── Req 16.5: Attachments mapping ──

    @Test
    fun `mapAttachments extracts filename mimeType and size`() {
        val attachments = listOf(
            JiraAttachment("1", "design.png", "image/png", 204800),
            JiraAttachment("2", "spec.pdf", "application/pdf", 1048576)
        )
        val result = JiraFieldMappers.mapAttachments(attachments)
        assertEquals(2, result.size)
        assertEquals("design.png", result[0].filename)
        assertEquals("image/png", result[0].mimeType)
        assertEquals(204800L, result[0].size)
    }

    @Test
    fun `mapAttachments returns empty for null input`() {
        assertEquals(emptyList(), JiraFieldMappers.mapAttachments(null))
    }

    // ── Req 16.6: Comments mapping ──

    @Test
    fun `mapComments extracts author date and content`() {
        val wrapper = JiraCommentWrapper(
            comments = listOf(
                JiraComment("1", JiraUser("Alice"), "2024-01-15T10:00:00Z"),
                JiraComment("2", JiraUser("Bob"), "2024-01-16T11:00:00Z")
            ),
            total = 2
        )
        val result = JiraFieldMappers.mapComments(wrapper)
        assertEquals(2, result.size)
        assertEquals("Alice", result[0].author)
        assertEquals("2024-01-15T10:00:00Z", result[0].createdDate)
    }

    @Test
    fun `mapComments limits to 20 most recent`() {
        val comments = (1..25).map { i ->
            JiraComment("$i", JiraUser("User$i"), "2024-01-${i}T00:00:00Z")
        }
        val wrapper = JiraCommentWrapper(comments, 25)
        val result = JiraFieldMappers.mapComments(wrapper)
        assertEquals(20, result.size)
        assertEquals("User6", result[0].author) // takeLast(20) → starts at 6
    }

    @Test
    fun `mapComments returns empty for null wrapper`() {
        assertEquals(emptyList(), JiraFieldMappers.mapComments(null))
    }

    // ── Req 16.7: Changelog mapping ──

    @Test
    fun `mapChangelog extracts tracked fields only`() {
        val changelog = JiraChangelog(
            histories = listOf(
                JiraChangeHistory(
                    id = "1",
                    author = JiraUser("Admin"),
                    created = "2024-01-10T09:00:00Z",
                    items = listOf(
                        JiraChangeItem("status", "Open", "In Progress"),
                        JiraChangeItem("description", "old desc", "new desc") // not tracked
                    )
                )
            )
        )
        val result = JiraFieldMappers.mapChangelog(changelog)
        assertEquals(1, result.size)
        assertEquals("status", result[0].field)
        assertEquals("Open", result[0].oldValue)
        assertEquals("In Progress", result[0].newValue)
        assertEquals("Admin", result[0].changedBy)
    }

    @Test
    fun `mapChangelog tracks priority assignee summary storyPoints`() {
        val changelog = JiraChangelog(
            histories = listOf(
                JiraChangeHistory("1", JiraUser("PM"), "2024-02-01", listOf(
                    JiraChangeItem("priority", "Low", "High"),
                    JiraChangeItem("assignee", "Alice", "Bob"),
                    JiraChangeItem("summary", "Old title", "New title"),
                    JiraChangeItem("Story Points", "3", "5")
                ))
            )
        )
        val result = JiraFieldMappers.mapChangelog(changelog)
        assertEquals(4, result.size)
        assertTrue(result.any { it.field == "priority" })
        assertTrue(result.any { it.field == "assignee" })
        assertTrue(result.any { it.field == "summary" })
        assertTrue(result.any { it.field == "Story Points" })
    }

    @Test
    fun `mapChangelog returns empty for null changelog`() {
        assertEquals(emptyList(), JiraFieldMappers.mapChangelog(null))
    }
}
