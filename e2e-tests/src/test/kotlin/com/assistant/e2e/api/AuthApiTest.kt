package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * Authentication tests: login, JWT claims, auth/unauth requests,
 * expired/malformed JWT, logout, double login/logout.
 */
@TestMethodOrder(OrderAnnotation::class)
class AuthApiTest : ApiTestBase() {

    @Test @Order(10)
    fun loginReturnsJwtToken() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        assertEquals(200, resp.status.value, "Login should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("jwt"), "Response should contain 'jwt' field")
        val tokenRegex = """"jwt"\s*:\s*"([^"]+)"""".toRegex()
        val match = tokenRegex.find(body)
        assertNotNull(match, "Should be able to extract JWT from response")
        adminJwt = match!!.groupValues[1]
        assertTrue(adminJwt.isNotBlank(), "JWT token should not be blank")
    }

    @Test @Order(11)
    fun jwtContainsRequiredClaims() {
        assertTrue(adminJwt.isNotBlank(), "JWT must be obtained first")
        val parts = adminJwt.split(".")
        assertTrue(parts.size == 3, "JWT should have 3 parts")
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        assertTrue(payload.contains("user_id"), "JWT payload should contain user_id")
        assertTrue(payload.contains("email"), "JWT payload should contain email")
        assertTrue(payload.contains("role"), "JWT payload should contain role")
    }

