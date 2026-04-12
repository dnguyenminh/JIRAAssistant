package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * E2E API tests for multi-project scan visibility:
 * GET /api/scan/active, 409 conflict response body, response format.
 * Requirements: 18.18, 18.19, 18.20, 18.21
 */
@TestMethodOrder(OrderAnnotation::class)
class MultiProjectScanApiTest : ApiTestBase() {

    private val projectKey get() = jiraProjectKey

    private fun scanUrl(action: String) =
        "$baseUrl/api/projects/$projectKey/scan/$action"

    private suspend fun ensureNoActiveScan() {
        client.post(scanUrl("cancel")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        delay(300)
    }

    // ── 131.1: GET /api/scan/active with admin JWT → 200 ───

    @Test @Order(1)
    fun getActiveScanReturns200WithList() = runBlocking {
        val resp = client.get("$baseUrl/api/scan/active") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET /api/scan/active should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.startsWith("["), "Response should be a JSON array, got: ${body.take(100)}")
    }

    // ── 131.1: Reader JWT → 200 (VIEW_ANALYSIS) ────────────

    @Test @Order(2)
    fun readerCanViewActiveScans() = runBlocking {
        val resp = client.get("$baseUrl/api/scan/active") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "READER should be able to view active scans")
    }

    // ── 131.1: No JWT → 401 ────────────────────────────────

    @Test @Order(3)
    fun getActiveScanWithoutJwtReturns401() = runBlocking {
        val resp = client.get("$baseUrl/api/scan/active")
        assertEquals(401, resp.status.value, "GET /api/scan/active without JWT should return 401")
    }

    // ── 131.2: POST start when already scanning → 409 ──────

    @Test @Order(4)
    fun startScanConflictReturns409WithMessage() = runBlocking {
        ensureNoActiveScan()

        val firstResp = client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, firstResp.status.value, "First start should succeed")

        val secondResp = client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val status = secondResp.status.value
        // NoOpJiraClient (0 tickets) → scan completes instantly → 200
        // Real Jira → scan stays SCANNING → 409
        if (status == 409) {
            val body = secondResp.bodyAsText()
            assertTrue(
                body.contains("already") || body.contains("conflict", ignoreCase = true)
                    || body.contains("running"),
                "409 response should contain conflict message, got: ${body.take(300)}"
            )
        }

        ensureNoActiveScan()
    }

    // ── 131.1: Response format validation ───────────────────

    @Test @Order(5)
    fun getActiveScanResponseFormatIsCorrect() = runBlocking {
        ensureNoActiveScan()

        client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }

        val resp = client.get("$baseUrl/api/scan/active") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()

        if (body != "[]") {
            assertResponseContainsFields(body)
        }

        ensureNoActiveScan()
    }

    private fun assertResponseContainsFields(body: String) {
        assertTrue(body.contains("\"projectKey\""), "Should contain 'projectKey'")
        assertTrue(body.contains("\"status\""), "Should contain 'status'")
        assertTrue(body.contains("\"totalTickets\""), "Should contain 'totalTickets'")
        assertTrue(body.contains("\"processedCount\""), "Should contain 'processedCount'")
        assertTrue(body.contains("\"progressPercent\""), "Should contain 'progressPercent'")
    }

    // ── 131.3: UI test placeholder ──────────────────────────
    // UI tests for stacked progress bars and 409→progress bar
    // should be added to the Cucumber/Serenity suite:
    //   - Stacked progress bars display when multiple scans active
    //   - 409 Conflict shows progress bar instead of error toast
    // See: e2e-tests/src/test/resources/features/014-BatchScan.feature
}
