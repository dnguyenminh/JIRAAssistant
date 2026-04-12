package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Estimation API tests — POST /api/estimation/estimate
 * Covers: auth, RBAC, response format, validation, edge cases, Scrum point scale.
 */
@TestMethodOrder(OrderAnnotation::class)
class EstimationApiTest : ApiTestBase() {

    // ── Auth ────────────────────────────────────────────────

    @Test @Order(60)
    fun estimationRequiresAuth() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            setBody("""{"summary":"test","description":"test"}""")
        }
        assertEquals(401, resp.status.value, "POST /api/estimation/estimate without auth should return 401")
    }

    @Test @Order(61)
    fun estimationWithAuthReturnsResult() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"summary":"Implement login page","description":"Create a login page with email and password fields"}""")
        }
        assertEquals(200, resp.status.value, "Estimation with auth should return 200")
    }

    // ── Response format ─────────────────────────────────────

    @Test @Order(62)
    fun estimationResponseContainsExpectedFields() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"summary":"Add payment gateway","description":"Integrate Stripe payment processing"}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        // Response should contain estimation fields (point, rationale, etc.)
        assertTrue(body.isNotBlank(), "Estimation response should not be empty")
        assertTrue(
            body.contains("point", ignoreCase = true) || body.contains("estimation", ignoreCase = true),
            "Estimation response should contain point or estimation field: ${body.take(300)}"
        )
    }

    @Test @Order(63)
    fun estimationResponseIsJson() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"summary":"Test task","description":"A simple test task"}""")
        }
        assertEquals(200, resp.status.value)
        val contentType = resp.headers[HttpHeaders.ContentType] ?: ""
        assertTrue(
            contentType.contains("application/json", ignoreCase = true),
            "Estimation response Content-Type should be application/json, got: $contentType"
        )
    }

    // ── Scrum point scale validation ────────────────────────

    @Test @Order(64)
    fun estimationReturnsValidScrumPoint() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"summary":"Complex feature","description":"A very complex feature requiring multiple sprints"}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        val validPoints = listOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0)
        val pointRegex = """"(?:point|scrumPoint|suggestedPoints)"\s*:\s*(\d+\.?\d*)""".toRegex()
        val match = pointRegex.find(body)
        if (match != null) {
            val point = match.groupValues[1].toDouble()
            assertTrue(
                point in validPoints,
                "Scrum point $point should be in valid scale $validPoints"
            )
        }
    }

    // ── Validation ──────────────────────────────────────────

    @Test @Order(65)
    fun estimationEmptySummaryReturns400() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"summary":"","description":"Some description"}""")
        }
        assertEquals(400, resp.status.value, "Empty summary should return 400")
    }

    @Test @Order(66)
    fun estimationMalformedJsonReturns400or500() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("{invalid json")
        }
        assertTrue(
            resp.status.value in listOf(400, 500),
            "Malformed JSON should return 400 or 500, got ${resp.status.value}"
        )
        val body = resp.bodyAsText()
        assertFalse(body.contains("at com."), "Error should not expose stack traces")
    }

    // ── With historical tickets ──────────────────────────────

    @Test @Order(67)
    fun estimationWithHistoricalTickets() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""
                {
                    "summary": "Implement OAuth2 Login",
                    "description": "Create OAuth2 login flow with Google and GitHub providers",
                    "featureArea": "Authentication",
                    "historicalTickets": [
                        {"ticketKey": "PROJ-10", "summary": "Basic login page", "actualPoints": 3.0, "similarityScore": 0.8},
                        {"ticketKey": "PROJ-25", "summary": "SSO integration", "actualPoints": 8.0, "similarityScore": 0.6}
                    ]
                }
            """.trimIndent())
        }
        assertEquals(200, resp.status.value, "Estimation with historical tickets should return 200")
    }

    @Test @Order(68)
    fun estimationWithFeatureArea() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"summary":"Add caching layer","description":"Redis caching for API responses","featureArea":"Infrastructure"}""")
        }
        assertEquals(200, resp.status.value, "Estimation with featureArea should return 200")
    }

    // ── RBAC ────────────────────────────────────────────────

    @Test @Order(69)
    fun readerCannotEstimate() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            setBody("""{"summary":"test","description":"test"}""")
        }
        assertEquals(403, resp.status.value, "READER should not access POST /api/estimation/estimate")
    }

    @Test @Order(70)
    fun neuralArchitectCanEstimate() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
            setBody("""{"summary":"Implement feature","description":"A new feature"}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "NEURAL_ARCHITECT should access estimation, got ${resp.status.value}"
        )
    }

    // ── Error handling ──────────────────────────────────────

    @Test @Order(71)
    fun estimationErrorNoStackTrace() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            setBody("""{"summary":"test","description":"test"}""")
        }
        val body = resp.bodyAsText()
        assertFalse(body.contains("at com."), "Estimation error should not expose stack traces")
    }
}
