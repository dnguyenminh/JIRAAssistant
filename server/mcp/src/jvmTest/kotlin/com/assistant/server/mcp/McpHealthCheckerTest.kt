package com.assistant.server.mcp

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.McpServerConfig
import com.assistant.mcp.McpServerRepository
import com.assistant.mcp.models.*
import com.assistant.server.mcp.internal.InternalMcpBridge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for McpHealthChecker.
 * Requirements: 1.1–1.8
 */
class McpHealthCheckerTest {

    // ── Stubs (same patterns as McpHealthCheckerPropertyTest) ───

    private class InMemoryRepo(
        private val configs: List<McpServerConfig>
    ) : McpServerRepository {
        override suspend fun getAll() = configs
        override suspend fun findById(id: String) = configs.find { it.id == id }
        override suspend fun findByName(name: String) =
            configs.find { it.name.equals(name, ignoreCase = true) }
        override suspend fun isInternal(id: String) =
            configs.find { it.id == id }?.internal == true
        override suspend fun insert(config: McpServerConfig) {}
        override suspend fun update(config: McpServerConfig) {}
        override suspend fun updateStatus(id: String, status: String) {}
        override suspend fun delete(id: String) {}
        override suspend fun deleteAll() {}
    }

    private class ConfigurableProcessManager(
        private val clientProvider: (String) -> McpProtocolClient?
    ) : McpProcessManager {
        private val stopped = McpProcessStatus("x", state = McpServerState.STOPPED)
        override suspend fun startServer(configId: String) = stopped
        override suspend fun stopServer(configId: String) = stopped
        override suspend fun restartServer(configId: String) = stopped
        override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
        override fun getStatus(configId: String): McpProcessStatus? = null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools() = emptyList<McpAggregatedTool>()
        override fun getClient(configId: String) = clientProvider(configId)
    }

    private val schema: JsonElement = JsonObject(
        mapOf("type" to JsonPrimitive("object"))
    )

    private inner class SuccessClient(private val toolCount: Int) : McpProtocolClient {
        override suspend fun initialize() = McpInitializeResult(
            protocolVersion = "1.0",
            serverInfo = McpServerInfoDto("stub", "1.0")
        )
        override suspend fun sendRequest(method: String, params: JsonElement?) =
            JsonPrimitive("ok")
        override suspend fun sendNotification(method: String, params: JsonElement?) {}
        override suspend fun listTools() = (1..toolCount).map {
            McpToolInfo("tool_$it", "desc", schema)
        }
        override suspend fun callTool(name: String, arguments: JsonObject) =
            McpToolCallResponse(content = emptyList(), isError = false)
        override fun close() {}
    }

    private class FailingClient(private val error: String) : McpProtocolClient {
        override suspend fun initialize() = McpInitializeResult(
            protocolVersion = "1.0",
            serverInfo = McpServerInfoDto("stub", "1.0")
        )
        override suspend fun sendRequest(method: String, params: JsonElement?) =
            JsonPrimitive("ok")
        override suspend fun sendNotification(method: String, params: JsonElement?) {}
        override suspend fun listTools(): List<McpToolInfo> = throw RuntimeException(error)
        override suspend fun callTool(name: String, arguments: JsonObject) =
            McpToolCallResponse(content = emptyList(), isError = true)
        override fun close() {}
    }

    private fun buildBridge() = InternalMcpBridge(null, null)

    private val internalConfig = McpServerConfig(
        id = InternalMcpBridge.INTERNAL_SERVER_ID,
        name = InternalMcpBridge.INTERNAL_SERVER_NAME,
        type = "internal", internal = true
    )

    // ── Test: all servers ready → allReady: true ────────────────

    @Test
    fun `all servers ready returns allReady true`() = runBlocking {
        val ext1 = McpServerConfig(id = "kb-server", name = "Knowledge Base")
        val ext2 = McpServerConfig(id = "db-server", name = "Database MCP")
        val repo = InMemoryRepo(listOf(ext1, ext2, internalConfig))
        val pm = ConfigurableProcessManager { SuccessClient(3) }
        val checker = McpHealthChecker(pm, buildBridge(), repo)

        val response = checker.checkAll()

        assertTrue(response.allReady, "All servers ready → allReady: true")
        assertEquals(3, response.servers.size)
        response.servers.forEach { server ->
            assertTrue(server.ready)
            assertNull(server.error)
            assertTrue(server.toolCount >= 0)
        }
    }