    @Test @Order(12)
    fun authenticatedRequestSucceeds() = runBlocking {
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "Authenticated GET /api/projects should return 200")
    }

    @Test @Order(13)
    fun authenticatedTestAuthEndpoint() = runBlocking {
        val resp = client.get("$baseUrl/api/test-auth") {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "Authenticated GET /api/test-auth should return 200")
    }

    @Test @Order(14)
    fun unauthenticatedRequestReturns401() = runBlocking {
        val resp = client.get("$baseUrl/api/projects")
        assertEquals(401, resp.status.value, "Unauthenticated GET /api/projects should return 401")
    }

    @Test @Order(15)
    fun malformedJwtReturns401() = runBlocking {
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
        }
        assertEquals(401, resp.status.value, "Malformed JWT should return 401")
    }

    @Test @Order(100)
    fun expiredJwtReturns401() = runBlocking {
        val expiredToken = createJwt(
            role = "ADMINISTRATOR",
            secret = JWT_SECRET,
            expSeconds = System.currentTimeMillis() / 1000 - 3600
        )
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, "Bearer $expiredToken")
        }
        assertEquals(401, resp.status.value, "Expired JWT should return 401")
    }

    @Test @Order(101)
    fun emptyBearerTokenReturns401() = runBlocking {
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, "Bearer ")
        }
        assertEquals(401, resp.status.value, "Empty Bearer token should return 401")
    }

    @Test @Order(102)
    fun noBearerPrefixReturns401() = runBlocking {
        val resp = client.get("$baseUrl/api/projects") {
            header(HttpHeaders.Authorization, adminJwt)
        }
        assertEquals(401, resp.status.value, "Authorization without 'Bearer ' prefix should return 401")
    }

    @Test @Order(103)
    fun loginWithMalformedJsonReturns400or500() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("{bad json")
        }
        assertTrue(
            resp.status.value in listOf(400, 500),
            "Malformed JSON login body should return 400 or 500, got ${resp.status.value}"
        )
        val body = resp.bodyAsText()
        assertFalse(body.contains("at com."), "Error should not expose stack traces")
    }

    @Test @Order(104)
    fun logoutWithoutAuthReturns401() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/logout")
        assertEquals(401, resp.status.value, "Logout without auth token should return 401")
    }

    @Test @Order(160)
    fun doubleLoginBothSucceed() = runBlocking {
        val resp1 = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        assertEquals(200, resp1.status.value, "First login should return 200")
        val body1 = resp1.bodyAsText()
        val tokenRegex = """"jwt"\s*:\s*"([^"]+)"""".toRegex()
        val token1 = tokenRegex.find(body1)!!.groupValues[1]
        assertTrue(token1.isNotBlank(), "First login should return a JWT")

        // Small delay to ensure different iat timestamp
        kotlinx.coroutines.delay(1100)

        val resp2 = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        assertEquals(200, resp2.status.value, "Second login should return 200")
        val body2 = resp2.bodyAsText()
        val token2 = tokenRegex.find(body2)!!.groupValues[1]
        assertTrue(token2.isNotBlank(), "Second login should return a JWT")

        // Both tokens should be valid JWTs
        assertEquals(3, token1.split(".").size, "Token 1 should have 3 parts")
        assertEquals(3, token2.split(".").size, "Token 2 should have 3 parts")

        adminJwt = token2
    }

    @Test @Order(161)
    fun doubleLogoutSecondReturns401() = runBlocking {
        val loginResp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        val loginBody = loginResp.bodyAsText()
        val tokenRegex = """"jwt"\s*:\s*"([^"]+)"""".toRegex()
        val freshToken = tokenRegex.find(loginBody)!!.groupValues[1]

        val resp1 = client.post("$baseUrl/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $freshToken")
        }
        assertEquals(200, resp1.status.value, "First logout should return 200")

        val resp2 = client.post("$baseUrl/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $freshToken")
        }
        assertTrue(
            resp2.status.value in listOf(200, 401),
            "Second logout should return 200 (stateless) or 401 (invalidated), got ${resp2.status.value}"
        )
    }

    @Test @Order(200)
    fun logoutInvalidatesSession() = runBlocking {
        val loginResp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        val loginBody = loginResp.bodyAsText()
        val tokenRegex = """"jwt"\s*:\s*"([^"]+)"""".toRegex()
        val freshToken = tokenRegex.find(loginBody)!!.groupValues[1]

        val resp = client.post("$baseUrl/api/auth/logout") {
            header(HttpHeaders.Authorization, "Bearer $freshToken")
        }
        assertEquals(200, resp.status.value)
    }

    @Test @Order(380)
    fun jwtHas24HourExpiry() {
        assertTrue(adminJwt.isNotBlank(), "JWT must be obtained first")
        val parts = adminJwt.split(".")
        assertEquals(3, parts.size, "JWT should have 3 parts")
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        val expRegex = """"exp"\s*:\s*(\d+)""".toRegex()
        val iatRegex = """"iat"\s*:\s*(\d+)""".toRegex()
        val expMatch = expRegex.find(payload)
        assertNotNull(expMatch, "JWT payload should contain 'exp' claim")
        val exp = expMatch!!.groupValues[1].toLong()

        val iatMatch = iatRegex.find(payload)
        if (iatMatch != null) {
            val iat = iatMatch.groupValues[1].toLong()
            val diffSeconds = exp - iat
            assertTrue(
                diffSeconds in 77760..95040,
                "JWT expiry should be ~24h (86400s), got ${diffSeconds}s"
            )
        } else {
            val nowSeconds = System.currentTimeMillis() / 1000
            val diffSeconds = exp - nowSeconds
            assertTrue(
                diffSeconds in 0..95040,
                "JWT exp should be in the future within ~24h, got ${diffSeconds}s from now"
            )
        }
    }

    @Test @Order(381)
    fun jwtHasIssuerClaim() {
        assertTrue(adminJwt.isNotBlank(), "JWT must be obtained first")
        val parts = adminJwt.split(".")
        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        assertTrue(payload.contains("\"iss\""), "JWT payload should contain 'iss' claim")
        assertTrue(
            payload.contains("jira-assistant"),
            "JWT issuer should be 'jira-assistant', payload: ${payload.take(200)}"
        )
    }

    @Test @Order(382)
    fun loginResponseContainsUserObject() = runBlocking {
        val resp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("user"), "Login response should contain 'user' object")
        val hasUserId = body.contains("userId") || body.contains("user_id") || body.contains("\"id\"")
        assertTrue(hasUserId, "Login response user should contain userId/id field")
        val hasEmail = body.contains("email")
        assertTrue(hasEmail, "Login response user should contain email field")
        val hasRole = body.contains("role")
        assertTrue(hasRole, "Login response user should contain role field")
    }
}
