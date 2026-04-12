package com.assistant.e2e.api

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Shared base class for all API E2E tests.
 *
 * Provides HTTP client, JWT helpers, admin token obtainment,
 * and optional Jira configuration from environment variables.
 *
 * To run with real Jira connection:
 *   JIRA_TEST_URL=https://yourorg.atlassian.net \
 *   JIRA_TEST_USER=user@example.com \
 *   JIRA_TEST_TOKEN=your-api-token \
 *   ./gradlew :e2e-tests:apiTest
 */
@Tag("api")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ApiTestBase {

    companion object {
        val baseUrl: String =
            System.getProperty("test.server.baseUrl") ?: "http://localhost:8080"

        var adminJwt: String = ""

        /** True when real Jira credentials are provided via env vars. */
        var jiraConfigured: Boolean = false
            private set

        /** The Jira project key discovered after configuring Jira (first project). */
        var jiraProjectKey: String = "PROJ"
            private set

        val client = HttpClient(CIO) {
            engine { requestTimeout = 30_000 }
            expectSuccess = false
        }

        const val JWT_SECRET = "e2e-test-jwt-secret-for-testing-only"

        // Jira credentials from env vars (never hardcoded)
        val jiraUrl: String? = System.getenv("JIRA_TEST_URL")
        val jiraUser: String? = System.getenv("JIRA_TEST_USER")
        val jiraToken: String? = System.getenv("JIRA_TEST_TOKEN")

        val hasRealJira: Boolean get() = !jiraUrl.isNullOrBlank() && !jiraUser.isNullOrBlank() && !jiraToken.isNullOrBlank()

        fun createJwt(
            role: String,
            secret: String,
            userId: String = "test-user-${role.lowercase()}",
            email: String = "${role.lowercase()}@test.com",
            expSeconds: Long = System.currentTimeMillis() / 1000 + 86400
        ): String {
            val encoder = java.util.Base64.getUrlEncoder().withoutPadding()
            val header = """{"alg":"HS256","typ":"JWT"}"""
            val payload = """{"user_id":"$userId","email":"$email","role":"$role","project_key":"$jiraProjectKey","jira_domain":"","iss":"jira-assistant","exp":$expSeconds}"""
            val headerB64 = encoder.encodeToString(header.toByteArray())
            val payloadB64 = encoder.encodeToString(payload.toByteArray())
            val signingInput = "$headerB64.$payloadB64"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            val signature = mac.doFinal(signingInput.toByteArray())
            val signatureB64 = encoder.encodeToString(signature)
            return "$headerB64.$payloadB64.$signatureB64"
        }

        fun readerJwt() = createJwt("READER", JWT_SECRET)
        fun neuralArchitectJwt() = createJwt("NEURAL_ARCHITECT", JWT_SECRET)
    }

    @BeforeAll
    fun setupTestEnvironment() = runBlocking {
        // 1. Login to get admin JWT
        val loginResp = client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin","password":"admin123"}""")
        }
        val loginBody = loginResp.bodyAsText()
        val tokenRegex = """"jwt"\s*:\s*"([^"]+)"""".toRegex()
        val match = tokenRegex.find(loginBody)
        if (match != null) {
            adminJwt = match.groupValues[1]
        }
        println("[ApiTestBase] Admin JWT obtained: ${adminJwt.take(20)}... (${adminJwt.length} chars)")

        // 2. Configure Jira if real credentials are provided
        if (hasRealJira) {
            println("[ApiTestBase] Real Jira credentials detected — configuring Jira connection...")
            val configResp = client.put("$baseUrl/api/integrations/jira/config") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $adminJwt")
                setBody("""{"domain":"$jiraUrl","email":"$jiraUser","apiToken":"$jiraToken"}""")
            }
            val configBody = configResp.bodyAsText()
            println("[ApiTestBase] Jira config response: ${configResp.status.value} — ${configBody.take(300)}")

            if (configResp.status.value == 200 && configBody.contains("\"active\"", ignoreCase = true)) {
                jiraConfigured = true

                // Extract first project key from response — prefer one with known data
                val projectKeyRegex = """"key"\s*:\s*"([^"]+)"""".toRegex()
                val allKeys = projectKeyRegex.findAll(configBody).map { it.groupValues[1] }.toList()
                // Try to find ITCM or use first available
                jiraProjectKey = allKeys.find { it == "ITCM" } ?: allKeys.firstOrNull() ?: "PROJ"
                println("[ApiTestBase] Using Jira project: $jiraProjectKey (from ${allKeys.size} projects)")
            } else {
                println("[ApiTestBase] WARNING: Jira config failed — tests will run with empty data")
            }
        } else {
            println("[ApiTestBase] No Jira credentials — running tests without real Jira data")
        }
    }

    /** Skip test if real Jira is not configured. */
    fun assumeJiraConfigured() {
        Assumptions.assumeTrue(jiraConfigured, "Skipped: real Jira credentials not provided (set JIRA_TEST_URL, JIRA_TEST_USER, JIRA_TEST_TOKEN)")
    }
}
