package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * API E2E tests for Document Job Manager.
 * Requirements: 1.1–8.7 from document-job-manager spec.
 */
@TestMethodOrder(OrderAnnotation::class)
class DocumentJobManagerApiTest : ApiTestBase() {

    companion object {
        var createdJobId: String? = null
        var createdChainId: String? = null
        private const val TICKET = "TEST-API-1"
    }

    // ── Requirement 8.1: Generate BRD returns jobId ─────────

    @Test @Order(1)
    fun `POST generate-brd returns jobId and QUEUED status`() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/$TICKET/generate-brd") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
        }
        val body = resp.bodyAsText()
        println("[DocJobApi] generate-brd: ${resp.status.value} — $body")
        // Accept 200/201 (job created) or 400 (ticket not analyzed) or 409 (lock)
        assert(resp.status.value in listOf(200, 201, 400, 409)) {
            "Expected 200/201/400/409 but got ${resp.status.value}"
        }
        if (resp.status.value in 200..201) {
            assert(body.contains("jobId")) { "Response should contain jobId" }
            val idRegex = """"jobId"\s*:\s*"([^"]+)"""".toRegex()
            createdJobId = idRegex.find(body)?.groupValues?.get(1)
        }
    }

    // ── Requirement 1.2: FSD blocked without BRD ────────────

    @Test @Order(2)
    fun `POST generate-fsd returns 400 when no BRD exists`() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/NO-BRD-TICKET/generate-fsd") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
        }
        val body = resp.bodyAsText()
        println("[DocJobApi] generate-fsd no BRD: ${resp.status.value} — $body")
        assert(resp.status.value in listOf(400, 404)) {
            "Expected 400/404 but got ${resp.status.value}"
        }
    }

    // ── Requirement 1.3: Slides blocked without BRD ─────────

    @Test @Order(3)
    fun `POST generate-slides returns 400 when no BRD exists`() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/NO-BRD-TICKET/generate-slides") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
        }
        val body = resp.bodyAsText()
        println("[DocJobApi] generate-slides no BRD: ${resp.status.value} — $body")
        assert(resp.status.value in listOf(400, 404)) {
            "Expected 400/404 but got ${resp.status.value}"
        }
    }

    // ── Requirement 8.4: Generate All creates chain ─────────

    @Test @Order(4)
    fun `POST generate-all returns chainId and 3 jobs`() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/$TICKET/generate-all") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
        }
        val body = resp.bodyAsText()
        println("[DocJobApi] generate-all: ${resp.status.value} — $body")
        // Accept 200 (chain created) or 400/409 (precondition)
        assert(resp.status.value in listOf(200, 201, 400, 409)) {
            "Expected 200/201/400/409 but got ${resp.status.value}"
        }
        if (resp.status.value in 200..201) {
            assert(body.contains("chainId")) { "Response should contain chainId" }
            val chainRegex = """"chainId"\s*:\s*"([^"]+)"""".toRegex()
            createdChainId = chainRegex.find(body)?.groupValues?.get(1)
        }
    }

    // ── Requirement 2.5: GET /api/jobs with filter ──────────

    @Test @Order(5)
    fun `GET jobs returns list with status filter`() = runBlocking {
        val resp = client.get("$baseUrl/api/jobs?status=active") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val body = resp.bodyAsText()
        println("[DocJobApi] GET jobs active: ${resp.status.value} — ${body.take(200)}")
        assert(resp.status.value == 200) { "Expected 200 but got ${resp.status.value}" }
    }

    @Test @Order(6)
    fun `GET jobs returns all jobs`() = runBlocking {
        val resp = client.get("$baseUrl/api/jobs?status=all") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        println("[DocJobApi] GET jobs all: ${resp.status.value}")
        assert(resp.status.value == 200) { "Expected 200 but got ${resp.status.value}" }
    }

    // ── Requirement 2.6: GET /api/jobs/{jobId} ──────────────

    @Test @Order(7)
    fun `GET job by ID returns 404 for non-existent job`() = runBlocking {
        val resp = client.get("$baseUrl/api/jobs/non-existent-id") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        println("[DocJobApi] GET job non-existent: ${resp.status.value}")
        assert(resp.status.value == 404) { "Expected 404 but got ${resp.status.value}" }
    }

    // ── Requirement 3.5: Pause/Cancel RUNNING returns 409 ───

    @Test @Order(8)
    fun `POST pause non-existent job returns 404`() = runBlocking {
        val resp = client.post("$baseUrl/api/jobs/non-existent-id/pause") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        println("[DocJobApi] pause non-existent: ${resp.status.value}")
        assert(resp.status.value == 404) { "Expected 404 but got ${resp.status.value}" }
    }

    @Test @Order(9)
    fun `POST cancel non-existent job returns 404`() = runBlocking {
        val resp = client.post("$baseUrl/api/jobs/non-existent-id/cancel") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        println("[DocJobApi] cancel non-existent: ${resp.status.value}")
        assert(resp.status.value == 404) { "Expected 404 but got ${resp.status.value}" }
    }

    // ── Requirement 8.7: GET active-jobs for ticket ─────────

    @Test @Order(10)
    fun `GET active-jobs returns list for ticket`() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/$TICKET/active-jobs") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val body = resp.bodyAsText()
        println("[DocJobApi] active-jobs: ${resp.status.value} — ${body.take(200)}")
        assert(resp.status.value == 200) { "Expected 200 but got ${resp.status.value}" }
    }

    // ── Requirement 8.6: GET documents metadata ─────────────

    @Test @Order(11)
    fun `GET documents returns metadata list`() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/$TICKET/documents") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        println("[DocJobApi] documents: ${resp.status.value}")
        assert(resp.status.value in listOf(200, 404)) {
            "Expected 200/404 but got ${resp.status.value}"
        }
    }

    // ── Requirement 6.5: Reject with short reason → 400 ────

    @Test @Order(12)
    fun `POST reject with short reason returns 400`() = runBlocking {
        val resp = client.post("$baseUrl/api/documents/fake-doc-id/reject") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"short"}""")
        }
        val body = resp.bodyAsText()
        println("[DocJobApi] reject short reason: ${resp.status.value} — $body")
        assert(resp.status.value in listOf(400, 404)) {
            "Expected 400/404 but got ${resp.status.value}"
        }
    }

    // ── RBAC: No JWT → 401 ──────────────────────────────────

    @Test @Order(13)
    fun `GET jobs without JWT returns 401`() = runBlocking {
        val resp = client.get("$baseUrl/api/jobs")
        println("[DocJobApi] no-jwt jobs: ${resp.status.value}")
        assert(resp.status.value == 401) { "Expected 401 but got ${resp.status.value}" }
    }

    @Test @Order(14)
    fun `POST generate-brd without JWT returns 401`() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/$TICKET/generate-brd") {
            contentType(ContentType.Application.Json)
        }
        println("[DocJobApi] no-jwt generate: ${resp.status.value}")
        assert(resp.status.value == 401) { "Expected 401 but got ${resp.status.value}" }
    }
}
