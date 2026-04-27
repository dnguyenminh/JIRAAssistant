package com.assistant.ai.deepanalysis

import com.assistant.jira.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for JiraContentExtractorImpl.
 * Uses FakeJiraClient from JiraTestFixtures — no real HTTP calls.
 * Requirements: 16.1, 16.3-16.8
 */
class JiraContentExtractorTest {

    private val classifier = SectionClassifierImpl()

    // ── Req 16.1: Core field mapping ──

    @Test
    fun `extract maps all core fields correctly`() = runTest {
        val result = extractFullIssue()

        assertEquals("Implement login", result.summary)
        assertEquals("In Progress", result.status)
        assertEquals("High", result.priority)
        assertEquals(5.0, result.storyPoints)
        assertEquals("Story", result.issueType)
        assertEquals("Alice Dev", result.assignee)
        assertEquals("Bob PM", result.reporter)
        assertEquals("2024-01-01T00:00:00Z", result.createdDate)
        assertEquals("2024-01-15T00:00:00Z", result.updatedDate)
    }

    @Test
    fun `extract maps labels and components`() = runTest {
        val result = extractFullIssue()

        assertEquals(listOf("backend", "auth"), result.labels)
        assertEquals(listOf("Auth Module", "API Gateway"), result.components)
    }

    // ── Req 16.3: Sub-tasks ──

    @Test
    fun `extract includes sub-tasks`() = runTest {
        val result = extractFullIssue()

        assertEquals(1, result.subTasks.size)
        assertEquals("Setup OAuth", result.subTasks[0].summary)
        assertEquals("Done", result.subTasks[0].status)
    }

    // ── Req 16.4: Issue links ──

    @Test
    fun `extract includes issue links`() = runTest {
        val result = extractFullIssue()

        assertTrue(result.issueLinks.isNotEmpty())
        assertTrue(result.issueLinks.any { it.key == "PROJ-50" })
    }

    // ── Req 16.5: Attachments ──

    @Test
    fun `extract includes attachments metadata`() = runTest {
        val result = extractFullIssue()

        assertEquals(1, result.attachments.size)
        assertEquals("mockup.png", result.attachments[0].filename)
        assertEquals("image/png", result.attachments[0].mimeType)
        assertEquals(102400L, result.attachments[0].size)
    }

    // ── Req 16.6: Comments ──

    @Test
    fun `extract includes comments`() = runTest {
        val result = extractFullIssue()

        assertEquals(1, result.comments.size)
        assertEquals("Reviewer", result.comments[0].author)
    }

    // ── Req 16.7: Changelog ──

    @Test
    fun `extract includes changelog entries`() = runTest {
        val result = extractFullIssue()

        assertTrue(result.changelog.isNotEmpty())
        assertEquals("status", result.changelog[0].field)
    }

    // ── Req 16.8: Consolidation ──

    @Test
    fun `extract assembles full StructuredTicketContent`() = runTest {
        val result = extractFullIssue()

        assertTrue(result.description.isNotEmpty())
        assertTrue(result.subTasks.isNotEmpty())
        assertTrue(result.issueLinks.isNotEmpty())
        assertTrue(result.attachments.isNotEmpty())
        assertTrue(result.comments.isNotEmpty())
        assertTrue(result.changelog.isNotEmpty())
    }

    // ── Error case: ticket not found ──

    @Test
    fun `extract throws IllegalStateException when ticket not found`() = runTest {
        val extractor = createExtractor(null)
        assertFailsWith<IllegalStateException> {
            extractor.extract("NONEXISTENT-999")
        }
    }

    // ── Helpers ──

    private fun createExtractor(issue: JiraIssue?): JiraContentExtractorImpl {
        return JiraContentExtractorImpl({ FakeJiraClient(issue) }, classifier)
    }

    private suspend fun extractFullIssue() =
        createExtractor(buildFullJiraIssue()).extract("PROJ-100")
}
