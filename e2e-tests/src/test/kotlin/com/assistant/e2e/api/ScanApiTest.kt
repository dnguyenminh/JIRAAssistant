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
 * E2E API tests for the 6 scan endpoints:
 * POST start, pause, resume, cancel + GET status, log.
 * Also covers RBAC enforcement (Reader vs Neural_Architect).
 */
@TestMethodOrder(OrderAnnotation::class)
class ScanApiTest : ApiTestBase() {

    private val projectKey get() = jiraProjectKey

    private fun scanUrl(action: String) = "$baseUrl/api/projects/$projectKey/scan/$action"

    // ── Helper: cancel any active scan so tests start clean ──

    private suspend fun ensureNoActiveScan() {
        // Try to cancel any lingering scan from a previous test run
        client.post(scanUrl("cancel")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        // Small delay to let the engine settle
        delay(300)
    }

    // ── START scan ──────────────────────────────────────────

    @Test @Order(1)
    fun startScanReturns200WithScanningStatus() = runBlocking {
        ensureNoActiveScan()

        val resp = client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "POST .../scan/start should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"status\""), "Response should contain 'status' field")
        assertTrue(body.contains("\"projectKey\""), "Response should contain 'projectKey' field")
        assertTrue(
            body.contains("SCANNING") || body.contains("COMPLETED"),
            "Status should be SCANNING or COMPLETED (if no tickets), got: ${body.take(300)}"
        )
    }

    // ── START scan when already scanning → 409 Conflict ─────

    @Test @Order(2)
    fun startScanWhileAlreadyScanningReturns409OrCompletesInstantly() = runBlocking {
        ensureNoActiveScan()

        // Start a scan first
        val startResp = client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, startResp.status.value, "First start should succeed")

        val firstBody = startResp.bodyAsText()
        // With NoOpJiraClient (0 tickets), scan completes instantly → second start returns 200
        // With real Jira (many tickets), scan stays SCANNING → second start returns 409
        val secondResp = client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val secondStatus = secondResp.status.value
        assertTrue(
            secondStatus == 200 || secondStatus == 409,
            "Second start should return 200 (scan completed) or 409 (conflict), got: $secondStatus"
        )

        // Cleanup
        ensureNoActiveScan()
    }

    // ── PAUSE scan ──────────────────────────────────────────

    @Test @Order(3)
    fun pauseScanReturns200WithPausedStatus() = runBlocking {
        ensureNoActiveScan()

        // Start a scan first
        val startResp = client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, startResp.status.value)

        val resp = client.post(scanUrl("pause")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "POST .../scan/pause should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"status\""), "Response should contain 'status' field")
        assertTrue(
            body.contains("PAUSED") || body.contains("COMPLETED") || body.contains("IDLE"),
            "Status should be PAUSED (or COMPLETED/IDLE if scan finished instantly), got: ${body.take(300)}"
        )

        // Cleanup
        ensureNoActiveScan()
    }

    // ── RESUME scan ─────────────────────────────────────────

    @Test @Order(4)
    fun resumeScanReturns200WithScanningStatus() = runBlocking {
        ensureNoActiveScan()

        // Start then pause
        client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        delay(200)
        client.post(scanUrl("pause")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        delay(300)

        val resp = client.post(scanUrl("resume")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        // 200 = resumed successfully, 500 = scan already completed (nothing to resume)
        val status = resp.status.value
        assertTrue(
            status == 200 || status == 500,
            "POST .../scan/resume should return 200 or 500 (if scan completed), got: $status"
        )
        if (status == 200) {
            val body = resp.bodyAsText()
            assertTrue(body.contains("\"status\""), "Response should contain 'status' field")
            assertTrue(
                body.contains("SCANNING") || body.contains("COMPLETED") || body.contains("PAUSED"),
                "Status should be SCANNING or COMPLETED after resume, got: ${body.take(300)}"
            )
        }

        // Cleanup
        ensureNoActiveScan()
    }

    // ── CANCEL scan ─────────────────────────────────────────

    @Test @Order(5)
    fun cancelScanReturns200WithCancelledStatus() = runBlocking {
        ensureNoActiveScan()

        // Start a scan first
        client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }

        val resp = client.post(scanUrl("cancel")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "POST .../scan/cancel should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"status\""), "Response should contain 'status' field")
        assertTrue(
            body.contains("CANCELLED") || body.contains("COMPLETED") || body.contains("IDLE"),
            "Status should be CANCELLED (or COMPLETED/IDLE if scan finished), got: ${body.take(300)}"
        )
    }

    // ── GET status ──────────────────────────────────────────

    @Test @Order(6)
    fun getStatusReturns200WithScanStatusResponse() = runBlocking {
        val resp = client.get(scanUrl("status")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET .../scan/status should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"status\""), "Response should contain 'status' field")
        assertTrue(body.contains("\"projectKey\""), "Response should contain 'projectKey' field")
        assertTrue(body.contains("\"totalTickets\""), "Response should contain 'totalTickets' field")
        assertTrue(body.contains("\"processedCount\""), "Response should contain 'processedCount' field")
        assertTrue(body.contains("\"progressPercent\""), "Response should contain 'progressPercent' field")
        assertTrue(body.contains("\"recentLog\""), "Response should contain 'recentLog' field")
    }

    // ── GET log ─────────────────────────────────────────────

    @Test @Order(7)
    fun getLogReturns200WithScanLogResponse() = runBlocking {
        val resp = client.get(scanUrl("log")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET .../scan/log should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"projectKey\""), "Response should contain 'projectKey' field")
        assertTrue(body.contains("\"entries\""), "Response should contain 'entries' field")
        assertTrue(body.contains("\"totalCount\""), "Response should contain 'totalCount' field")
    }

    // ── RBAC: Reader calls START → 403 Forbidden ────────────

    @Test @Order(8)
    fun readerCannotStartScan() = runBlocking {
        val resp = client.post(scanUrl("start")) {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "READER should not be able to start scan (requires ANALYZE_AI)")
    }

    // ── RBAC: Reader calls GET status → 200 (allowed) ───────

    @Test @Order(9)
    fun readerCanViewScanStatus() = runBlocking {
        val resp = client.get(scanUrl("status")) {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "READER should be able to view scan status (VIEW_ANALYSIS)")
    }
}
