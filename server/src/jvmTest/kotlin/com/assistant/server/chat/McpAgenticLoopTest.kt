package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatAction
import com.assistant.chat.ChatResponse
import com.assistant.server.chat.models.SyncResult
import com.assistant.server.chat.models.SyncType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for McpAgenticLoop: MCP integration, sync warnings,
 * Confluence actions, tool call parsing, and MCP-unavailable fallback.
 * Requirements: 17.2, 17.6, 18.1, 18.5
 */
class McpAgenticLoopTest {

    // --- parseMcpToolCall ---

    @Test
    fun `parseMcpToolCall extracts valid tool call from AI response`() {
        val response = """Here is the result: {"mcpToolCall":{"serverId":"jira","toolName":"create_issue","arguments":{}}}"""
        val call = McpAgenticLoop.parseMcpToolCall(response)
        assertNotNull(call)
        assertEquals("jira", call!!.serverId)
        assertEquals("create_issue", call.toolName)
    }

    @Test
    fun `parseMcpToolCall returns null when no tool call present`() {
        assertNull(McpAgenticLoop.parseMcpToolCall("Just a normal AI reply"))
    }

    @Test
    fun `parseMcpToolCall returns null for malformed JSON`() {
        assertNull(McpAgenticLoop.parseMcpToolCall("""{"mcpToolCall": broken}"""))
    }

    // --- appendSyncWarnings ---

    @Test
    fun `appendSyncWarnings appends warning for failed sync`() {
        val response = ChatResponse(reply = "Done")
        val results = listOf(
            SyncResult(false, SyncType.CREATE_TICKET, warningMessage = "Graph sync pending")
        )
        val updated = McpAgenticLoop.appendSyncWarnings(response, results)
        assertTrue(updated.reply.contains("⚠️"))
        assertTrue(updated.reply.contains("Graph sync pending"))
    }

    @Test
    fun `appendSyncWarnings returns unchanged response when all syncs succeed`() {
        val response = ChatResponse(reply = "Done")
        val results = listOf(SyncResult(true, SyncType.CREATE_TICKET, ticketKey = "PROJ-1"))
        val updated = McpAgenticLoop.appendSyncWarnings(response, results)
        assertEquals("Done", updated.reply)
    }

    @Test
    fun `appendSyncWarnings returns unchanged response for empty results`() {
        val response = ChatResponse(reply = "Done")
        val updated = McpAgenticLoop.appendSyncWarnings(response, emptyList())
        assertEquals("Done", updated.reply)
    }


    // --- appendConfluenceActions ---

    @Test
    fun `appendConfluenceActions adds openUrl actions for pages with URLs`() {
        val response = ChatResponse(reply = "Found docs")
        val pages = listOf(
            ConfluencePage("1", "Auth Guide", "https://wiki.example.com/auth", "How to auth")
        )
        val updated = McpAgenticLoop.appendConfluenceActions(response, pages)
        assertEquals(1, updated.actions.size)
        assertEquals("openUrl", updated.actions[0].type)
        assertEquals("https://wiki.example.com/auth", updated.actions[0].params["url"])
        assertTrue(updated.actions[0].label.contains("Auth Guide"))
    }

    @Test
    fun `appendConfluenceActions skips pages without URL`() {
        val response = ChatResponse(reply = "Found docs")
        val pages = listOf(ConfluencePage("1", "No URL Page", null, "Summary"))
        val updated = McpAgenticLoop.appendConfluenceActions(response, pages)
        assertEquals(0, updated.actions.size)
    }

    @Test
    fun `appendConfluenceActions preserves existing actions`() {
        val existing = ChatAction(type = "navigate", label = "Go", params = mapOf("screen" to "dashboard"))
        val response = ChatResponse(reply = "Result", actions = listOf(existing))
        val pages = listOf(ConfluencePage("1", "Doc", "https://doc.com", "Info"))
        val updated = McpAgenticLoop.appendConfluenceActions(response, pages)
        assertEquals(2, updated.actions.size)
        assertEquals("navigate", updated.actions[0].type)
        assertEquals("openUrl", updated.actions[1].type)
    }

    @Test
    fun `appendConfluenceActions returns unchanged response for empty pages`() {
        val response = ChatResponse(reply = "No docs")
        val updated = McpAgenticLoop.appendConfluenceActions(response, emptyList())
        assertSame(response, updated)
    }

    // --- execute: MCP unavailable fallback (Req 17.6, 18.5) ---

    @Test
    fun `execute returns AI response directly when mcpProcessManager is null`() = runBlocking {
        val aiResult = AIResult.Success("No MCP available, here is local data")
        val response = McpAgenticLoop.execute(
            initialPrompt = "find bugs",
            mcpProcessManager = null,
            callAI = { aiResult },
            toResponse = { result, _ -> ChatResponse(reply = (result as AIResult.Success).response) }
        )
        assertEquals("No MCP available, here is local data", response.reply)
    }

    @Test
    fun `execute with sync handler returns response when no tool call in AI reply`() = runBlocking {
        val aiResult = AIResult.Success("Just a text reply, no tool call")
        val response = McpAgenticLoop.execute(
            initialPrompt = "hello",
            mcpProcessManager = null,
            callAI = { aiResult },
            toResponse = { result, _ -> ChatResponse(reply = (result as AIResult.Success).response) },
            syncHandler = null,
            projectKey = null
        )
        assertEquals("Just a text reply, no tool call", response.reply)
    }

    @Test
    fun `execute gracefully handles AI failure result`() = runBlocking {
        val aiResult = AIResult.Failure("AI service error")
        val response = McpAgenticLoop.execute(
            initialPrompt = "test",
            mcpProcessManager = null,
            callAI = { aiResult },
            toResponse = { result, _ -> ChatResponse(reply = (result as AIResult.Failure).error) }
        )
        assertEquals("AI service error", response.reply)
    }

    // --- Multiple sync warnings ---

    @Test
    fun `appendSyncWarnings joins multiple warnings`() {
        val response = ChatResponse(reply = "Done")
        val results = listOf(
            SyncResult(false, SyncType.CREATE_TICKET, warningMessage = "Create failed"),
            SyncResult(true, SyncType.UPDATE_TICKET, ticketKey = "PROJ-1"),
            SyncResult(false, SyncType.LINK_TICKETS, warningMessage = "Link failed")
        )
        val updated = McpAgenticLoop.appendSyncWarnings(response, results)
        assertTrue(updated.reply.contains("Create failed"))
        assertTrue(updated.reply.contains("Link failed"))
        assertFalse(updated.reply.contains("PROJ-1"))
    }
}
