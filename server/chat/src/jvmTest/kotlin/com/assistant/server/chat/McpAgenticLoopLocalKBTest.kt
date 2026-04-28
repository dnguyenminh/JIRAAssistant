package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatResponse
import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.mcp.models.McpProcessStatus
import com.assistant.mcp.models.McpServerState
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for McpAgenticLoop local KB routing.
 * Validates: AC 19.67 (local routing, no getClient), AC 19.70 (agentic loop).
 */
class McpAgenticLoopLocalKBTest {

    private fun localKBToolCallResponse(tool: String = "search_knowledge"): String =
        """Here is data: {"mcpToolCall":{"serverId":"local-knowledge-base","toolName":"$tool","arguments":{}}}"""

    private fun externalToolCallResponse(): String =
        """Result: {"mcpToolCall":{"serverId":"jira","toolName":"search_issues","arguments":{}}}"""

    private fun simpleResponse(text: String) = AIResult.Success(text)
    private fun toResponse(r: AIResult, len: Int) = ChatResponse(reply = (r as AIResult.Success).response)

    // --- Stubs ---

    private val stubEmbedding = object : EmbeddingService {
        override suspend fun embed(text: String) = FloatArray(3) { 0.1f }
    }
    private val stubVectorStore = object : VectorStore {
        override suspend fun saveChunk(chunk: AttachmentChunk) = true
        override suspend fun existsByAttachmentId(attachmentId: String) = false
        override suspend fun search(q: FloatArray, topK: Int) = emptyList<AttachmentChunk>()
        override suspend fun search(q: FloatArray, topK: Int, chunkType: String?) = emptyList<AttachmentChunk>()
        override suspend fun deleteByTicketId(ticketId: String) = true
        override suspend fun deleteByProjectKey(pk: String, ct: String?) = true
        override suspend fun findByTicketId(ticketId: String) = emptyList<AttachmentChunk>()
    }
    private val stubKBRepo = object : KBRepository {
        override suspend fun findByTicketId(ticketId: String): KBRecord? = null
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(pk: String, g: NetworkGraph) = true
        override suspend fun getGraphData(pk: String): NetworkGraph? = null
    }

    /** McpProcessManager that tracks getClient calls — should NOT be called for local KB. */
    private class TrackingMcpManager : McpProcessManager {
        var getClientCalled = false
        var getClientServerId: String? = null
        private val dummyStatus = McpProcessStatus("x", state = McpServerState.STOPPED)
        override suspend fun startServer(id: String) = dummyStatus
        override suspend fun stopServer(id: String) = dummyStatus
        override suspend fun restartServer(id: String) = dummyStatus
        override fun getRunningServers(): Map<String, McpProcessStatus> = emptyMap()
        override fun getStatus(id: String): McpProcessStatus? = null
        override suspend fun startAllEnabled() {}
        override suspend fun stopAll() {}
        override fun getActiveTools(): List<McpAggregatedTool> = emptyList()
        override fun getClient(configId: String): McpProtocolClient? {
            getClientCalled = true
            getClientServerId = configId
            return null // server not running
        }
    }

    // --- Tests ---

    /** AC 19.67: serverId == "local-knowledge-base" → LocalKBToolExecutor, NO getClient(). */
    @Test
    fun `local KB tool call routes to LocalKBToolExecutor not McpProcessManager`() = runBlocking {
        val manager = TrackingMcpManager()
        val executor = LocalKBToolExecutor(stubEmbedding, stubVectorStore, stubKBRepo)
        var callCount = 0
        val response = McpAgenticLoop.execute(
            initialPrompt = "search tickets",
            mcpProcessManager = manager,
            callAI = { if (callCount++ == 0) simpleResponse(localKBToolCallResponse()) else simpleResponse("Done with results") },
            toResponse = ::toResponse,
            localKBExecutor = executor
        )
        assertFalse(manager.getClientCalled, "getClient() must NOT be called for local-knowledge-base")
        assertTrue(response.reply.contains("Done") || response.reply.contains("results"))
    }

    /** AC 19.70: serverId != "local-knowledge-base" → routes to McpProcessManager. */
    @Test
    fun `external tool call routes to McpProcessManager getClient`() = runBlocking {
        val manager = TrackingMcpManager()
        val executor = LocalKBToolExecutor(stubEmbedding, stubVectorStore, stubKBRepo)
        var callCount = 0
        val response = McpAgenticLoop.execute(
            initialPrompt = "search jira",
            mcpProcessManager = manager,
            callAI = { if (callCount++ == 0) simpleResponse(externalToolCallResponse()) else simpleResponse("Jira results") },
            toResponse = ::toResponse,
            localKBExecutor = executor
        )
        assertTrue(manager.getClientCalled, "getClient() MUST be called for external serverId")
        assertEquals("jira", manager.getClientServerId)
    }

    /** AC 19.69: Local tool exception → graceful error, loop continues. */
    @Test
    fun `local KB tool exception returns graceful error and loop continues`() = runBlocking {
        val manager = TrackingMcpManager()
        // Use real executor with embedding that throws
        val failEmbed = object : EmbeddingService {
            override suspend fun embed(text: String): FloatArray? = throw RuntimeException("embed crashed")
        }
        val executor = LocalKBToolExecutor(failEmbed, stubVectorStore, stubKBRepo)
        var callCount = 0
        val response = McpAgenticLoop.execute(
            initialPrompt = "search knowledge",
            mcpProcessManager = manager,
            callAI = { if (callCount++ == 0) simpleResponse(localKBToolCallResponse()) else simpleResponse("Recovered after error") },
            toResponse = ::toResponse,
            localKBExecutor = executor
        )
        assertFalse(manager.getClientCalled)
        assertTrue(response.reply.contains("Recovered"), "Loop should continue after local tool error")
    }
}
