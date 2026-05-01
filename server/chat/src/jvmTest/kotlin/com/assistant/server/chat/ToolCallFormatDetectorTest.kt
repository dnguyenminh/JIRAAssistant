package com.assistant.server.chat

import com.assistant.server.chat.models.AIModelContext
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for ToolCallFormatDetector.
 * Requirements: 19.92, 19.93, 19.94, 19.95, 19.99
 */
class ToolCallFormatDetectorTest {

    private val modelCtx = AIModelContext("OLLAMA", "gemma3:4b")

    @Test
    fun `detect mcpToolCall JSON format`() = runBlocking {
        val response = """{"mcpToolCall":{"serverId":"local-knowledge-base","toolName":"get_ticket_info","arguments":{"ticketId":"ICL2-339"}}}"""
        val result = ToolCallFormatDetector.detect(response, null, null)
        assertNotNull(result)
        assertEquals("get_ticket_info", result!!.toolName)
        assertEquals("local-knowledge-base", result.serverId)
    }

    @Test
    fun `detect tool_name tool_input JSON format`() = runBlocking {
        val response = """{"tool_name":"get_ticket_info","tool_input":{"ticketId":"ICL2-339"}}"""
        val result = ToolCallFormatDetector.detect(response, null, null)
        assertNotNull(result)
        assertEquals("get_ticket_info", result!!.toolName)
    }

    @Test
    fun `detect text pattern format`() = runBlocking {
        val response = """Tool Call: search_knowledge(query="test query")"""
        val result = ToolCallFormatDetector.detect(response, null, null)
        assertNotNull(result)
        assertEquals("search_knowledge", result!!.toolName)
    }

    @Test
    fun `cache hit returns cached format result`() = runBlocking {
        val repo = FakeSettingsRepo()
        repo.put("ai_tool_call_format:OLLAMA:gemma3:4b", "mcpToolCall")
        val response = """{"mcpToolCall":{"serverId":"local-knowledge-base","toolName":"search_knowledge","arguments":{"query":"test"}}}"""
        val result = ToolCallFormatDetector.detect(response, modelCtx, repo)
        assertNotNull(result)
        assertEquals("search_knowledge", result!!.toolName)
    }

    @Test
    fun `stale cache invalidated and retries all patterns`() = runBlocking {
        val repo = FakeSettingsRepo()
        repo.put("ai_tool_call_format:OLLAMA:gemma3:4b", "mcpToolCall")
        // Response uses tool_name format, not mcpToolCall
        val response = """{"tool_name":"search_knowledge","tool_input":{"query":"test"}}"""
        val result = ToolCallFormatDetector.detect(response, modelCtx, repo)
        assertNotNull(result)
        assertEquals("search_knowledge", result!!.toolName)
        // Cache should be updated to tool_name_input
        assertEquals("tool_name_input", repo.get("ai_tool_call_format:OLLAMA:gemma3:4b"))
    }

    @Test
    fun `no tool call in response returns null`() = runBlocking {
        val response = """{"reply":"Just a normal response","actions":[]}"""
        val result = ToolCallFormatDetector.detect(response, null, null)
        assertNull(result)
    }

    @Test
    fun `cache key uses correct prefix`() = runBlocking {
        val repo = FakeSettingsRepo()
        val response = """{"mcpToolCall":{"serverId":"local-knowledge-base","toolName":"get_ticket_info","arguments":{"ticketId":"X-1"}}}"""
        ToolCallFormatDetector.detect(response, modelCtx, repo)
        val key = "ai_tool_call_format:OLLAMA:gemma3:4b"
        assertEquals("mcpToolCall", repo.get(key))
    }

    @Test
    fun `saves format after successful detection without cache`() = runBlocking {
        val repo = FakeSettingsRepo()
        val response = """Tool Call: get_ticket_info(ticketId="ICL2-100")"""
        ToolCallFormatDetector.detect(response, modelCtx, repo)
        assertEquals("text_pattern", repo.get("ai_tool_call_format:OLLAMA:gemma3:4b"))
    }

    /** In-memory fake SettingsRepository for testing. */
    private class FakeSettingsRepo(
        private val store: MutableMap<String, String> = mutableMapOf()
    ) : SettingsRepository {
        override suspend fun getAll() = store.toMap()
        override suspend fun get(key: String) = store[key]
        override suspend fun put(key: String, value: String) { store[key] = value }
        override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
    }
}
