package com.assistant.server.chat

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import com.assistant.domain.NetworkGraph
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.graph.GraphLayout
import com.assistant.graph.Bounds
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk

/** AI agent that captures the last prompt for assertion. */
class CapturingAIAgent : AIAgent {
    var nextResponse: AIResult = AIResult.Success("default")
    var lastPrompt: String? = null

    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        lastPrompt = prompt
        return nextResponse
    }

    override fun getAgentName(): String = "CapturingAgent"
}

/** Fake EmbeddingService returning a fixed embedding or null. */
class FakeEmbeddingService(private val result: FloatArray?) : EmbeddingService {
    override suspend fun embed(text: String): FloatArray? = result
}

/** Fake VectorStore returning pre-configured chunks on search. */
class FakeVectorStore(private val chunks: List<AttachmentChunk>) : VectorStore {
    override suspend fun saveChunk(chunk: AttachmentChunk) = true
    override suspend fun existsByAttachmentId(attachmentId: String) = false
    override suspend fun deleteByTicketId(ticketId: String) = true
    override suspend fun findByTicketId(ticketId: String) = emptyList<AttachmentChunk>()

    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<AttachmentChunk> =
        search(queryEmbedding, topK, chunkType = null)

    override suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<AttachmentChunk> {
        val filtered = if (chunkType != null) chunks.filter { it.chunkType == chunkType } else chunks
        return filtered.take(topK)
    }

    override suspend fun deleteByProjectKey(projectKey: String, chunkType: String?) = true
}

/** Stub KBRepository with no data. */
class StubKBRepository : KBRepository {
    override suspend fun findByTicketId(ticketId: String): KBRecord? = null
    override suspend fun save(record: KBRecord) = true
    override suspend fun overwrite(record: KBRecord) = true
    override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
    override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
}

/** Stub GraphEngine with no clusters. */
class StubGraphEngine : GraphEngine {
    override fun computeLayout(graph: NetworkGraph, width: Double, height: Double) =
        GraphLayout(emptyMap(), Bounds(width, height))
    override fun detectClusters(graph: NetworkGraph) = emptyList<Cluster>()
}
