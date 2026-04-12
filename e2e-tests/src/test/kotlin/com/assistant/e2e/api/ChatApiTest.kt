package com.assistant.e2e.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation

/**
 * E2E API tests for the AI Chat Sidebar endpoints:
 * POST /api/chat/send            — Send message, get AI response
 * POST /api/chat/execute-action   — Execute AI-suggested action (RBAC per actionType)
 * GET  /api/chat/history          — Paginated chat history
 * DELETE /api/chat/history        — Delete current user's chat history
 *
 * Requirements: 19.20, 19.21, 19.22, 19.23, 19.13, 19.14
 */
@TestMethodOrder(OrderAnnotation::class)
class ChatApiTest : ApiTestBase() {

    private fun chatUrl(path: String) = "$baseUrl/api/chat/$path"

    // ── 1. POST /api/chat/send → 200 + ChatResponse ────────

    @Test @Order(1)
    fun sendChatReturns200WithReply() = runBlocking {
        val resp = client.post(chatUrl("send")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Hello"}""")
        }
        assertEquals(200, resp.status.value, "POST /api/chat/send should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"reply\""), "Response should contain 'reply' field, got: ${body.take(300)}")
    }

    // ── 2. POST /api/chat/send saves to history ─────────────

    @Test @Order(2)
    fun sendChatSavesToHistory() = runBlocking {
        // Send a unique message
        val uniqueMsg = "E2E-test-history-check-${System.currentTimeMillis()}"
        val sendResp = client.post(chatUrl("send")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
            setBody("""{"message":"$uniqueMsg"}""")
        }
        assertEquals(200, sendResp.status.value, "Send should succeed")

        // Verify it appears in history
        val histResp = client.get(chatUrl("history")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, histResp.status.value, "GET history should return 200")
        val histBody = histResp.bodyAsText()
        assertTrue(histBody.contains(uniqueMsg), "History should contain the sent message, got: ${histBody.take(500)}")
    }

    // ── 3. POST /api/chat/execute-action navigate → 200 ─────

    @Test @Order(3)
    fun executeActionNavigateReturns200() = runBlocking {
        val resp = client.post(chatUrl("execute-action")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
            setBody("""{"actionType":"navigate","parameters":{"screen":"dashboard"}}""")
        }
        assertEquals(200, resp.status.value, "Navigate action should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"success\""), "Response should contain 'success' field, got: ${body.take(300)}")
    }

    // ── 4. execute-action changeConfig Reader → 403 ─────────

    @Test @Order(4)
    fun executeActionChangeConfigReaderReturns403() = runBlocking {
        val resp = client.post(chatUrl("execute-action")) {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            contentType(ContentType.Application.Json)
            setBody("""{"actionType":"changeConfig","parameters":{"configType":"setting","key":"test","value":"val"}}""")
        }
        assertEquals(403, resp.status.value, "Reader should not be able to changeConfig (requires Administrator)")
    }

    // ── 5. execute-action changeConfig Admin → 200 ──────────

    @Test @Order(5)
    fun executeActionChangeConfigAdminReturns200() = runBlocking {
        val resp = client.post(chatUrl("execute-action")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
            setBody("""{"actionType":"changeConfig","parameters":{"configType":"setting","key":"chat-test-key","value":"chat-test-value"}}""")
        }
        assertEquals(200, resp.status.value, "Admin should be able to changeConfig")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"success\""), "Response should contain 'success' field, got: ${body.take(300)}")
    }

    // ── 6. execute-action triggerAnalysis Reader → 403 ──────

    @Test @Order(6)
    fun executeActionTriggerAnalysisReaderReturns403() = runBlocking {
        val resp = client.post(chatUrl("execute-action")) {
            header(HttpHeaders.Authorization, "Bearer ${readerJwt()}")
            contentType(ContentType.Application.Json)
            setBody("""{"actionType":"triggerAnalysis","parameters":{"ticketId":"PROJ-1"}}""")
        }
        assertEquals(403, resp.status.value, "Reader should not be able to triggerAnalysis (requires ANALYZE_AI)")
    }

    // ── 7. GET /api/chat/history → 200 + pagination ─────────

    @Test @Order(7)
    fun getHistoryReturns200WithPagination() = runBlocking {
        val resp = client.get(chatUrl("history")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET /api/chat/history should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"messages\""), "Response should contain 'messages' field")
        assertTrue(body.contains("\"total\""), "Response should contain 'total' field")
        assertTrue(body.contains("\"page\""), "Response should contain 'page' field")
        assertTrue(body.contains("\"size\""), "Response should contain 'size' field")
    }

    // ── 8. GET /api/chat/history empty → messages=[], total=0

    @Test @Order(8)
    fun getHistoryEmptyReturnsEmptyList() = runBlocking {
        // Delete history first to ensure empty state
        val delResp = client.delete(chatUrl("history")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, delResp.status.value, "DELETE history should return 200")

        // Now GET should return empty
        val resp = client.get(chatUrl("history")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET history should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"messages\":[]") || body.contains("\"messages\" : []"),
            "Messages should be empty array, got: ${body.take(300)}")
        assertTrue(body.contains("\"total\":0") || body.contains("\"total\" : 0"),
            "Total should be 0, got: ${body.take(300)}")
    }

    // ── 9. DELETE /api/chat/history → 200, then GET → empty ─

    @Test @Order(9)
    fun deleteHistoryReturns200ThenGetReturnsEmpty() = runBlocking {
        // Send a message first so there's something to delete
        client.post(chatUrl("send")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Message before delete"}""")
        }

        // Delete history
        val delResp = client.delete(chatUrl("history")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, delResp.status.value, "DELETE /api/chat/history should return 200")

        // Verify history is now empty
        val resp = client.get(chatUrl("history")) {
            header(HttpHeaders.Authorization, "Bearer $adminJwt")
        }
        assertEquals(200, resp.status.value, "GET history after delete should return 200")
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"messages\":[]") || body.contains("\"messages\" : []"),
            "Messages should be empty after delete, got: ${body.take(300)}")
        assertTrue(body.contains("\"total\":0") || body.contains("\"total\" : 0"),
            "Total should be 0 after delete, got: ${body.take(300)}")
    }

    // ── 10. No JWT → 401 ────────────────────────────────────

    @Test @Order(10)
    fun noJwtReturns401() = runBlocking {
        val resp = client.post(chatUrl("send")) {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Hello"}""")
        }
        assertEquals(401, resp.status.value, "POST /api/chat/send without JWT should return 401")
    }
}
