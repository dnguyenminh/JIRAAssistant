package com.assistant.server.document.collection

import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk

/**
 * Fake [VectorStore] that returns a configurable list of [AttachmentChunk]s.
 *
 * Supports duplicates (same attachmentId, chunkIndex) and multiple filenames
 * to test deduplication and grouping in [AttachmentContentCollector].
 *
 * @param chunks The chunks to return from [findByTicketId].
 */
class FakeVectorStore(
    private val chunks: List<AttachmentChunk> = emptyList()
) : VectorStore {

    override suspend fun saveChunk(chunk: AttachmentChunk): Boolean = true

    override suspend fun existsByAttachmentId(attachmentId: String): Boolean =
        chunks.any { it.attachmentId == attachmentId }

    override suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int
    ): List<AttachmentChunk> = emptyList()

    override suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<AttachmentChunk> = emptyList()

    override suspend fun deleteByTicketId(ticketId: String): Boolean = true

    override suspend fun deleteByProjectKey(
        projectKey: String,
        chunkType: String?
    ): Boolean = true

    override suspend fun findByTicketId(
        ticketId: String
    ): List<AttachmentChunk> = chunks.filter { it.ticketId == ticketId }
}
