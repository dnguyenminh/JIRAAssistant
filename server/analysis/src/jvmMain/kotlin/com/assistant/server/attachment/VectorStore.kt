package com.assistant.server.attachment

import com.assistant.server.attachment.models.AttachmentChunk

/**
 * Vector store for attachment chunk embeddings.
 * Requirements: 22.10, 22.11, 12.3, 13.3, 15.1, 16.5
 */
interface VectorStore {
    /** Save a chunk with its embedding. */
    suspend fun saveChunk(chunk: AttachmentChunk): Boolean

    /** Check if chunks already exist for an attachment. */
    suspend fun existsByAttachmentId(attachmentId: String): Boolean

    /** Semantic search: find top-K chunks nearest to query embedding. */
    suspend fun search(queryEmbedding: FloatArray, topK: Int = 5): List<AttachmentChunk>

    /** Semantic search with optional chunkType filter. null = search all types. */
    suspend fun search(queryEmbedding: FloatArray, topK: Int = 5, chunkType: String? = null): List<AttachmentChunk>

    /** Semantic search returning chunks with similarity scores. */
    suspend fun searchWithScores(queryEmbedding: FloatArray, topK: Int = 5, chunkType: String? = null): List<Pair<AttachmentChunk, Float>> =
        search(queryEmbedding, topK, chunkType).map { it to 1.0f }

    /** Delete all chunks for a ticket. */
    suspend fun deleteByTicketId(ticketId: String): Boolean

    /** Delete all chunks for a project, optionally filtered by chunkType. */
    suspend fun deleteByProjectKey(projectKey: String, chunkType: String? = null): Boolean

    /** Find all chunks for a specific ticket. */
    suspend fun findByTicketId(ticketId: String): List<AttachmentChunk>
}
