package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.mcp.models.McpHealthResponse
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json

/**
 * Checks MCP server readiness before document generation.
 * Calls GET /api/mcp/health and optionally shows ReadinessDialog.
 * Requirements: 2.1, 2.3, 2.4, 2.5
 */
internal object McpReadinessChecker {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Call GET /api/mcp/health and return parsed McpHealthResponse.
     * Throws on network failure, timeout, or non-200 status.
     */
    suspend fun check(): McpHealthResponse {
        val token = ApiClient.getToken() ?: throw IllegalStateException("Not authenticated")
        val headers = js("({})")
        headers["Authorization"] = "Bearer $token"
        headers["Content-Type"] = "application/json"
        val opts = js("({})")
        opts["method"] = "GET"
        opts["headers"] = headers

        val resp = try {
            window.fetch("/api/mcp/health", opts).await()
        } catch (e: Exception) {
            throw IllegalStateException("Network error: ${e.message ?: "connection failed"}")
        }

        val status = resp.status.toInt()
        if (status == 401 || status == 403) {
            throw IllegalStateException("Authentication failed (HTTP $status)")
        }
        if (status != 200) {
            throw IllegalStateException("Health check failed (HTTP $status)")
        }

        val body: String = try {
            resp.text().await()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to read response: ${e.message}")
        }

        return try {
            json.decodeFromString<McpHealthResponse>(body)
        } catch (e: Exception) {
            throw IllegalStateException("Invalid health response: ${e.message}")
        }
    }

    /**
     * Check readiness and proceed: if allReady → true,
     * else show ReadinessDialog and return user choice.
     */
    suspend fun checkAndProceed(docType: String): Boolean {
        val response = check()
        if (response.allReady) return true
        return ReadinessDialog.show(response, docType)
    }
}
