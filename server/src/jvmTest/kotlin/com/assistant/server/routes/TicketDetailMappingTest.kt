package com.assistant.server.routes

import com.assistant.graph.LinkedTicketDTO
import com.assistant.graph.SubTaskDTO
import com.assistant.jira.*
import kotlin.test.*

/**
 * Unit tests for TicketDetailRoutes mapping functions — edge cases.
 * Requirements: 20.3, 20.4, 20.5, 20.6
 */
class TicketDetailMappingTest {

    // --- mapIssueLinkToDtos edge cases ---

    @Test
    fun `mapIssueLinkToDtos skips outward issue matching currentKey`() {
        val link = JiraIssueLink(
            id = "1",
            type = JiraIssueLinkType("Relates", "relates to", "relates to"),
            outwardIssue = JiraLinkedIssue("24", "ICL2-24", JiraLinkedIssueFields("Self ref"))
        )
        val result = mapIssueLinkToDtos(link, "ICL2-24")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mapIssueLinkToDtos handles both inward and outward issues`() {
        val link = JiraIssueLink(
            id = "1",
            type = JiraIssueLinkType("Blocks", "is blocked by", "blocks"),
            outwardIssue = JiraLinkedIssue("100", "ICL2-100", JiraLinkedIssueFields("Outward")),
            inwardIssue = JiraLinkedIssue("55", "ICL2-55", JiraLinkedIssueFields("Inward"))
        )
        val result = mapIssueLinkToDtos(link, "ICL2-24")
        assertEquals(2, result.size)
        assertEquals("blocks", result[0].relationship)
        assertEquals("is blocked by", result[1].relationship)
    }

    @Test
    fun `mapIssueLinkToDtos uses outward string even when empty`() {
        val link = JiraIssueLink(
            id = "1",
            type = JiraIssueLinkType(name = "Duplicate", inward = "", outward = ""),
            outwardIssue = JiraLinkedIssue("100", "ICL2-100", JiraLinkedIssueFields("Dup"))
        )
        val result = mapIssueLinkToDtos(link, "ICL2-24")
        assertEquals(1, result.size)
        // outward is "" (not null), so elvis doesn't trigger — returns ""
        assertEquals("", result[0].relationship)
    }

    @Test
    fun `mapIssueLinkToDtos defaults to relates to when type is null`() {
        val link = JiraIssueLink(
            id = "1",
            type = null,
            outwardIssue = JiraLinkedIssue("100", "ICL2-100", JiraLinkedIssueFields("No type"))
        )
        val result = mapIssueLinkToDtos(link, "ICL2-24")
        assertEquals("relates to", result[0].relationship)
    }

    @Test
    fun `mapIssueLinkToDtos uses key as summary fallback when fields null`() {
        val link = JiraIssueLink(
            id = "1",
            type = JiraIssueLinkType("Relates", "relates to", "relates to"),
            outwardIssue = JiraLinkedIssue("100", "ICL2-100", fields = null)
        )
        val result = mapIssueLinkToDtos(link, "ICL2-24")
        assertEquals("ICL2-100", result[0].summary)
    }

    // --- mapSubTaskToDto edge cases ---

    @Test
    fun `mapSubTaskToDto returns null for blank key`() {
        val subtask = JiraSubtask("", "", JiraSubtaskFields("Some task"))
        assertNull(mapSubTaskToDto(subtask))
    }

    @Test
    fun `mapSubTaskToDto uses key as summary fallback when fields null`() {
        val subtask = JiraSubtask("101", "ICL2-101", fields = null)
        val result = mapSubTaskToDto(subtask)
        assertNotNull(result)
        assertEquals("ICL2-101", result.summary)
        assertEquals("Unknown", result.status)
    }

    @Test
    fun `mapSubTaskToDto uses Unknown status when status is null`() {
        val subtask = JiraSubtask("101", "ICL2-101", JiraSubtaskFields("Task", status = null))
        val result = mapSubTaskToDto(subtask)
        assertNotNull(result)
        assertEquals("Unknown", result.status)
    }

    @Test
    fun `mapSubTaskToDto maps all fields correctly`() {
        val subtask = JiraSubtask(
            "101", "ICL2-101",
            JiraSubtaskFields("Implement OAuth", JiraStatus("Done", "3"))
        )
        val result = mapSubTaskToDto(subtask)
        assertEquals(SubTaskDTO("ICL2-101", "Implement OAuth", "Done"), result)
    }

    // --- mapIssueLinkToDtos with empty list ---

    @Test
    fun `mapIssueLinkToDtos returns empty when no inward or outward`() {
        val link = JiraIssueLink(id = "1", type = null, inwardIssue = null, outwardIssue = null)
        val result = mapIssueLinkToDtos(link, "ICL2-24")
        assertTrue(result.isEmpty())
    }
}
