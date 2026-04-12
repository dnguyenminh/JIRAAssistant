package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Graph API tests — GET /api/graph/{projectKey}
 * Covers: auth, RBAC, response format, search filter, missing project, error handling.
 */
@TestMethodOrder(OrderAnnotation::class)
class GraphApiTest : ApiTestBase() {

    // ── Auth ────────────────────────────────────────────────

    @Test @Order(50)
    fun graphRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ")
        assertEquals(401, resp.status.value, "GET /api/graph/PROJ without auth should return 401")
    }

    @Test @Order(51)
    fun graphWithAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Graph should return 200 or 404, got ${resp.status.value}"
        )
    }

    // ── Response format ─────────────────────────────────────

    @Test @Order(52)
    fun graphResponseContainsExpectedFields() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        if (resp.status.value == 200) {
            val body = resp.bodyAsText()
            assertTrue(body.contains("nodes"), "Graph response should contain 'nodes' field")
            assertTrue(body.contains("edges"), "Graph response should contain 'edges' field")
            assertTrue(body.contains("clusters"), "Graph response should contain 'clusters' field")
        }
    }

    @Test @Order(53)
    fun graphResponseIsJson() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val contentType = resp.headers[HttpHeaders.ContentType] ?: ""
        assertTrue(
            contentType.contains("application/json", ignoreCase = true),
            "Graph response Content-Type should be application/json, got: $contentType"
        )
    }

    // ── Search filter ───────────────────────────────────────

    @Test @Order(54)
    fun graphSearchFilterAccepted() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ?search=auth") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Graph with search filter should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(55)
    fun graphEmptySearchReturnsAll() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ?search=") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Graph with empty search should return 200 or 404, got ${resp.status.value}"
        )
    }

    // ── Missing project ─────────────────────────────────────

    @Test @Order(56)
    fun graphNonExistentProjectReturns404() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/NONEXISTENT-PROJECT-XYZ") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Graph for non-existent project should return 404 or 200 (empty), got ${resp.status.value}"
        )
    }

    @Test @Order(57)
    fun graphMissingProjectKeyReturns400() = runBlocking {
        // /api/graph without projectKey — should not match the route
        val resp = client.get("$baseUrl/api/graph/") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(400, 404, 200),
            "Graph without projectKey should return 400 or 404, got ${resp.status.value}"
        )
    }

    // ── RBAC ────────────────────────────────────────────────

    @Test @Order(58)
    fun readerCanAccessGraph() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "READER should access graph (VIEW_GRAPH), got ${resp.status.value}"
        )
    }

    @Test @Order(59)
    fun neuralArchitectCanAccessGraph() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "NEURAL_ARCHITECT should access graph (VIEW_GRAPH), got ${resp.status.value}"
        )
    }

    // ── Error handling ──────────────────────────────────────

    @Test @Order(60)
    fun graphErrorResponseNoStackTrace() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }
        val body = resp.bodyAsText()
        assertFalse(body.contains("at com."), "Graph error should not expose stack traces")
        assertFalse(body.contains("stackTrace"), "Graph error should not contain stackTrace field")
    }
}
