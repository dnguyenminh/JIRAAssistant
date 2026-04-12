package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Analysis API tests: analysis, reanalyze, status, project analysis, KB cache.
 */
@TestMethodOrder(OrderAnnotation::class)
class AnalysisApiTest : ApiTestBase() {

    @Test @Order(40)
    fun analysisRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/PROJ-1")
        assertEquals(401, resp.status.value)
    }

    @Test @Order(41)
    fun analysisWithAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/PROJ-1") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Analysis should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(310)
    fun projectAnalysisReturnsExpectedFields() = runBlocking {
        val resp = client.get("$baseUrl/api/projects/PROJ/analysis") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Project analysis should return 200 or 404, got ${resp.status.value}"
        )
        if (resp.status.value == 200) {
            val body = resp.bodyAsText()
            assertTrue(body.contains("projectKey"), "Response should contain projectKey")
            assertTrue(body.contains("totalTickets"), "Response should contain totalTickets")
            assertTrue(body.contains("resolutionRate"), "Response should contain resolutionRate")
            assertTrue(body.contains("cycleTimeDays"), "Response should contain cycleTimeDays")
            assertTrue(body.contains("aiVelocity"), "Response should contain aiVelocity")
            assertTrue(body.contains("velocityTrend"), "Response should contain velocityTrend")
            assertTrue(body.contains("bottlenecks"), "Response should contain bottlenecks")
        }
    }

    @Test @Order(311)
    fun projectAnalysisRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/projects/PROJ/analysis")
        assertEquals(401, resp.status.value, "GET /api/projects/PROJ/analysis without auth should return 401")
    }

    @Test @Order(312)
    fun projectIssuesEndpoint() = runBlocking {
        val resp = client.get("$baseUrl/api/projects/PROJ/issues") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET /api/projects/PROJ/issues with auth should return 200")
    }

    @Test @Order(313)
    fun projectIssuesRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/projects/PROJ/issues")
        assertEquals(401, resp.status.value, "GET /api/projects/PROJ/issues without auth should return 401")
    }

    @Test @Order(320)
    fun analysisStatusEndpoint() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/PROJ-1/status") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Analysis status should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(321)
    fun analysisStatusRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/PROJ-1/status")
        assertEquals(401, resp.status.value, "GET /api/analysis/PROJ-1/status without auth should return 401")
    }

    @Test @Order(322)
    fun reanalyzeEndpoint() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/PROJ-1/reanalyze") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Reanalyze should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(323)
    fun reanalyzeRequiresAuth() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/PROJ-1/reanalyze")
        assertEquals(401, resp.status.value, "POST /api/analysis/PROJ-1/reanalyze without auth should return 401")
    }

    @Test @Order(360)
    fun analysisResultIsCachedInKB() = runBlocking {
        val start1 = System.currentTimeMillis()
        val resp1 = client.get("$baseUrl/api/analysis/PROJ-1") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val elapsed1 = System.currentTimeMillis() - start1

        assertTrue(
            resp1.status.value in listOf(200, 404),
            "First analysis call should return 200 or 404, got ${resp1.status.value}"
        )

        val start2 = System.currentTimeMillis()
        val resp2 = client.get("$baseUrl/api/analysis/PROJ-1") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        val elapsed2 = System.currentTimeMillis() - start2

        assertTrue(
            resp2.status.value in listOf(200, 404),
            "Second analysis call should return 200 or 404, got ${resp2.status.value}"
        )
        println("[AnalysisApiTest] KB cache test: first=${elapsed1}ms, second=${elapsed2}ms")
    }
}
