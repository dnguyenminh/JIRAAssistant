package com.assistant.server.document.curation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for McpToolRegistrar.
 * Requirements: 10.3, 10.4
 */
class McpToolRegistrarTest {

    private val registrar = DefaultMcpToolRegistrar()

    @Test
    fun `tool-capable agent returns true for isToolUseSupported`() {
        assertTrue(registrar.isToolUseSupported("GeminiCliAgent"))
        assertTrue(registrar.isToolUseSupported("gemini"))
        assertTrue(registrar.isToolUseSupported("gemini-cli"))
    }

    @Test
    fun `non-tool agent returns false for isToolUseSupported`() {
        assertFalse(registrar.isToolUseSupported("OllamaAgent"))
        assertFalse(registrar.isToolUseSupported("LMStudio"))
        assertFalse(registrar.isToolUseSupported("unknown"))
    }

    @Test
    fun `buildToolBlock returns empty for no tickets`() {
        val result = registrar.buildToolBlock(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildToolBlock contains tool definitions`() {
        val tickets = listOf("TICK-1", "TICK-2", "TICK-3")
        val result = registrar.buildToolBlock(tickets)
        assertTrue(result.contains("kb_search"))
        assertTrue(result.contains("kb_read"))
        assertTrue(result.contains("TICK-1"))
        assertTrue(result.contains("TICK-2"))
        assertTrue(result.contains("TICK-3"))
    }

    @Test
    fun `buildToolBlock limits to MAX_MCP_LOOKUPS tickets`() {
        val tickets = List(30) { "TICK-$it" }
        val result = registrar.buildToolBlock(tickets)
        assertTrue(result.contains("TICK-0"))
        assertTrue(result.contains("TICK-19"))
        assertFalse(result.contains("TICK-20"))
    }

    @Test
    fun `buildToolBlock contains max lookup warning`() {
        val tickets = listOf("TICK-1")
        val result = registrar.buildToolBlock(tickets)
        assertTrue(result.contains("Max ${CurationConfig.MAX_MCP_LOOKUPS} lookups"))
    }
}
