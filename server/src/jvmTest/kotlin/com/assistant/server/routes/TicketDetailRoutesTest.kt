package com.assistant.server.routes

import com.assistant.graph.LinkedTicketDTO
import com.assistant.graph.SubTaskDTO
import com.assistant.jira.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for TicketDetailRoutes — linked tickets and sub-tasks mapping.
 * Requirements: 20.3, 20.5
 */
class TicketDetailRoutesTest {

    // --- fetchLinkedTickets: correct mapping ---

    @Test
    fun `fetchLinkedTickets returns correct LinkedTicketDTOs for outward links`() = runTest {
        val link = JiraIssueLink(
            id = "1",
            type = JiraIssueLinkType(name = "Blocks", inward = "is blocked by", outward = "blocks"),
            outwardIssue = JiraLinkedIssue("100", "ICL2-100", JiraLinkedIssueFields("Payment gateway"))
        )
        val issue = buildJiraIssue("ICL2-24", issuelinks = listOf(link))
        val client = FakeJiraClient(mapOf("ICL2-24" to issue))

        val result = fetchLinkedTickets(client, "ICL2-24")

        assertEquals(1, result.size)
        assertEquals(LinkedTicketDTO("ICL2-100", "Payment gateway", "blocks"), result[0])
    }

    @Test
    fun `fetchLinkedTickets returns correct LinkedTicketDTOs for inward links`() = runTest {
        val link = JiraIssueLink(
            id = "2",
            type = JiraIssueLinkType(name = "Blocks", inward = "is blocked by", outward = "blocks"),
            inwardIssue = JiraLinkedIssue("55", "ICL2-55", JiraLinkedIssueFields("Login redesign"))
        )
        val issue = buildJiraIssue("ICL2-24", issuelinks = listOf(link))
        val client = FakeJiraClient(mapOf("ICL2-24" to issue))

        val result = fetchLinkedTickets(client, "ICL2-24")

        assertEquals(1, result.size)
        assertEquals(LinkedTicketDTO("ICL2-55", "Login redesign", "is blocked by"), result[0])
    }

    @Test
    fun `fetchLinkedTickets returns empty list for NoOpJiraClient`() = runTest {
        val result = fetchLinkedTickets(NoOpJiraClient(), "ICL2-24")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchLinkedTickets returns empty list when ticket not found`() = runTest {
        val client = FakeJiraClient(emptyMap())
        val result = fetchLinkedTickets(client, "NONEXISTENT-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchLinkedTickets returns empty list when issuelinks is null`() = runTest {
        val issue = buildJiraIssue("ICL2-24", issuelinks = null)
        val client = FakeJiraClient(mapOf("ICL2-24" to issue))

        val result = fetchLinkedTickets(client, "ICL2-24")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchLinkedTickets returns empty list on exception`() = runTest {
        val result = fetchLinkedTickets(ThrowingJiraClient(), "ICL2-24")
        assertTrue(result.isEmpty())
    }

    // --- fetchSubTasks: correct mapping ---

    @Test
    fun `fetchSubTasks returns correct SubTaskDTOs`() = runTest {
        val subtasks = listOf(
            JiraSubtask("101", "ICL2-101", JiraSubtaskFields("OAuth flow", JiraStatus("In Progress"))),
            JiraSubtask("102", "ICL2-102", JiraSubtaskFields("Password reset", JiraStatus("To Do")))
        )
        val issue = buildJiraIssue("ICL2-24", subtasks = subtasks)
        val client = FakeJiraClient(mapOf("ICL2-24" to issue))

        val result = fetchSubTasks(client, "ICL2-24")

        assertEquals(2, result.size)
        assertEquals(SubTaskDTO("ICL2-101", "OAuth flow", "In Progress"), result[0])
        assertEquals(SubTaskDTO("ICL2-102", "Password reset", "To Do"), result[1])
    }

    @Test
    fun `fetchSubTasks returns empty list for NoOpJiraClient`() = runTest {
        val result = fetchSubTasks(NoOpJiraClient(), "ICL2-24")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchSubTasks returns empty list when ticket not found`() = runTest {
        val client = FakeJiraClient(emptyMap())
        val result = fetchSubTasks(client, "NONEXISTENT-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchSubTasks returns empty list when subtasks is null`() = runTest {
        val issue = buildJiraIssue("ICL2-24", subtasks = null)
        val client = FakeJiraClient(mapOf("ICL2-24" to issue))

        val result = fetchSubTasks(client, "ICL2-24")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchSubTasks returns empty list on exception`() = runTest {
        val result = fetchSubTasks(ThrowingJiraClient(), "ICL2-24")
        assertTrue(result.isEmpty())
    }
}
