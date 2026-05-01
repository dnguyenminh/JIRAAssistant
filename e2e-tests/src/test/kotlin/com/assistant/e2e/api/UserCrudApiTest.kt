package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * E2E-API tests for User CRUD — Part 1: Core CRUD lifecycle.
 *
 * Covers STC: E2E-API-01 (duplicate email), E2E-API-02 (full lifecycle),
 * E2E-API-03 (RBAC/auth checks).
 *
 * Requires server running at baseUrl (default: http://localhost:8080).
 */
@TestMethodOrder(OrderAnnotation::class)
class UserCrudApiTest : ApiTestBase() {

    companion object {
        var lifecycleUserId: String = ""
    }

    // STC: E2E-API-01 — Duplicate email rejection
    // Strategy: create a user first, then try to create another with the same email.
    // This avoids depending on pre-seeded email which may vary.
    @Test @Order(1)
    fun `E2E-API-01 - duplicate email returns 409`() = runBlocking {
        // Step 1: Create a user with a known email
        val uniqueEmail = "dup-test-${System.currentTimeMillis()}@test.com"
        val createResp = client.post("$baseUrl/api/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"First User","email":"$uniqueEmail","role":"READER"}""")
        }
        assertEquals(201, createResp.status.value, "First create should succeed")

        // Step 2: Try to create another user with the same email → expect 409
        val dupResp = client.post("$baseUrl/api/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"Dup Test","email":"$uniqueEmail","role":"READER"}""")
        }
        assertEquals(409, dupResp.status.value, "Duplicate email should return 409")
        assertTrue(dupResp.bodyAsText().contains("Email already exists"))

        // Cleanup: delete the created user
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        val userId = idRegex.find(createResp.bodyAsText())?.groupValues?.get(1)
        if (!userId.isNullOrBlank()) {
            client.delete("$baseUrl/api/users/$userId") {
                header(HttpHeaders.Authorization, "Bearer $adminJwt")
            }
        }
    }

    // STC: E2E-API-02 — Full CRUD lifecycle (Create)
    @Test @Order(10)
    fun `E2E-API-02a - create user`() = runBlocking {
        val resp = client.post("$baseUrl/api/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"E2E Lifecycle","email":"e2e-lifecycle-crud@test.com","role":"READER"}""")
        }
        assertEquals(201, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("ACTIVE"))
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        lifecycleUserId = idRegex.find(body)?.groupValues?.get(1) ?: ""
        assertTrue(lifecycleUserId.isNotBlank())
    }

    // STC: E2E-API-02 — Full CRUD lifecycle (Get)
    @Test @Order(11)
    fun `E2E-API-02b - get created user`() = runBlocking {
        Assumptions.assumeTrue(lifecycleUserId.isNotBlank())
        val resp = client.get("$baseUrl/api/users/$lifecycleUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
        assertTrue(resp.bodyAsText().contains("E2E Lifecycle"))
    }

    // STC: E2E-API-02 — Full CRUD lifecycle (Update)
    @Test @Order(12)
    fun `E2E-API-02c - update user`() = runBlocking {
        Assumptions.assumeTrue(lifecycleUserId.isNotBlank())
        val resp = client.put("$baseUrl/api/users/$lifecycleUserId") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"name":"E2E Updated","email":"e2e-updated-crud@test.com"}""")
        }
        assertEquals(200, resp.status.value)
        assertTrue(resp.bodyAsText().contains("E2E Updated"))
    }

    // STC: E2E-API-02 — Full CRUD lifecycle (Disable→Enable→Delete→Verify)
    @Test @Order(13)
    fun `E2E-API-02d - disable enable delete verify`() = runBlocking {
        Assumptions.assumeTrue(lifecycleUserId.isNotBlank())
        val disable = client.put("$baseUrl/api/users/$lifecycleUserId/status") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"status":"DISABLED"}""")
        }
        assertEquals(200, disable.status.value)
        assertTrue(disable.bodyAsText().contains("DISABLED"))

        val enable = client.put("$baseUrl/api/users/$lifecycleUserId/status") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"status":"ACTIVE"}""")
        }
        assertEquals(200, enable.status.value)

        val del = client.delete("$baseUrl/api/users/$lifecycleUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(204, del.status.value)

        val get = client.get("$baseUrl/api/users/$lifecycleUserId") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(404, get.status.value)
    }

    // STC: E2E-API-03 — No JWT → 401 for all endpoints
    @Test @Order(20)
    fun `E2E-API-03a - no JWT returns 401`() = runBlocking {
        val calls = listOf(
            client.post("$baseUrl/api/users") { contentType(ContentType.Application.Json); setBody("{}") },
            client.get("$baseUrl/api/users/any-id"),
            client.put("$baseUrl/api/users/any-id") { contentType(ContentType.Application.Json); setBody("{}") },
            client.put("$baseUrl/api/users/any-id/status") { contentType(ContentType.Application.Json); setBody("{}") },
            client.delete("$baseUrl/api/users/any-id")
        )
        calls.forEachIndexed { i, resp ->
            assertEquals(401, resp.status.value, "Endpoint #${i + 1} without JWT should be 401")
        }
    }

    // STC: E2E-API-03 — READER JWT → 403 for all endpoints
    @Test @Order(21)
    fun `E2E-API-03b - READER returns 403`() = runBlocking {
        val jwt = readerJwt()
        val calls = listOf(
            client.post("$baseUrl/api/users") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $jwt")
                setBody("""{"name":"X","email":"x@t.com","role":"READER"}""")
            },
            client.get("$baseUrl/api/users/any-id") { header(HttpHeaders.Authorization, "Bearer $jwt") },
            client.put("$baseUrl/api/users/any-id") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $jwt")
                setBody("""{"name":"X","email":"x@t.com"}""")
            },
            client.put("$baseUrl/api/users/any-id/status") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $jwt")
                setBody("""{"status":"DISABLED"}""")
            },
            client.delete("$baseUrl/api/users/any-id") { header(HttpHeaders.Authorization, "Bearer $jwt") }
        )
        calls.forEachIndexed { i, resp ->
            assertEquals(403, resp.status.value, "Endpoint #${i + 1} with READER should be 403")
        }
    }
}
