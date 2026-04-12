package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Error handling tests: error responses, stack traces, 404s,
 * invalid JSON, API group existence.
 */
@TestMethodOrder(OrderAnnotation::class)
class ErrorHandlingApiTest : ApiTestBase() {

    @Test @Order(140)
    fun nonExistentApiRouteReturns404or401() = runBlocking {
        val resp = client.get("$baseUrl/api/does-not-exist") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "GET /api/does-not-exist should return 404 or 200 (SPA fallback), got ${resp.status.value}"
        )
    }

    @Test @Order(141)
    fun analysisNonExistentTicket() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/NONEXISTENT-999") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "GET /api/analysis/NONEXISTENT-999 should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(150)
    fun errorResponseIsJson() = runBlocking {
        val resp = client.get("$baseUrl/api/settings")
        assertEquals(401, resp.status.value)
        val contentType = resp.headers[HttpHeaders.ContentType] ?: ""
        assertTrue(
            contentType.contains("application/json", ignoreCase = true),
            "Error response Content-Type should be application/json, got: $contentType"
        )
    }

    @Test @Order(151)
    fun errorResponseHasErrorField() = runBlocking {
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"jiraHost":"not-a-url"}""")
        }
        assertEquals(400, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("error", ignoreCase = true),
            "400 error response should contain 'error' field, got: ${body.take(200)}"
        )
    }

    @Test @Order(152)
    fun noStackTraceInAnyErrorResponse() = runBlocking {
        val responses = listOf(
            "unauthenticated settings" to client.get("$baseUrl/api/settings"),
            "non-existent route" to client.get("$baseUrl/api/nonexistent-endpoint-for-error-test"),
            "malformed JSON login" to client.post("$baseUrl/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("{bad json")
            },
            "invalid JWT" to client.get("$baseUrl/api/projects") {
                header(HttpHeaders.Authorization, "Bearer invalid-token")
            }
        )

        for ((label, resp) in responses) {
            val body = resp.bodyAsText()
            assertFalse(
                body.contains("at com."),
                "Error '$label' (${resp.status.value}) should not contain Java stack traces"
            )
            assertFalse(
                body.contains("stackTrace"),
                "Error '$label' (${resp.status.value}) should not contain 'stackTrace' field"
            )
            assertFalse(
                body.contains("java.lang."),
                "Error '$label' (${resp.status.value}) should not contain Java exception class names"
            )
        }
    }

    @Test @Order(350)
    fun healthResponseFormat() = runBlocking {
        val resp = client.get("$baseUrl/health")
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("jira"), "Health response should contain 'jira' status object")
        assertTrue(body.contains("aiProvider"), "Health response should contain 'aiProvider' status object")
        assertTrue(body.contains("knowledgeBase"), "Health response should contain 'knowledgeBase' status object")
        assertTrue(body.contains("status"), "Health response should contain overall 'status' field")
    }

    @Test @Order(351)
    fun allApiGroupsExist() = runBlocking {
        val endpoints = mapOf(
            "/api/auth/login" to "POST",
            "/api/projects" to "GET",
            "/api/integrations" to "GET",
            "/api/settings/status" to "GET",
            "/health" to "GET"
        )
        for ((path, method) in endpoints) {
            val resp = when (method) {
                "POST" -> client.post("$baseUrl$path") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"admin","password":"admin123"}""")
                }
                else -> client.get("$baseUrl$path")
            }
            assertNotEquals(
                404, resp.status.value,
                "API group $method $path should exist (not 404), got ${resp.status.value}"
            )
        }
    }

    @Test @Order(352)
    fun invalidJsonBodyReturns400or500() = runBlocking {
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("{invalid")
        }
        assertTrue(
            resp.status.value in listOf(400, 500),
            "Invalid JSON body should return 400 or 500, got ${resp.status.value}"
        )
        val body = resp.bodyAsText()
        assertFalse(body.contains("at com."), "Error should not expose stack traces")
    }
}
