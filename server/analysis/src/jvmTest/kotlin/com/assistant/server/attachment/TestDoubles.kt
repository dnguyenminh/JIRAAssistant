package com.assistant.server.attachment

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.McpProtocolClient
import com.assistant.mcp.models.*
import com.assistant.scan.ScanLogEntry
import com.assistant.scan.ScanLogRepository
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Fake AttachmentDownloader that records calls. */
class FakeDownloader : AttachmentDownloader {
    var shouldSucceed = true
    var lastUrl: String? = null
    var lastAuth: String? = null

    override suspend fun download(contentUrl: String, destPath: String, authHeader: String): Boolean {
        lastUrl = contentUrl
        lastAuth = authHeader
        return shouldSucceed
    }
}

/** Fake EmbeddingService returning a configurable vector. */
class FakeEmbeddingService : EmbeddingService {
    var nextEmbedding: FloatArray? = floatArrayOf(0.1f, 0.2f, 0.3f)
    var lastText: String? = null
    var embedCallCount = 0
    /** When set, embed() throws on the Nth call (1-based). Resets after throwing. */
    var failOnCallNumber: Int? = null

    override suspend fun embed(text: String): FloatArray? {
        embedCallCount++
        lastText = text
        if (failOnCallNumber == embedCallCount) {
            failOnCallNumber = null
            throw RuntimeException("Simulated embed failure")
        }
        return nextEmbedding
    }
}

/** In-memory VectorStore for testing. */
class FakeVectorStore : VectorStore {
    val chunks = mutableListOf<AttachmentChunk>()
    val existingAttachmentIds = mutableSetOf<String>()

    override suspend fun saveChunk(chunk: AttachmentChunk): Boolean {
        chunks.add(chunk)
        return true
    }

    override suspend fun existsByAttachmentId(attachmentId: String): Boolean =
        attachmentId in existingAttachmentIds

    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<AttachmentChunk> =
        search(queryEmbedding, topK, chunkType = null)

    override suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<AttachmentChunk> {
        val filtered = if (chunkType != null) chunks.filter { it.chunkType == chunkType } else chunks
        return filtered.sortedByDescending {
            CosineSimilarity.compute(queryEmbedding, it.embedding.toFloatArray())
        }.take(topK)
    }

    override suspend fun deleteByTicketId(ticketId: String): Boolean {
        chunks.removeAll { it.ticketId == ticketId }
        return true
    }

    override suspend fun deleteByProjectKey(
        projectKey: String,
        chunkType: String?
    ): Boolean {
        chunks.removeAll { chunk ->
            chunk.ticketId.startsWith(projectKey) &&
                (chunkType == null || chunk.chunkType == chunkType)
        }
        return true
    }

    override suspend fun findByTicketId(ticketId: String): List<AttachmentChunk> =
        chunks.filter { it.ticketId == ticketId }
}


/** Fake ScanLogRepository that collects entries. */
class FakeScanLogRepository : ScanLogRepository {
    val entries = mutableListOf<ScanLogEntry>()

    override suspend fun addEntry(entry: ScanLogEntry) {
        entries.add(entry)
    }

    override suspend fun getByProjectKey(projectKey: String, limit: Long): List<ScanLogEntry> =
        entries.filter { it.projectKey == projectKey }.take(limit.toInt())

    override suspend fun getByProjectKeyPaged(projectKey: String, limit: Long, offset: Long): List<ScanLogEntry> =
        entries.filter { it.projectKey == projectKey }.drop(offset.toInt()).take(limit.toInt())

    override suspend fun countByProjectKey(projectKey: String): Long =
        entries.count { it.projectKey == projectKey }.toLong()

    override suspend fun deleteByProjectKey(projectKey: String) {
        entries.removeAll { it.projectKey == projectKey }
    }
}

/** Fake McpProtocolClient for markitdown tool calls. */
class FakeMcpProtocolClient : McpProtocolClient {
    var markdownResult: String? = "# Converted markdown"

    override suspend fun initialize(): McpInitializeResult =
        McpInitializeResult("2024-11-05", McpServerInfoDto("fake", "1.0"))

    override suspend fun sendRequest(method: String, params: JsonElement?): JsonElement =
        JsonObject(emptyMap())

    override suspend fun sendNotification(method: String, params: JsonElement?) {}

    override suspend fun listTools(): List<McpToolInfo> = emptyList()

    override suspend fun callTool(name: String, arguments: JsonObject): McpToolCallResponse =
        McpToolCallResponse(
            content = listOfNotNull(markdownResult?.let { McpContent(type = "text", text = it) })
        )

    override fun close() {}
}

/** Fake McpProcessManager with configurable markitdown status. */
class FakeMcpProcessManager : McpProcessManager {
    var markitdownRunning = false
    val fakeClient = FakeMcpProtocolClient()

    override suspend fun startServer(configId: String) = status(configId)
    override suspend fun stopServer(configId: String) = status(configId)
    override suspend fun restartServer(configId: String) = status(configId)
    override fun getRunningServers(): Map<String, McpProcessStatus> = emptyMap()

    override fun getStatus(configId: String): McpProcessStatus? {
        if (configId == "markitdown" && markitdownRunning) {
            return McpProcessStatus(configId = "markitdown", state = McpServerState.RUNNING)
        }
        return McpProcessStatus(configId = configId, state = McpServerState.STOPPED)
    }

    override suspend fun startAllEnabled() {}
    override suspend fun stopAll() {}
    override fun getActiveTools(): List<McpAggregatedTool> = emptyList()
    override fun getClient(configId: String): McpProtocolClient? =
        if (configId == "markitdown" && markitdownRunning) fakeClient else null

    private fun status(id: String) = McpProcessStatus(configId = id, state = McpServerState.STOPPED)
}


// --- Minimal fakes for ChatService integration test ---

/** Fake AIAgent for attachment integration tests. */
class FakeAIAgentForAttachment : com.assistant.ai.AIAgent {
    override suspend fun analyze(prompt: String, context: com.assistant.ai.AIContext?): com.assistant.ai.AIResult =
        com.assistant.ai.AIResult.Success("ok")
    override fun getAgentName(): String = "FakeAttachmentAgent"
}

/** Fake KBRepository for attachment integration tests. */
class FakeKBRepoForAttachment : com.assistant.kb.KBRepository {
    override suspend fun findByTicketId(ticketId: String): com.assistant.kb.KBRecord? = null
    override suspend fun save(record: com.assistant.kb.KBRecord): Boolean = true
    override suspend fun overwrite(record: com.assistant.kb.KBRecord): Boolean = true
    override suspend fun saveGraphData(projectKey: String, graph: com.assistant.domain.NetworkGraph): Boolean = true
    override suspend fun getGraphData(projectKey: String): com.assistant.domain.NetworkGraph? = null
}

/** Fake GraphEngine for attachment integration tests. */
class FakeGraphEngineForAttachment : com.assistant.graph.GraphEngine {
    override fun computeLayout(
        graph: com.assistant.domain.NetworkGraph, width: Double, height: Double
    ): com.assistant.graph.GraphLayout =
        com.assistant.graph.GraphLayout(emptyMap(), com.assistant.graph.Bounds(width, height))

    override fun detectClusters(graph: com.assistant.domain.NetworkGraph): List<com.assistant.graph.Cluster> =
        emptyList()
}
