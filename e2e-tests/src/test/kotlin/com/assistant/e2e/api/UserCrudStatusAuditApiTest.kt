package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * E2E-API tests for User CRUD — Part 2: Self-deletion, status, audit.
 *
 * Covers STC: E2E-API-04 (self-deletion), E2E-API-05 (status change),
 * E2E-API-06 (audit log verification).
 *
 * Requires server running at baseUrl (default: http://localhost:8080).
 */
@TestMethodOrder(OrderAnnotation::class)
class UserCrudStatusAuditApiTest : ApiTestBase() {

    companion object {
        var statusUserId: String = ""
        var auditUserId: String = ""
    }

    // STC: E2E-API-04 — Self-deletion prevention
    // Admin user_id from login is "admin" (AuthServiceImpl sets userId = username)
    // Note: Ktor status page handler overwrites 403 body with generic {"error":"Forbidden"}
    @Test @Order(30)
    fun `E2E-API-04 - self-deletion returns 403`() = runBlocking {
        // Extract the actual user_id from the admin JWT
        val jwtParts = adminJwt.split(".")
        val payloadJson = String(java.util.Base64.getUrlDecoder().decode(jwtParts[1]))
        val userIdRegex = """"user_id"\s*:\s*"([^"]+)"""".toRegex()
        val adminUserId = userIdRegex.find(payloadJson)?.groupValues?.get(1) ?: "admin"

        val resp = client.delete("$baseUrl/api/users/$adminUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(403, resp.status.value, "Self-deletion should return 403")
        val body = resp.bodyAsText()
        assertTrue(
            body.contains("Cannot delete your own account") || body.contains("Forbidden"),
            "Response should indicate forbidden: $body"
        )

        val verify = client.get("$baseUrl/api/users/$adminUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, verify.status.value, "Admin should still exist")
    }

    // STC: E2E-API-05 — Status change API (create test user)
    @Test @Order(40)
    fun `E2E-API-05a - create test user`() = runBlocking {
        val resp = client.post("$baseUrl/api/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"Status Test","email":"status-e2e-crud@test.com","role":"READER"}""")
        }
        assertEquals(201, resp.status.value)
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        statusUserId = idRegex.find(resp.bodyAsText())?.groupValues?.get(1) ?: ""
        assertTrue(statusUserId.isNotBlank())
    }

    // STC: E2E-API-05 — Disable → verify → Enable → verify → invalid → 400
    @Test @Order(41)
    fun `E2E-API-05b - disable verify enable verify`() = runBlocking {
        Assumptions.assumeTrue(statusUserId.isNotBlank())

        val disable = client.put("$baseUrl/api/users/$statusUserId/status") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"status":"DISABLED"}""")
        }
        assertEquals(200, disable.status.value)
        assertTrue(disable.bodyAsText().contains("DISABLED"))

        val getDisabled = client.get("$baseUrl/api/users/$statusUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(getDisabled.bodyAsText().contains("DISABLED"))

        val enable = client.put("$baseUrl/api/users/$statusUserId/status") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"status":"ACTIVE"}""")
        }
        assertEquals(200, enable.status.value)
        assertTrue(enable.bodyAsText().contains("ACTIVE"))
    }

    // STC: E2E-API-05 — Invalid status → 400
    @Test @Order(42)
    fun `E2E-API-05c - invalid status returns 400`() = runBlocking {
        Assumptions.assumeTrue(statusUserId.isNotBlank())
        val resp = client.put("$baseUrl/api/users/$statusUserId/status") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"status":"INVALID"}""")
        }
        assertEquals(400, resp.status.value)
        assertTrue(resp.bodyAsText().contains("Invalid status"))
    }

    // STC: E2E-API-05 — Cleanup
    @Test @Order(43)
    fun `E2E-API-05d - cleanup status test user`() = runBlocking {
        Assumptions.assumeTrue(statusUserId.isNotBlank())
        assertEquals(204, client.delete("$baseUrl/api/users/$statusUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }.status.value)
    }

    // STC: E2E-API-06 — Audit log verification (create user)
    @Test @Order(50)
    fun `E2E-API-06a - create user for audit`() = runBlocking {
        val resp = client.post("$baseUrl/api/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"Audit E2E","email":"audit-e2e-crud@test.com","role":"READER"}""")
        }
        assertEquals(201, resp.status.value)
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        auditUserId = idRegex.find(resp.bodyAsText())?.groupValues?.get(1) ?: ""
        assertTrue(auditUserId.isNotBlank())
    }

    // STC: E2E-API-06 — Verify create audit entry
    @Test @Order(51)
    fun `E2E-API-06b - verify create audit`() = runBlocking {
        Assumptions.assumeTrue(auditUserId.isNotBlank())
        val resp = client.get("$baseUrl/api/users/audit-log") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        assertTrue(resp.bodyAsText().contains("USER_CREATED"))
    }

    // STC: E2E-API-06 — Update + disable + delete + verify audit
    @Test @Order(52)
    fun `E2E-API-06c - update disable delete verify audit`() = runBlocking {
        Assumptions.assumeTrue(auditUserId.isNotBlank())

        client.put("$baseUrl/api/users/$auditUserId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"Audit Updated","email":"audit-upd-crud@test.com"}""")
        }
        var audit = client.get("$baseUrl/api/users/audit-log") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(audit.bodyAsText().contains("USER_UPDATED"))

        client.put("$baseUrl/api/users/$auditUserId/status") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"status":"DISABLED"}""")
        }
        audit = client.get("$baseUrl/api/users/audit-log") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(audit.bodyAsText().contains("USER_DISABLED"))

        client.delete("$baseUrl/api/users/$auditUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        audit = client.get("$baseUrl/api/users/audit-log") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertTrue(audit.bodyAsText().contains("USER_DELETED"))
    }
}
