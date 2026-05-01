package com.assistant.server.document

import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit test for DocumentAggregatorImpl: rejects unanalyzed tickets.
 * Validates: Requirement 1.1 — ticket must have deep analysis.
 */
class DocumentAggregatorImplTest {

    private val stubVectorStore = object : VectorStore {
        override suspend fun saveChunk(chunk: AttachmentChunk) = true
        override suspend fun existsByAttachmentId(attachmentId: String) = false
        override suspend fun search(queryEmbedding: FloatArray, topK: Int) = emptyList<AttachmentChunk>()
        override suspend fun search(queryEmbedding: FloatArray, topK: Int, chunkType: String?) = emptyList<AttachmentChunk>()
        override suspend fun deleteByTicketId(ticketId: String) = true
        override suspend fun deleteByProjectKey(projectKey: String, chunkType: String?) = true
        override suspend fun findByTicketId(ticketId: String) = emptyList<AttachmentChunk>()
    }

    private val stubEmbedding = object : EmbeddingService {
        override suspend fun embed(text: String): FloatArray? = null
    }

    private fun kbRepoReturning(record: KBRecord?) = object : KBRepository {
        override suspend fun findByTicketId(ticketId: String) = record
        override suspend fun save(record: KBRecord) = true
        override suspend fun overwrite(record: KBRecord) = true
        override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
        override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
    }

    private fun unanalyzedRecord() = KBRecord(
        ticketId = "NET-999",
        requirementSummary = "Some ticket",
        evolutionHistory = emptyList(),
        scrumPoints = 0.0,
        confidenceScore = 0.0,
        rationale = "",
        similarTicketRefs = emptyList(),
        timestamp = "2024-01-01T00:00:00Z",
        businessSummary = ""  // blank → no deep analysis
    )

    @Test
    fun `rejects ticket with no deep analysis`(): Unit = runBlocking {
        val aggregator = DocumentAggregatorImpl(
            kbRepository = kbRepoReturning(unanalyzedRecord()),
            vectorStore = stubVectorStore,
            embeddingService = stubEmbedding
        )
        val ex = assertThrows<IllegalArgumentException> {
            aggregator.aggregate("NET-999")
        }
        assert(ex.message!!.contains("must be analyzed"))
    }

    @Test
    fun `rejects ticket not found in KB`(): Unit = runBlocking {
        val aggregator = DocumentAggregatorImpl(
            kbRepository = kbRepoReturning(null),
            vectorStore = stubVectorStore,
            embeddingService = stubEmbedding
        )
        assertThrows<IllegalStateException> {
            aggregator.aggregate("NET-MISSING")
        }
    }
}
