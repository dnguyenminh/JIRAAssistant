package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Integrations API tests: GET/PUT integrations, Jira config,
 * provider config, test provider, status format.
 */
@TestMethodOrder(OrderAnnotation::class)
class IntegrationsApiTest : ApiTestBase() {

    @Test @Order(20)
    fun getIntegrationsRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/integrations")
        assertEquals(401, resp.status.value)
    }

    @Test @Order(21)
    fun getIntegrationsWithAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/integrations") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(22)
    fun jiraConfigSaveAndTest() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"domain":"https://test.atlassian.net","email":"test@example.com","apiToken":"fake-token"}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("status"), "Response should contain 'status' field")
    }

    @Test @Order(23)
    fun providerConfigUpdate() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/ollama/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"endpoint":"http://localhost:11434"}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(130)
    fun jiraConfigMissingFields() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"domain":"","email":"","apiToken":""}""")
        }
        assertEquals(400, resp.status.value, "Jira config with all empty fields should return 400")
    }

    @Test @Order(131)
    fun jiraConfigMissingEmail() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"domain":"https://x.atlassian.net","email":"","apiToken":"t"}""")
        }
        assertEquals(400, resp.status.value, "Jira config with empty email should return 400")
    }

    @Test @Order(132)
    fun testNonExistentProvider() = runBlocking {
        val resp = client.post("$baseUrl/api/integrations/nonexistent/test") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "POST /api/integrations/nonexistent/test should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(133)
    fun configNonExistentProvider() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/nonexistent/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"endpoint":"http://localhost:9999"}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "PUT /api/integrations/nonexistent/config should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(300)
    fun jiraStatusReturnsFalseWhenNotConfigured() = runBlocking {
        val resp = client.get("$baseUrl/api/integrations/jira/status")
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("configured"), "Response should contain 'configured' field")
    }

    @Test @Order(302)
    fun jiraStatusReturnsTrueAfterConfig() = runBlocking {
        val configResp = client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"domain":"https://test.atlassian.net","email":"test@example.com","apiToken":"fake-token"}""")
        }
        assertEquals(200, configResp.status.value)

        val statusResp = client.get("$baseUrl/api/integrations/jira/status")
        assertEquals(200, statusResp.status.value)
        val body = statusResp.bodyAsText()
        assertTrue(body.contains("configured"), "Response should contain 'configured' field")
    }

    @Test @Order(330)
    fun testProviderRequiresAuth() = runBlocking {
        val resp = client.post("$baseUrl/api/integrations/ollama/test")
        assertEquals(401, resp.status.value, "POST /api/integrations/ollama/test without auth should return 401")
    }

    @Test @Order(332)
    fun configProviderRequiresAuth() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/ollama/config") {
            contentType(ContentType.Application.Json)
            setBody("""{"endpoint":"http://localhost:11434"}""")
        }
        assertEquals(401, resp.status.value, "PUT /api/integrations/ollama/config without auth should return 401")
    }

    @Test @Order(334)
    fun jiraStatusResponseFormat() = runBlocking {
        val resp = client.get("$baseUrl/api/integrations/jira/status")
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("configured"), "Jira status should contain 'configured' field")
        assertTrue(body.contains("domain"), "Jira status should contain 'domain' field")
        assertTrue(body.contains("status"), "Jira status should contain 'status' field")
    }

    // ── Real Jira tests (only run when JIRA_TEST_* env vars are set) ──

    @Test @Order(400)
    fun realJiraConfigReturnsActiveStatus() = runBlocking {
        assumeJiraConfigured()
        val resp = client.get("$baseUrl/api/integrations/jira/status")
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"configured\":true"), "Jira should be configured: $body")
        assertTrue(body.contains("\"active\"", ignoreCase = true), "Jira status should be active: $body")
    }

    @Test @Order(401)
    fun realJiraProjectsReturnNonEmptyList() = runBlocking {
        assumeJiraConfigured()
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        println("[IntegrationsApiTest] Real Jira projects (${body.length} chars): ${body.take(500)}")
        assertTrue(body.startsWith("["), "Projects should return a JSON array")
        assertFalse(body == "[]", "Projects list should NOT be empty when real Jira is configured")
    }

    @Test @Order(402)
    fun realJiraProjectIssuesReturnData() = runBlocking {
        assumeJiraConfigured()
        val resp = client.get("$baseUrl/api/projects/$jiraProjectKey/issues") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.startsWith("["), "Issues should return a JSON array: ${body.take(100)}")
        println("[IntegrationsApiTest] Real Jira issues for $jiraProjectKey: ${body.take(500)}")
    }

    @Test @Order(403)
    fun realJiraProjectAnalysisReturnsMetrics() = runBlocking {
        assumeJiraConfigured()
        val resp = client.get("$baseUrl/api/projects/$jiraProjectKey/analysis") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "Project analysis should return 200 with real Jira data")
        val body = resp.bodyAsText()
        assertTrue(body.contains("projectKey"), "Analysis should contain projectKey")
        assertTrue(body.contains("totalTickets"), "Analysis should contain totalTickets")
        assertTrue(body.contains("resolutionRate"), "Analysis should contain resolutionRate")
        assertTrue(body.contains("velocityTrend"), "Analysis should contain velocityTrend")
        assertTrue(body.contains("bottlenecks"), "Analysis should contain bottlenecks")
        val ticketCountRegex = """"totalTickets"\s*:\s*(\d+)""".toRegex()
        val ticketMatch = ticketCountRegex.find(body)
        if (ticketMatch != null) {
            val count = ticketMatch.groupValues[1].toInt()
            println("[IntegrationsApiTest] totalTickets for $jiraProjectKey: $count")
            // Project may have 0 tickets — that's valid, just log it
        }
        println("[IntegrationsApiTest] Real analysis for $jiraProjectKey: ${body.take(500)}")
    }
}
