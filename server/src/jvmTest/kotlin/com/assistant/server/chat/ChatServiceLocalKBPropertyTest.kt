package com.assistant.server.chat

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus
import com.assistant.mcp.models.McpServerState
import com.assistant.settings.SettingsRepository
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.property.PropTestConfig
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Property tests for ChatServiceImpl Local KB tool registration and context independence.
 *
 * Feature: ai-chat-sidebar, Property 1: Tool registration reflects enabled state
 * Feature: ai-chat-sidebar, Property 6: buildKnowledgeContext independence
 */
@OptIn(ExperimentalKotest::class)
class ChatServiceLocalKBPropertyTest {

    private class FakeSettings(
        private val store: MutableMap<String, String> = mutableMapOf()
    ) : SettingsRepository {
        override suspend fun getAll() = store.toMap()
        override suspend fun get(key: String) = store[key]
        override suspend fun put(key: String, value: String) { store[key] = value }
        override suspend fun putAll(settings: Map<String, String>) { store.putAll(settings) }
    }

    /** Minimal McpProcessManager stub with no external tools. */
    private class EmptyMcpManager : McpProcessManager {
        private val dummy = McpProcessStatus("x", state = McpServerState.STOPPED)
        override suspend fun startServer(configId: String) = dummy
        override suspend fun stopServer(configId: String) = dummy
        override suspend fun restartServer(configId: String) = dummy
        override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
        override fun getStatus(configId: String): McpProcessStatus? = null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools() = emptyList<McpAggregatedTool>()
        override fun getClient(configId: String): McpProtocolClient? = null
    }

    private fun buildService(
        settingsRepo: SettingsRepository? = null,
        embSvc: FakeEmbeddingService? = null,
        vecStore: FakeVectorStore? = null,
        mcpMgr: McpProcessManager? = EmptyMcpManager()
    ) = ChatServiceImpl(
        aiAgentProvider = { CapturingAIAgent() },
        kbRepository = StubKBRepository(),
        graphEngine = StubGraphEngine(),
        settingsRepository = settingsRepo,
        embeddingService = embSvc,
        vectorStore = vecStore,
        mcpProcessManager = mcpMgr
    )

    // ── Property 1: Tool registration reflects enabled state ──

    /**
     * Property 1: For any boolean enabled state, buildMcpToolsContext()
     * includes 3 tool descriptions + priority guidance when enabled,
     * and excludes both when disabled.
     *
     * **Validates: Requirements 19.61, 19.71, 19.76**
     */
    @Test
    fun `Property 1 - enabled includes 3 tools and priority hint`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.boolean()) { enabled ->
                val settings = FakeSettings(
                    mutableMapOf("local_kb_tool_enabled" to enabled.toString())
                )
                val service = buildService(settingsRepo = settings)
                val ctx = service.buildMcpToolsContext()

                if (enabled) {
                    assertToolsPresent(ctx)
                    assertPriorityPresent(ctx)
                } else {
                    assertToolsAbsent(ctx)
                    assertPriorityAbsent(ctx)
                }
            }
        }
    }

    /**
     * Property 1 (edge): settingsRepository == null → disabled,
     * so tools and priority hint are absent.
     *
     * **Validates: Requirements 19.76**
     */
    @Test
    fun `Property 1 - null settings repo excludes tools`() {
        val service = buildService(settingsRepo = null)
        val ctx = service.buildMcpToolsContext()
        assertToolsAbsent(ctx)
        assertPriorityAbsent(ctx)
    }

    private fun assertToolsPresent(ctx: String) {
        val tools = listOf("search_knowledge", "get_ticket_info", "search_relationships")
        tools.forEach { assertTrue(ctx.contains(it), "Missing $it") }
    }

    private fun assertPriorityPresent(ctx: String) {
        assertTrue(ctx.contains("LOCAL KB TOOLS"), "Missing priority guidance")
    }

    private fun assertToolsAbsent(ctx: String) {
        assertFalse(ctx.contains("[MCP:local-knowledge-base]"), "Unexpected local KB tool")
    }

    private fun assertPriorityAbsent(ctx: String) {
        assertFalse(ctx.contains("LOCAL KB TOOLS"), "Unexpected priority guidance")
    }

    // ── Property 6: buildKnowledgeContext independence ──

    /**
     * Property 6: For any local_kb_tool_enabled state and any message,
     * buildKnowledgeContext() produces identical output — the auto-inject
     * mechanism is unaffected by Local KB Tool state.
     *
     * **Validates: Requirements 19.73**
     */
    @Test
    fun `Property 6 - buildKnowledgeContext same regardless of toggle`() {
        runBlocking {
            val arbMsg = Arb.string(1, 30, Codepoint.alphanumeric())
            val arbPk = Arb.string(2, 8, Codepoint.alphanumeric())
            val emb = FakeEmbeddingService(floatArrayOf(0.1f, 0.2f))
            val vs = FakeVectorStore(emptyList())

            checkAll(PropTestConfig(iterations = 100), arbMsg, arbPk) { msg, pk ->
                val svcOn = buildService(
                    FakeSettings(mutableMapOf("local_kb_tool_enabled" to "true")),
                    emb, vs
                )
                val svcOff = buildService(
                    FakeSettings(mutableMapOf("local_kb_tool_enabled" to "false")),
                    emb, vs
                )
                val outOn = svcOn.buildKnowledgeContext(pk, msg)
                val outOff = svcOff.buildKnowledgeContext(pk, msg)

                assertEquals(outOn, outOff,
                    "buildKnowledgeContext must be identical for pk=$pk")
            }
        }
    }
}
