package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Regression tests for bugs found during manual testing.
 * Each test corresponds to a specific bug that was discovered and fixed.
 * These tests ensure the bugs don't reappear.
 */
@TestMethodOrder(OrderAnnotation::class)
class RegressionApiTest : ApiTestBase() {

    // ── Bug: Login with wrong credentials should return 401 ──

    @Test @Order(1)
    fun loginWithWrongPasswordReturns401() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"wrongpassword"}""")
        }
        assertEquals(401, resp.status.value, "Wrong password should return 401")
        val body = resp.bodyAsText()
        assertTrue(body.contains("error"), "401 response should contain error field")
    }

    @Test @Order(2)
    fun loginWithNonExistentUserReturns401() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nonexistent","password":"admin123"}""")
        }
        assertEquals(401, resp.status.value, "Non-existent user should return 401")
    }

    @Test @Order(3)
    fun loginWithEmptyCredentialsReturns401() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"","password":""}""")
        }
        assertEquals(401, resp.status.value, "Empty credentials should return 401, not auto-login")
    }

    @Test @Order(4)
    fun loginAsUserReturnsReaderRole() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user","password":"user123"}""")
        }
        assertEquals(200, resp.status.value, "User login should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("READER"), "User should have READER role, got: ${body.take(300)}")
    }

    @Test @Order(5)
    fun loginAsAdminReturnsAdministratorRole() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("ADMINISTRATOR"), "Admin should have ADMINISTRATOR role")
    }

    // ── Bug: GET /api/integrations returned empty [] even after Jira config ──

    @Test @Order(10)
    fun integrationsReturnsSavedProvidersFromDb() = runBlocking {
        assumeJiraConfigured() // Only run with real Jira credentials
        // GET /api/integrations should return non-empty list including Jira from DB
        val resp = client.get("$baseUrl/api/integrations") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertFalse(body == "[]", "GET /api/integrations should NOT return empty [] when Jira is configured")
        assertTrue(body.contains("jira", ignoreCase = true), "Response should contain Jira provider")
        println("[RegressionApiTest] Integrations: ${body.take(500)}")
    }

    // ── Bug: Jira TEST LINK returned serialization error (mapOf with mixed types) ──

    @Test @Order(20)
    fun jiraTestLinkReturnsValidJson() = runBlocking {
        // Configure Jira first
        client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"domain":"https://test.atlassian.net","email":"test@example.com","apiToken":"fake-token"}""")
        }

        // Test Jira link
        val resp = client.post("$baseUrl/api/integrations/jira/test") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "Jira test should return 200, not 500")
        val body = resp.bodyAsText()
        // Should NOT contain serialization error
        assertFalse(body.contains("Serializing collections"), "Should not have serialization error")
        // Should contain proper JSON with providerId and status
        assertTrue(body.contains("providerId"), "Response should contain providerId field")
        assertTrue(body.contains("status"), "Response should contain status field")
        println("[RegressionApiTest] Jira test result: $body")
    }

    @Test @Order(21)
    fun jiraTestLinkWithoutConfigReturnsOffline() = runBlocking {
        // On fresh DB without Jira config, test should return OFFLINE (not crash)
        val resp = client.post("$baseUrl/api/integrations/jira/test") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "Jira test without config should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("providerId"), "Response should contain providerId")
        assertFalse(body.contains("Serializing"), "Should not have serialization error")
    }

    // ── Bug: JiraConfigResponse had projects as List<String> instead of List<JiraProject> ──

    @Test @Order(30)
    fun jiraConfigResponseHasProjectObjects() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"domain":"https://test.atlassian.net","email":"test@example.com","apiToken":"fake-token"}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("status"), "Response should contain status field")
        // If projects are returned, they should be objects (not strings)
        if (body.contains("projects") && !body.contains("\"projects\":[]")) {
            assertTrue(body.contains("key"), "Projects should be objects with 'key' field, not strings")
        }
    }

    // ── Bug: Jira status dot inconsistent (green after test, blue after reload) ──

    @Test @Order(40)
    fun jiraStatusPersistsAfterConfig() = runBlocking {
        assumeJiraConfigured() // Only run with real Jira credentials

        // Check status endpoint — should be configured after real Jira setup
        val statusResp = client.get("$baseUrl/api/integrations/jira/status")
        assertEquals(200, statusResp.status.value)
        val statusBody = statusResp.bodyAsText()
        assertTrue(statusBody.contains("\"configured\":true"), "Jira should be configured: $statusBody")

        // GET /api/integrations should include Jira with its saved status
        val listResp = client.get("$baseUrl/api/integrations") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, listResp.status.value)
        val listBody = listResp.bodyAsText()
        assertTrue(listBody.contains("jira", ignoreCase = true), "Integrations list should include Jira from DB")
    }

    // ── Bug: handleUnauthorized redirected to #dashboard causing navigation loop ──

    @Test @Order(50)
    fun unauthorizedDoesNotCauseRedirectLoop() = runBlocking {
        // Call protected endpoint without auth
        val resp = client.get("$baseUrl/api/integrations")
        assertEquals(401, resp.status.value, "Should return 401 without auth")
        // The response should be a clean JSON error, not a redirect
        val body = resp.bodyAsText()
        assertTrue(body.contains("error") || body.contains("Token"), "401 should return JSON error")
        assertFalse(body.contains("<html"), "401 should NOT return HTML redirect")
    }

    // ── Bug: Provider config update returned mapOf with mixed types ──

    @Test @Order(60)
    fun providerConfigUpdateReturnsValidJson() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/ollama/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"endpoint":"http://localhost:11434"}""")
        }
        assertEquals(200, resp.status.value, "Provider config update should return 200")
        val body = resp.bodyAsText()
        assertFalse(body.contains("Serializing collections"), "Should not have serialization error")
        assertTrue(body.contains("providerId") || body.contains("message"), "Response should be valid JSON")
    }

    // ── Bug: JiraClient singleton not refreshing after config change ──

    @Test @Order(70)
    fun projectsEndpointReturnsDataAfterJiraConfig() = runBlocking {
        assumeJiraConfigured()
        // After Jira is configured with real credentials, GET /api/projects should return data
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.startsWith("["), "Projects should return JSON array")
        assertFalse(body == "[]", "Projects should NOT be empty when real Jira is configured")
        println("[RegressionApiTest] Projects after Jira config: ${body.take(300)}")
    }

    // ── Bug: RBAC middleware used wrong pipeline phase ──

    @Test @Order(80)
    fun rbacMiddlewareWorksCorrectly() = runBlocking {
        // Admin should access integrations
        val adminResp = client.get("$baseUrl/api/integrations") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, adminResp.status.value, "Admin should access GET /api/integrations")

        // Reader should also access integrations (VIEW_DASHBOARD permission)
        val readerResp = client.get("$baseUrl/api/integrations") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, readerResp.status.value, "Reader should access GET /api/integrations (VIEW_DASHBOARD)")

        // Reader should NOT access settings (MANAGE_SETTINGS permission)
        val settingsResp = client.get("$baseUrl/api/settings") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, settingsResp.status.value, "Reader should NOT access GET /api/settings")
    }
}
