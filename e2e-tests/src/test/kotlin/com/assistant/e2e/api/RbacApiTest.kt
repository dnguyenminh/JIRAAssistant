package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * RBAC tests across all domains: reader/neural-architect permission checks.
 */
@TestMethodOrder(OrderAnnotation::class)
class RbacApiTest : ApiTestBase() {

    // ── Reader restrictions ─────────────────────────────────

    @Test @Order(110)
    fun readerCannotAccessSettings() = runBlocking {
        val resp = client.get("$baseUrl/api/settings") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "READER should not access GET /api/settings")
    }

    @Test @Order(111)
    fun readerCannotUpdateSettings() = runBlocking {
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            setBody("""{"jiraHost":"https://evil.example.com"}""")
        }
        assertEquals(403, resp.status.value, "READER should not access PUT /api/settings")
    }

    @Test @Order(112)
    fun readerCannotConfigureIntegrations() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            setBody("""{"domain":"https://x.atlassian.net","email":"a@b.com","apiToken":"t"}""")
        }
        assertEquals(403, resp.status.value, "READER should not access PUT /api/integrations/jira/config")
    }

    @Test @Order(113)
    fun readerCannotManageUsers() = runBlocking {
        val resp = client.get("$baseUrl/api/users") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "READER should not access GET /api/users")
    }

    @Test @Order(114)
    fun readerCanViewIntegrations() = runBlocking {
        val resp = client.get("$baseUrl/api/integrations") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "READER should be able to view GET /api/integrations (VIEW_DASHBOARD)")
    }

    @Test @Order(115)
    fun readerCanViewProjects() = runBlocking {
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(200, resp.status.value, "READER should be able to view GET /api/projects (auth-only, no RBAC)")
    }

    // ── Analysis RBAC ───────────────────────────────────────

    @Test @Order(324)
    fun readerCannotAnalyze() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/PROJ-1") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "READER should not access GET /api/analysis/PROJ-1")
    }

    @Test @Order(325)
    fun readerCannotReanalyze() = runBlocking {
        val resp = client.post("$baseUrl/api/analysis/PROJ-1/reanalyze") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "READER should not access POST /api/analysis/PROJ-1/reanalyze")
    }

    @Test @Order(326)
    fun neuralArchitectCanAnalyze() = runBlocking {
        val resp = client.get("$baseUrl/api/analysis/PROJ-1") {
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "NEURAL_ARCHITECT should access analysis, got ${resp.status.value}"
        )
    }

    // ── Integrations RBAC ───────────────────────────────────

    @Test @Order(331)
    fun testProviderRequiresAdminRole() = runBlocking {
        val resp = client.post("$baseUrl/api/integrations/ollama/test") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "READER should not access POST /api/integrations/ollama/test")
    }

    @Test @Order(333)
    fun jiraConfigRequiresAdminRole() = runBlocking {
        val resp = client.put("$baseUrl/api/integrations/jira/config") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
            setBody("""{"domain":"https://x.atlassian.net","email":"a@b.com","apiToken":"t"}""")
        }
        assertEquals(403, resp.status.value, "NEURAL_ARCHITECT should not access PUT /api/integrations/jira/config")
    }

    // ── User Management RBAC ────────────────────────────────

    @Test @Order(342)
    fun changeUserRoleRequiresAdmin() = runBlocking {
        val resp = client.put("$baseUrl/api/users/test-user-id/role") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            setBody("""{"role":"READER"}""")
        }
        assertEquals(403, resp.status.value, "READER should not access PUT /api/users/{userId}/role")
    }

    @Test @Order(345)
    fun neuralArchitectCannotManageUsers() = runBlocking {
        val resp = client.get("$baseUrl/api/users") {
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
        }
        assertEquals(403, resp.status.value, "NEURAL_ARCHITECT should not access GET /api/users")
    }

    // ── Estimation RBAC ─────────────────────────────────────

    @Test @Order(371)
    fun estimationReaderDenied() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            setBody("""{"summary":"test","description":"test"}""")
        }
        assertEquals(403, resp.status.value, "READER should not access POST /api/estimation/estimate")
    }

    @Test @Order(372)
    fun estimationNeuralArchitectAllowed() = runBlocking {
        val resp = client.post("$baseUrl/api/estimation/estimate") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
            setBody("""{"summary":"Implement login page","description":"Create a login page with email and password fields"}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "NEURAL_ARCHITECT should access estimation, got ${resp.status.value}"
        )
    }

    // ── Graph RBAC ──────────────────────────────────────────

    @Test @Order(390)
    fun neuralArchitectCanViewGraph() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "NEURAL_ARCHITECT should access graph (VIEW_GRAPH), got ${resp.status.value}"
        )
    }

    @Test @Order(391)
    fun readerCanViewGraph() = runBlocking {
        val resp = client.get("$baseUrl/api/graph/PROJ") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "READER should access graph (VIEW_GRAPH permission), got ${resp.status.value}"
        )
    }

    @Test @Order(392)
    fun readerCannotTestProvider() = runBlocking {
        val resp = client.post("$baseUrl/api/integrations/ollama/test") {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
        }
        assertEquals(403, resp.status.value, "READER should not access POST /api/integrations/ollama/test")
    }

    @Test @Order(393)
    fun neuralArchitectCannotTestProvider() = runBlocking {
        val resp = client.post("$baseUrl/api/integrations/ollama/test") {
            header(HttpHeaders.Authorization, "Bearer ${neuralArchitectJwt()}")
        }
        assertEquals(403, resp.status.value, "NEURAL_ARCHITECT should not access POST /api/integrations/ollama/test (CONFIG_INTEGRATIONS)")
    }
}
