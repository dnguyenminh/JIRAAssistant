package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * E2E API tests for Attachment Processing Pipeline.
 * Tests the GET /api/projects/{key}/tickets/{ticketKey}/attachments endpoint
 * and verifies attachment status responses.
 * Requirements: 22.20
 */
@TestMethodOrder(OrderAnnotation::class)
class AttachmentApiTest : ApiTestBase() {

    private val projectKey get() = jiraProjectKey

    private fun attachmentUrl(ticketKey: String) =
        "$baseUrl/api/projects/$projectKey/tickets/$ticketKey/attachments"

    // ── GET attachment status — returns 200 with list ──────

    @Test @Order(1)
    fun getAttachmentStatusReturns200() = runBlocking {
        val resp = client.get(attachmentUrl("$projectKey-1")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value,
            "GET .../attachments should return 200")
        val body = resp.bodyAsText()
        // Response is a JSON array (possibly empty)
        assertTrue(body.startsWith("["),
            "Response should be a JSON array, got: ${body.take(100)}")
    }

    // ── GET attachment status for non-existent ticket — returns 200 empty ──

    @Test @Order(2)
    fun getAttachmentStatusForNonExistentTicketReturnsEmptyList() = runBlocking {
        val resp = client.get(attachmentUrl("NONEXIST-99999")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value,
            "Non-existent ticket should still return 200")
        val body = resp.bodyAsText()
        assertEquals("[]", body.trim(),
            "Non-existent ticket should return empty array")
    }

    // ── RBAC: Reader can view attachment status (VIEW_ANALYSIS) ──

    @Test @Order(3)
    fun readerCanViewAttachmentStatus() = runBlocking {
        val resp = client.get(attachmentUrl("$projectKey-1")) {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value,
            "READER should be able to view attachment status (VIEW_ANALYSIS)")
    }

    // ── Unauthenticated request → 401 ──

    @Test @Order(4)
    fun unauthenticatedRequestReturns401() = runBlocking {
        val resp = client.get(attachmentUrl("$projectKey-1"))
        assertEquals(401, resp.status.value,
            "Unauthenticated request should return 401")
    }

    // ── After scan, attachment status contains expected fields ──

    @Test @Order(5)
    fun attachmentStatusContainsExpectedFieldsWhenDataExists() = runBlocking {
        assumeJiraConfigured()

        // Start a scan to populate data
        val scanResp = client.post("$baseUrl/api/projects/$projectKey/scan/start") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        if (scanResp.status.value == 200) {
            // Wait briefly for scan to process at least one ticket
            kotlinx.coroutines.delay(5000)

            // Cancel scan to stop it
            client.post("$baseUrl/api/projects/$projectKey/scan/cancel") {
                header(HttpHeaders.Authorization, "Bearer $adminJwt")
            }
        }

        // Now check attachment status for first ticket
        val resp = client.get(attachmentUrl("$projectKey-1")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()

        // If there are attachments, verify field structure
        if (body != "[]") {
            assertTrue(body.contains("\"attachmentId\""),
                "Response should contain 'attachmentId' field")
            assertTrue(body.contains("\"filename\""),
                "Response should contain 'filename' field")
            assertTrue(body.contains("\"status\""),
                "Response should contain 'status' field")
            assertTrue(body.contains("\"chunkCount\""),
                "Response should contain 'chunkCount' field")
            // Status should be one of the valid values
            assertTrue(
                body.contains("CONVERTED") || body.contains("PENDING") || body.contains("FAILED"),
                "Status should be CONVERTED, PENDING, or FAILED"
            )
        }
    }
}
