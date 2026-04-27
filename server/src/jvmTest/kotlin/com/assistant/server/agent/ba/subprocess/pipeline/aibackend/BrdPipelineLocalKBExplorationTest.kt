package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus
import com.assistant.server.agent.ba.subprocess.pipeline.AiBackendPipelineStrategy
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.chat.LocalKBToolExecutor
import com.assistant.server.mcp.internal.InternalMcpBridge
import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Bug Condition Exploration Test — Local KB Tools Missing
 *
 * Property 1: When Local KB is enabled, collectToolDescriptors()
 * SHALL include KB-compatible tool descriptors AND
 * PhaseToolFilter.hasKbTools() SHALL return true AND
 * ToolExecutionBridge SHALL route Local KB tool calls.
 *
 * EXPECTED: These tests FAIL on unfixed code — failure confirms
 * the bug exists (Local KB tools absent from pipeline).
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
 */
class BrdPipelineLocalKBExplorationTest {

    // ── Case A: collectToolDescriptors() missing KB tools ───────
    @Test
    fun `Case A - collectToolDescriptors includes kb_search tool when Local KB enabled`() {
        val mgr = fakeMcpManager(sampleExternalTools())
        val bridge = InternalMcpBridge(null, null) // no internal executor
        val strategy = AiBackendPipelineStrategy(
            subprocessProxy = noOpProxy(),
            cliBackendResolver = fakeResolver(),
            mcpProcessManager = mgr,
            internalMcpBridge = bridge,
            settingsRepository = fakeSettingsRepo(),
            localKBToolExecutor = fakeLocalKBExecutor()
        )

        val method = AiBackendPipelineStrategy::class.java
            .getDeclaredMethod("collectToolDescriptors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tools = method.invoke(strategy) as List<ToolDescriptor>

        val hasKbSearch = tools.any {
            it.name.lowercase().contains("kb_search")
        }
        assertTrue(
            hasKbSearch,
            "collectToolDescriptors() should include at least one " +
                "tool with 'kb_search' in name when Local KB is " +
                "enabled. Got ${tools.size} tools: " +
                tools.map { it.name }
        )
    }

    // ── Case B: ToolExecutionBridge cannot route Local KB ────────
    @Test
    fun `Case B - ToolExecutionBridge routes local-knowledge-base tool successfully`() {
        runBlocking {
            val mgr = fakeMcpManager(sampleExternalTools())
            val bridge = InternalMcpBridge(null, null)
            val exec = ToolExecutionBridge(
                noOpProxy(), noOpReporter(), mgr, bridge,
                fakeLocalKBExecutor()
            )

            val request = ToolRequest(
                tool = "mcp_local-knowledge-base_kb_search_knowledge",
                params = mapOf("query" to "test query")
            )
            val result = exec.execute(request)

            assertTrue(
                result.rawResponse.success,
                "ToolExecutionBridge should route Local KB tool " +
                    "call successfully. Got error: " +
                    "'${result.rawResponse.error}'"
            )
        }
    }

    // ── Case C: hasKbTools() returns false for pipeline tools ────
    @Test
    fun `Case C - hasKbTools returns true for collectToolDescriptors output`() {
        val mgr = fakeMcpManager(sampleExternalTools())
        val bridge = InternalMcpBridge(null, null)
        val strategy = AiBackendPipelineStrategy(
            subprocessProxy = noOpProxy(),
            cliBackendResolver = fakeResolver(),
            mcpProcessManager = mgr,
            internalMcpBridge = bridge,
            settingsRepository = fakeSettingsRepo(),
            localKBToolExecutor = fakeLocalKBExecutor()
        )

        val method = AiBackendPipelineStrategy::class.java
            .getDeclaredMethod("collectToolDescriptors")
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tools = method.invoke(strategy) as List<ToolDescriptor>

        val filter = PhaseToolFilter()
        val result = filter.hasKbTools(tools)

        assertTrue(
            result,
            "hasKbTools() should return true when Local KB is " +
                "enabled. Tool names: ${tools.map { it.name }}"
        )
    }

    // ── Sample external tools (realistic, no KB tools) ──────────

    private fun sampleExternalTools(): List<McpAggregatedTool> =
        listOf(
            tool("markitdown", "convert_to_markdown", "Convert"),
            tool("fetch", "fetch", "Fetch URL"),
            tool("jira", "get_issue", "Get Jira issue"),
            tool("jira", "search_jira", "Search Jira"),
            tool("drawio", "create_diagram", "Create diagram")
        )

    private fun tool(
        server: String, name: String, desc: String
    ) = McpAggregatedTool(
        server, server, name, desc, JsonObject(emptyMap())
    )

    // ── Fakes ───────────────────────────────────────────────────

    private fun fakeMcpManager(
        tools: List<McpAggregatedTool>
    ): McpProcessManager = object : McpProcessManager {
        override suspend fun startServer(id: String) = stub()
        override suspend fun stopServer(id: String) = stub()
        override suspend fun restartServer(id: String) = stub()
        override fun getRunningServers() =
            emptyMap<String, McpProcessStatus>()
        override fun getStatus(id: String): McpProcessStatus? = null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools() = tools
        override fun getClient(id: String): McpProtocolClient? = null
        private fun stub(): Nothing = error("not needed")
    }

    private fun noOpProxy(): SubprocessProxy = object : SubprocessProxy {
        override suspend fun handleToolCallRequest(
            r: ToolCallRequest
        ) = ToolCallResponse(r.id, true, "ok", "")
        override fun getAvailableToolDescriptors() =
            emptyList<ToolDescriptor>()
        override fun buildToolListMessage() = ""
        override fun buildToolsUpdatedMessage() = ""
    }

    private fun noOpReporter(): ProgressReporter =
        object : ProgressReporter {
            override suspend fun reportPhase(
                n: String, i: Int, t: Int
            ) {}
            override suspend fun reportProgress(p: Int, m: String) {}
            override suspend fun reportToolCall(n: String, s: String) {}
        }

    private fun fakeResolver(): com.assistant.server.agent.ba.subprocess.CliBackendResolver {
        val repo = object : com.assistant.settings.SettingsRepository {
            override suspend fun getAll() = emptyMap<String, String>()
            override suspend fun get(key: String): String? = null
            override suspend fun put(key: String, value: String) {}
            override suspend fun putAll(s: Map<String, String>) {}
        }
        return com.assistant.server.agent.ba.subprocess.CliBackendResolver(repo)
    }

    /** SettingsRepository that reports Local KB as enabled. */
    private fun fakeSettingsRepo(): SettingsRepository =
        object : SettingsRepository {
            override suspend fun getAll() = emptyMap<String, String>()
            override suspend fun get(key: String): String? =
                if (key == "local_kb_tool_enabled") "true" else null
            override suspend fun put(key: String, value: String) {}
            override suspend fun putAll(s: Map<String, String>) {}
        }

    /** Minimal LocalKBToolExecutor with no-op dependencies. */
    private fun fakeLocalKBExecutor(): LocalKBToolExecutor {
        val embedding = object : EmbeddingService {
            override suspend fun embed(text: String): FloatArray? = null
        }
        val vectorStore = object : VectorStore {
            override suspend fun saveChunk(chunk: AttachmentChunk) = true
            override suspend fun existsByAttachmentId(id: String) = false
            override suspend fun search(
                queryEmbedding: FloatArray, topK: Int
            ) = emptyList<AttachmentChunk>()
            override suspend fun search(
                queryEmbedding: FloatArray, topK: Int, chunkType: String?
            ) = emptyList<AttachmentChunk>()
            override suspend fun deleteByTicketId(id: String) = true
            override suspend fun deleteByProjectKey(
                projectKey: String, chunkType: String?
            ) = true
            override suspend fun findByTicketId(id: String) =
                emptyList<AttachmentChunk>()
        }
        val kbRepo = object : KBRepository {
            override suspend fun findByTicketId(id: String): KBRecord? = null
            override suspend fun save(record: KBRecord) = true
            override suspend fun overwrite(record: KBRecord) = true
            override suspend fun saveGraphData(
                projectKey: String, graph: com.assistant.domain.NetworkGraph
            ) = true
            override suspend fun getGraphData(
                projectKey: String
            ): com.assistant.domain.NetworkGraph? = null
        }
        return LocalKBToolExecutor(embedding, vectorStore, kbRepo)
    }
}