    // ── Test: one server down → allReady: false ─────────────────

    @Test
    fun `one server down returns allReady false with error`() = runBlocking {
        val ext1 = McpServerConfig(id = "kb-server", name = "Knowledge Base")
        val ext2 = McpServerConfig(id = "db-server", name = "Database MCP")
        val repo = InMemoryRepo(listOf(ext1, ext2, internalConfig))
        val pm = ConfigurableProcessManager { id ->
            if (id == "db-server") null else SuccessClient(2)
        }
        val checker = McpHealthChecker(pm, buildBridge(), repo)

        val response = checker.checkAll()

        assertTrue(!response.allReady, "One server down → allReady: false")
        val down = response.servers.find { it.configId == "db-server" }
        assertNotNull(down)
        assertTrue(!down.ready)
        assertNotNull(down.error)
        assertTrue(down.error!!.contains("not running", ignoreCase = true))
    }

    // ── Test: no external servers → only internal ───────────────

    @Test
    fun `no external servers returns only internal server`() = runBlocking {
        val repo = InMemoryRepo(listOf(internalConfig))
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, buildBridge(), repo)

        val response = checker.checkAll()

        assertTrue(response.allReady)
        assertEquals(1, response.servers.size)
        val server = response.servers.first()
        assertEquals(InternalMcpBridge.INTERNAL_SERVER_ID, server.configId)
        assertTrue(server.ready)
        assertEquals("jira_internal", server.role)
    }

    // ── Test: role classification edge cases ─────────────────────

    @Test
    fun `classifyRole handles mixed case names`() {
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, buildBridge(), InMemoryRepo(emptyList()))

        val kbUpper = McpServerConfig(id = "s1", name = "KNOWLEDGE Base Server")
        assertEquals("knowledge_base", checker.classifyRole(kbUpper))

        val dbMixed = McpServerConfig(id = "s2", name = "MyDataBase")
        assertEquals("database", checker.classifyRole(dbMixed))

        val markitMixed = McpServerConfig(id = "s3", name = "MarkItDown Tool")
        assertEquals("markitdown", checker.classifyRole(markitMixed))
    }

    @Test
    fun `classifyRole returns other for empty name`() {
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, buildBridge(), InMemoryRepo(emptyList()))

        val empty = McpServerConfig(id = "s4", name = "")
        assertEquals("other", checker.classifyRole(empty))
    }

    @Test
    fun `classifyRole returns jira_internal for internal ID`() {
        val pm = ConfigurableProcessManager { null }
        val checker = McpHealthChecker(pm, buildBridge(), InMemoryRepo(emptyList()))

        val internal = McpServerConfig(
            id = InternalMcpBridge.INTERNAL_SERVER_ID,
            name = "anything"
        )
        assertEquals("jira_internal", checker.classifyRole(internal))
    }

    // ── Test: ping exception yields correct error ───────────────

    @Test
    fun `ping exception returns ready false with error message`() = runBlocking {
        val ext = McpServerConfig(id = "bad-server", name = "Bad Server")
        val repo = InMemoryRepo(listOf(ext, internalConfig))
        val pm = ConfigurableProcessManager { id ->
            if (id == "bad-server") FailingClient("Connection refused")
            else null
        }
        val checker = McpHealthChecker(pm, buildBridge(), repo)

        val response = checker.checkAll()

        val bad = response.servers.find { it.configId == "bad-server" }
        assertNotNull(bad)
        assertTrue(!bad.ready)
        assertEquals("Connection refused", bad.error)
    }

    // ── Test: disabled/marker servers are excluded ───────────────

    @Test
    fun `disabled and marker servers are excluded`() = runBlocking {
        val active = McpServerConfig(id = "active", name = "Active Server")
        val disabled = McpServerConfig(id = "dis", name = "Disabled", disabled = true)
        val marker = McpServerConfig(id = "mark", name = "Marker", type = "marker")
        val repo = InMemoryRepo(listOf(active, disabled, marker, internalConfig))
        val pm = ConfigurableProcessManager { SuccessClient(1) }
        val checker = McpHealthChecker(pm, buildBridge(), repo)

        val response = checker.checkAll()

        // Only active + internal should be present
        assertEquals(2, response.servers.size)
        val ids = response.servers.map { it.configId }.toSet()
        assertTrue("active" in ids)
        assertTrue(InternalMcpBridge.INTERNAL_SERVER_ID in ids)
        assertTrue("dis" !in ids)
        assertTrue("mark" !in ids)
    }
}
