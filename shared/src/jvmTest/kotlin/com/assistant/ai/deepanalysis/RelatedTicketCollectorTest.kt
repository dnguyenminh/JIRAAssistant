package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for RelatedTicketCollector.
 * Validates: Requirement 26.1 — discovers related tickets from
 * issue links, sub-tasks, parent ticket, and comment mentions.
 */
class RelatedTicketCollectorTest {

    // ── Req 26.1: Issue links ──

    @Test
    fun `collect returns issue link keys`() {
        val content = StructuredTicketContent(
            issueLinks = listOf(
                IssueLinkInfo(key = "PROJ-10", relationshipType = "blocks"),
                IssueLinkInfo(key = "PROJ-11", relationshipType = "relates to")
            )
        )
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertTrue(result.contains("PROJ-10"))
        assertTrue(result.contains("PROJ-11"))
    }

    // ── Req 26.1: Sub-tasks ──

    @Test
    fun `collect returns sub-task keys`() {
        val content = StructuredTicketContent(
            subTasks = listOf(
                SubTaskInfo(key = "PROJ-20", summary = "Sub 1"),
                SubTaskInfo(key = "PROJ-21", summary = "Sub 2")
            )
        )
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertTrue(result.containsAll(setOf("PROJ-20", "PROJ-21")))
    }

    // ── Req 26.1: Parent ticket ──

    @Test
    fun `collect returns parent key`() {
        val content = StructuredTicketContent(parentKey = "PROJ-100")
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertTrue(result.contains("PROJ-100"))
    }

    @Test
    fun `collect ignores blank parent key`() {
        val content = StructuredTicketContent(parentKey = "")
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertTrue(result.isEmpty())
    }

    // ── Req 26.1: Comment mentions ──

    @Test
    fun `collect extracts ticket keys from comments`() {
        val content = StructuredTicketContent(
            comments = listOf(
                CommentInfo(content = "See PROJ-30 and PROJ-31 for details"),
                CommentInfo(content = "Related to ABC-5")
            )
        )
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertTrue(result.containsAll(setOf("PROJ-30", "PROJ-31", "ABC-5")))
    }

    // ── Excludes self ──

    @Test
    fun `collect excludes the ticket itself`() {
        val content = StructuredTicketContent(
            issueLinks = listOf(IssueLinkInfo(key = "PROJ-1")),
            parentKey = "PROJ-1",
            comments = listOf(CommentInfo(content = "See PROJ-1"))
        )
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertFalse(result.contains("PROJ-1"))
    }

    // ── Deduplication ──

    @Test
    fun `collect returns unique keys across all sources`() {
        val content = StructuredTicketContent(
            issueLinks = listOf(IssueLinkInfo(key = "PROJ-10")),
            subTasks = listOf(SubTaskInfo(key = "PROJ-10")),
            parentKey = "PROJ-10",
            comments = listOf(CommentInfo(content = "See PROJ-10"))
        )
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertEquals(1, result.size)
        assertTrue(result.contains("PROJ-10"))
    }

    // ── Empty content ──

    @Test
    fun `collect returns empty set for content with no relations`() {
        val content = StructuredTicketContent(summary = "Standalone ticket")
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertTrue(result.isEmpty())
    }

    // ── Blank keys filtered ──

    @Test
    fun `collect ignores blank issue link keys`() {
        val content = StructuredTicketContent(
            issueLinks = listOf(IssueLinkInfo(key = ""), IssueLinkInfo(key = "PROJ-5"))
        )
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertEquals(1, result.size)
        assertTrue(result.contains("PROJ-5"))
    }

    @Test
    fun `collect ignores blank sub-task keys`() {
        val content = StructuredTicketContent(
            subTasks = listOf(SubTaskInfo(key = ""), SubTaskInfo(key = "PROJ-6"))
        )
        val result = RelatedTicketCollector.collect("PROJ-1", content)
        assertEquals(1, result.size)
    }
}
