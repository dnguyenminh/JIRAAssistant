package com.assistant.server.routes

import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for handleGetTools() local KB tool logic.
 * Validates: Requirements 19.62
 */
class ChatConfigRoutesToolsTest {

    /** In-memory fake SettingsRepository for testing. */
    private class FakeSettingsRepository(
        private val store: MutableMap<String, String> = mutableMapOf()
    ) : SettingsRepository {
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override suspend fun get(key: String): String? = store[key]
        override suspend fun put(key: String, value: String) { store[key] = value }
        override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
    }

    // --- Enabled state: response contains 4 local KB ToolInfo entries ---

    @Test
    fun `buildLocalKBToolInfos returns 4 tools when enabled explicitly`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "true"))

        val tools = buildLocalKBToolInfos(settingsRepo)

        assertEquals(4, tools.size)
        assertTrue(tools.all { it.serverName == "local-knowledge-base" })
    }

    @Test
    fun `buildLocalKBToolInfos returns 4 tools when key not set (default enabled)`() = runTest {
        val settingsRepo = FakeSettingsRepository()

        val tools = buildLocalKBToolInfos(settingsRepo)

        assertEquals(4, tools.size)
        assertTrue(tools.all { it.serverName == "local-knowledge-base" })
    }

    @Test
    fun `buildLocalKBToolInfos contains search_knowledge tool`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "true"))

        val tools = buildLocalKBToolInfos(settingsRepo)

        val searchTool = tools.find { it.toolName == "search_knowledge" }
        assertNotNull(searchTool)
        assertEquals("local-knowledge-base", searchTool.serverName)
        assertTrue(searchTool.description.isNotBlank())
    }

    @Test
    fun `buildLocalKBToolInfos contains get_ticket_info tool`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "true"))

        val tools = buildLocalKBToolInfos(settingsRepo)

        val ticketTool = tools.find { it.toolName == "get_ticket_info" }
        assertNotNull(ticketTool)
        assertEquals("local-knowledge-base", ticketTool.serverName)
        assertTrue(ticketTool.description.isNotBlank())
    }

    @Test
    fun `buildLocalKBToolInfos contains search_relationships tool`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "true"))

        val tools = buildLocalKBToolInfos(settingsRepo)

        val relTool = tools.find { it.toolName == "search_relationships" }
        assertNotNull(relTool)
        assertEquals("local-knowledge-base", relTool.serverName)
        assertTrue(relTool.description.isNotBlank())
    }

    @Test
    fun `buildLocalKBToolInfos contains ingest_knowledge tool`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "true"))

        val tools = buildLocalKBToolInfos(settingsRepo)

        val ingestTool = tools.find { it.toolName == "ingest_knowledge" }
        assertNotNull(ingestTool)
        assertEquals("local-knowledge-base", ingestTool.serverName)
        assertTrue(ingestTool.description.isNotBlank())
    }

    // --- Disabled state: response does NOT contain local KB tools ---

    @Test
    fun `buildLocalKBToolInfos returns empty list when disabled`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "false"))

        val tools = buildLocalKBToolInfos(settingsRepo)

        assertTrue(tools.isEmpty())
    }

    // --- Local KB tools + external MCP tools coexist in one response ---

    @Test
    fun `local KB tools can be combined with external MCP tools in single list`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "true"))

        // Simulate external MCP tools
        val externalTools = listOf(
            ToolInfo("aws-docs", "aws-mcp-server", "Search AWS documentation"),
            ToolInfo("jira-search", "jira-mcp-server", "Search Jira issues")
        )

        // Build local KB tools
        val localTools = buildLocalKBToolInfos(settingsRepo)

        // Combine as handleGetTools() does
        val allTools = externalTools.toMutableList()
        allTools.addAll(localTools)

        assertEquals(6, allTools.size)
        // External tools present
        assertEquals(2, allTools.count { it.serverName != "local-knowledge-base" })
        // Local KB tools present
        assertEquals(4, allTools.count { it.serverName == "local-knowledge-base" })
    }

    @Test
    fun `combined list contains only external tools when local KB disabled`() = runTest {
        val settingsRepo = FakeSettingsRepository(mutableMapOf("local_kb_tool_enabled" to "false"))

        val externalTools = listOf(
            ToolInfo("aws-docs", "aws-mcp-server", "Search AWS documentation")
        )

        val localTools = buildLocalKBToolInfos(settingsRepo)
        val allTools = externalTools.toMutableList()
        allTools.addAll(localTools)

        assertEquals(1, allTools.size)
        assertEquals("aws-mcp-server", allTools[0].serverName)
    }
}
