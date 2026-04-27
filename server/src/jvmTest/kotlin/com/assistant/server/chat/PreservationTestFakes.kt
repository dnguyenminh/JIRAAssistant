package com.assistant.server.chat

import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** McpProcessManager that tracks getClient calls and returns a stub client. */
class TrackingClientManager(
    private val toolResult: String
) : McpProcessManager {
    var getClientCalled = false
    var getClientServerId: String? = null
    private val stopped = McpProcessStatus("x", state = McpServerState.STOPPED)
    override suspend fun startServer(id: String) = stopped
    override suspend fun stopServer(id: String) = stopped
    override suspend fun restartServer(id: String) = stopped
    override fun getRunningServers() = emptyMap<String, McpProcessStatus>()
    override fun getStatus(id: String): McpProcessStatus? = null
    override suspend fun startAllEnabled() {}
    override suspend fun stopAll() {}
    override fun getActiveTools() = emptyList<McpAggregatedTool>()
    override fun getClient(configId: String): McpProtocolClient? {
        getClientCalled = true
        getClientServerId = configId
        return StubProtocolClient(toolResult)
    }
}

/** Stub McpProtocolClient returning a fixed text response. */
class StubProtocolClient(private val result: String) : McpProtocolClient {
    override suspend fun initialize() =
        McpInitializeResult("1.0", McpServerInfoDto("stub"))
    override suspend fun sendRequest(method: String, params: JsonElement?) =
        JsonObject(emptyMap())
    override suspend fun sendNotification(method: String, params: JsonElement?) {}
    override suspend fun listTools() = emptyList<McpToolInfo>()
    override suspend fun callTool(name: String, arguments: JsonObject) =
        McpToolCallResponse(content = listOf(McpContent(type = "text", text = result)))
    override fun close() {}
}

/** Stub EmbeddingService for LocalKBToolExecutor tests. */
object PreservationStubEmbedding : EmbeddingService {
    override suspend fun embed(text: String) = FloatArray(3) { 0.1f }
}

/** Stub VectorStore for LocalKBToolExecutor tests. */
object PreservationStubVectorStore : VectorStore {
    override suspend fun saveChunk(chunk: AttachmentChunk) = true
    override suspend fun existsByAttachmentId(attachmentId: String) = false
    override suspend fun search(queryEmbedding: FloatArray, topK: Int) =
        emptyList<AttachmentChunk>()
    override suspend fun search(queryEmbedding: FloatArray, topK: Int, chunkType: String?) =
        emptyList<AttachmentChunk>()
    override suspend fun deleteByTicketId(ticketId: String) = true
    override suspend fun deleteByProjectKey(projectKey: String, chunkType: String?) = true
    override suspend fun findByTicketId(ticketId: String) = emptyList<AttachmentChunk>()
}

/** Stub KBRepository for LocalKBToolExecutor tests. */
object PreservationStubKBRepo : KBRepository {
    override suspend fun findByTicketId(ticketId: String): KBRecord? = null
    override suspend fun save(record: KBRecord) = true
    override suspend fun overwrite(record: KBRecord) = true
    override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
    override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
}
