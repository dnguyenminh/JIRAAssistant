package com.assistant.server.attachment

import com.assistant.db.JiraDatabase
import com.assistant.server.attachment.models.AttachmentChunk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLDelight-backed VectorStore with brute-force cosine similarity search.
 * Requirements: 22.11, 12.3, 13.3, 15.1, 16.5
 */
class VectorStoreImpl(
    private val database: JiraDatabase
) : VectorStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val queries get() = database.knowledgeBaseQueries

    override suspend fun saveChunk(chunk: AttachmentChunk): Boolean = try {
        val embeddingJson = json.encodeToString(chunk.embedding)
        queries.insertAttachmentChunk(
            ticket_id = chunk.ticketId,
            attachment_id = chunk.attachmentId,
            filename = chunk.filename,
            chunk_index = chunk.chunkIndex.toLong(),
            chunk_text = chunk.chunkText,
            embedding = embeddingJson,
            created_at = chunk.createdAt,
            chunk_type = chunk.chunkType
        )
        true
    } catch (e: Exception) {
        println("[VectorStore] saveChunk failed: ${e.message}")
        false
    }

    override suspend fun existsByAttachmentId(attachmentId: String): Boolean = try {
        queries.existsAttachmentChunks(attachmentId).executeAsOne() > 0
    } catch (_: Exception) { false }

    override suspend fun search(queryEmbedding: FloatArray, topK: Int): List<AttachmentChunk> =
        search(queryEmbedding, topK, chunkType = null)

    override suspend fun search(
        queryEmbedding: FloatArray,
        topK: Int,
        chunkType: String?
    ): List<AttachmentChunk> = try {
        val allRows = if (chunkType != null) {
            queries.getAllChunksByType(chunkType).executeAsList()
        } else {
            queries.getAllChunks().executeAsList()
        }
        allRows.mapNotNull { row -> parseAndScore(row, queryEmbedding) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    } catch (e: Exception) {
        println("[VectorStore] search failed: ${e.message}")
        emptyList()
    }

    override suspend fun deleteByTicketId(ticketId: String): Boolean = try {
        queries.deleteChunksByTicketId(ticketId)
        true
    } catch (_: Exception) { false }

    override suspend fun deleteByProjectKey(
        projectKey: String,
        chunkType: String?
    ): Boolean = try {
        if (chunkType != null) {
            queries.deleteChunksByProjectKeyAndType(projectKey, chunkType)
        } else {
            queries.deleteChunksByProjectKey(projectKey)
        }
        true
    } catch (e: Exception) {
        println("[VectorStore] deleteByProjectKey failed: ${e.message}")
        false
    }

    override suspend fun findByTicketId(ticketId: String): List<AttachmentChunk> = try {
        queries.findChunksByTicketId(ticketId).executeAsList().map { row ->
            rowToChunk(row)
        }
    } catch (e: Exception) {
        println("[VectorStore] findByTicketId failed: ${e.message}")
        emptyList()
    }

    /** Parse a DB row into AttachmentChunk + cosine similarity score. */
    private fun parseAndScore(
        row: com.assistant.db.Attachment_chunks,
        queryEmbedding: FloatArray
    ): Pair<AttachmentChunk, Float>? = try {
        val emb = json.decodeFromString<List<Float>>(row.embedding)
        val embArray = FloatArray(emb.size) { emb[it] }
        val score = CosineSimilarity.compute(queryEmbedding, embArray)
        rowToChunk(row, emb) to score
    } catch (_: Exception) { null }

    private fun rowToChunk(
        row: com.assistant.db.Attachment_chunks,
        emb: List<Float>? = null
    ): AttachmentChunk {
        val embedding = emb ?: json.decodeFromString<List<Float>>(row.embedding)
        return AttachmentChunk(
            id = row.id,
            ticketId = row.ticket_id,
            attachmentId = row.attachment_id,
            filename = row.filename,
            chunkIndex = row.chunk_index.toInt(),
            chunkText = row.chunk_text,
            embedding = embedding,
            createdAt = row.created_at,
            chunkType = row.chunk_type
        )
    }
}
