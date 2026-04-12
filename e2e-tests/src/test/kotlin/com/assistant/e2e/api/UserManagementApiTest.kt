package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * User Management API tests: users endpoint, role change, permissions.
 */
@TestMethodOrder(OrderAnnotation::class)
class UserManagementApiTest : ApiTestBase() {

    @Test @Order(70)
    fun getUsersRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/users")
        assertEquals(401, resp.status.value)
    }

    @Test @Order(71)
    fun getUsersWithAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/users") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(340)
    fun changeUserRoleEndpoint() = runBlocking {
        val resp = client.put("$baseUrl/api/users/test-user-id/role") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"role":"READER"}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Change user role should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(341)
    fun changeUserRoleRequiresAuth() = runBlocking {
        val resp = client.put("$baseUrl/api/users/test-user-id/role") {
            contentType(ContentType.Application.Json)
            setBody("""{"role":"READER"}""")
        }
        assertEquals(401, resp.status.value, "PUT /api/users/{userId}/role without auth should return 401")
    }

    @Test @Order(343)
    fun togglePermissionEndpoint() = runBlocking {
        val resp = client.put("$baseUrl/api/users/test-user-id/permissions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"permission":"VIEW_DASHBOARD","enabled":true}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 404),
            "Toggle permission should return 200 or 404, got ${resp.status.value}"
        )
    }

    @Test @Order(344)
    fun togglePermissionRequiresAuth() = runBlocking {
        val resp = client.put("$baseUrl/api/users/test-user-id/permissions") {
            contentType(ContentType.Application.Json)
            setBody("""{"permission":"VIEW_DASHBOARD","enabled":true}""")
        }
        assertEquals(401, resp.status.value, "PUT /api/users/{userId}/permissions without auth should return 401")
    }
}
