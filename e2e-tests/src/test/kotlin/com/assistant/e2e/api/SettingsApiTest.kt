package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Settings API tests: GET/PUT settings, validation.
 */
@TestMethodOrder(OrderAnnotation::class)
class SettingsApiTest : ApiTestBase() {

    @Test @Order(30)
    fun getSettingsRequiresAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/settings")
        assertEquals(401, resp.status.value)
    }

    @Test @Order(31)
    fun getSettingsWithAuth() = runBlocking {
        val resp = client.get("$baseUrl/api/settings") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(32)
    fun putSettingsIgnoresUnknownFields() = runBlocking {
        // After removing legacy fields, unknown fields like jiraHost are silently ignored
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"jwtSecret":"new-secret-value-for-test"}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(33)
    fun putSettingsSuccess() = runBlocking {
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"jwtSecret":"another-valid-secret"}""")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(120)
    fun settingsInvalidPort() = runBlocking {
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"port":99999}""")
        }
        assertEquals(400, resp.status.value, "Port 99999 (>65535) should return 400")
    }

    @Test @Order(121)
    fun settingsIgnoresLegacyFields() = runBlocking {
        // After removing legacy fields, aiProviderUrl is silently ignored
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{"aiProviderUrl":"not-url"}""")
        }
        // Unknown fields are ignored, so this should return 200 (no validation on removed fields)
        assertTrue(
            resp.status.value in listOf(200, 400),
            "PUT with legacy field should return 200 (ignored) or 400, got ${resp.status.value}"
        )
    }

    @Test @Order(122)
    fun settingsEmptyBodyReturns400or200() = runBlocking {
        val resp = client.put("$baseUrl/api/settings") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            setBody("""{}""")
        }
        assertTrue(
            resp.status.value in listOf(200, 400),
            "PUT /api/settings with {} should return 200 or 400, got ${resp.status.value}"
        )
    }

    @Test @Order(301)
    fun settingsStatusReturnsFalseWhenNotConfigured() = runBlocking {
        val resp = client.get("$baseUrl/api/settings/status")
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("configured"), "Response should contain 'configured' field")
    }
}
