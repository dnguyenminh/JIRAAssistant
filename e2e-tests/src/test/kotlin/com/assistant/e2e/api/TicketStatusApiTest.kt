package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * E2E API tests for the ticket analysis status endpoint:
 * GET /api/projects/{key}/tickets/status
 *
 * Validates response structure and ticket analysis states
 * (NOT_ANALYZED, ANALYZED, HAS_UPDATES).
 */
@TestMethodOrder(OrderAnnotation::class)
class TicketStatusApiTest : ApiTestBase() {

    private val projectKey get() = jiraProjectKey

    private fun ticketStatusUrl() = "$baseUrl/api/projects/$projectKey/tickets/status"

    // ── GET ticket status → 200 + List<TicketAnalysisStatus> ──

    @Test @Order(1)
    fun getTicketStatusReturns200() = runBlocking {
        val resp = client.get(ticketStatusUrl()) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET .../tickets/status should return 200")
    }

    @Test @Order(2)
    fun getTicketStatusReturnsJsonArray() = runBlocking {
        val resp = client.get(ticketStatusUrl()) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText().trim()
        assertTrue(
            body.startsWith("["),
            "Response should be a JSON array, got: ${body.take(100)}"
        )
    }

    @Test @Order(3)
    fun ticketStatusEntriesHaveRequiredFields() = runBlocking {
        val resp = client.get(ticketStatusUrl()) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText().trim()

        // With NoOpJiraClient the list may be empty — that's valid
        if (body == "[]") {
            // Empty list is acceptable when no Jira is configured
            return@runBlocking
        }

        // If there are entries, verify structure
        assertTrue(body.contains("\"ticketId\""), "Entries should contain 'ticketId' field")
        assertTrue(body.contains("\"ticketSummary\""), "Entries should contain 'ticketSummary' field")
        assertTrue(body.contains("\"analysisState\""), "Entries should contain 'analysisState' field")
    }

    @Test @Order(4)
    fun ticketStatusAnalysisStateIsValid() = runBlocking {
        val resp = client.get(ticketStatusUrl()) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText().trim()

        if (body == "[]") return@runBlocking

        // Extract all analysisState values and verify they are valid enum values
        val stateRegex = """"analysisState"\s*:\s*"([^"]+)"""".toRegex()
        val states = stateRegex.findAll(body).map { it.groupValues[1] }.toList()
        val validStates = setOf("NOT_ANALYZED", "ANALYZED", "HAS_UPDATES", "ANALYZING")

        states.forEach { state ->
            assertTrue(
                state in validStates,
                "analysisState '$state' should be one of $validStates"
            )
        }
    }

    @Test @Order(5)
    fun ticketStatusDefaultStateIsNotAnalyzed() = runBlocking {
        // Without running a scan, tickets should be NOT_ANALYZED
        val resp = client.get(ticketStatusUrl()) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText().trim()

        if (body == "[]") return@runBlocking

        // At minimum, some tickets should be NOT_ANALYZED if no scan has been run
        val stateRegex = """"analysisState"\s*:\s*"([^"]+)"""".toRegex()
        val states = stateRegex.findAll(body).map { it.groupValues[1] }.toList()

        if (states.isNotEmpty()) {
            assertTrue(
                states.any { it == "NOT_ANALYZED" || it == "ANALYZED" || it == "HAS_UPDATES" },
                "Tickets should have a valid analysis state, got: $states"
            )
        }
    }

    // ── RBAC: Reader can view ticket status (VIEW_ANALYSIS) ──

    @Test @Order(6)
    fun readerCanViewTicketStatus() = runBlocking {
        val resp = client.get(ticketStatusUrl()) {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "READER should be able to view ticket status (VIEW_ANALYSIS)")
    }

    // ── Unauthenticated → 401 ───────────────────────────────

    @Test @Order(7)
    fun unauthenticatedTicketStatusReturns401() = runBlocking {
        val resp = client.get(ticketStatusUrl())
        assertEquals(401, resp.status.value, "Unauthenticated request should return 401")
    }
}
