package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Health & initialization endpoint tests.
 */
@TestMethodOrder(OrderAnnotation::class)
class HealthApiTest : ApiTestBase() {

    @Test @Order(1)
    fun healthEndpointReturns200() = runBlocking {
        val resp = client.get("$baseUrl/health")
        assertEquals(200, resp.status.value, "GET /health should return 200")
    }

    @Test @Order(2)
    fun settingsStatusIsPublic() = runBlocking {
        val resp = client.get("$baseUrl/api/settings/status")
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("configured"), "Response should contain 'configured' field")
    }

    @Test @Order(3)
    fun jiraStatusIsPublic() = runBlocking {
        val resp = client.get("$baseUrl/api/integrations/jira/status")
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("configured"), "Response should contain 'configured' field")
    }
}
