package com.assistant.server.indexing

import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import java.time.Instant

/**
 * Batches embedding requests (max 20 per batch) with retry and error handling.
 * When EmbeddingService is null/unavailable → skip + log warning, never throw.
 * Requirements: 16.1, 16.2, 16.3, 16.4
 */
class BatchEmbedder(
    private val embeddingService: EmbeddingService?,
    private val vectorStore: VectorStore
) {
    companion object {
        const val MAX_BATCH_SIZE = 20
    }

    /**
     * Embed and save a list of items in batches of up to 20.
     * Returns the number of successfully saved chunks.
     */
    suspend fun embedAndSaveAll(items: List<EmbedItem>): Int {
        if (embeddingService == null) {
            println("[BatchEmbedder] WARN: EmbeddingService unavailable, skipping")
            return 0
        }
        if (items.isEmpty()) return 0
        var saved = 0
        for (batch in items.chunked(MAX_BATCH_SIZE)) {
            saved += processBatch(batch)
        }
        return saved
    }

    private suspend fun processBatch(batch: List<EmbedItem>): Int {
        val results = embedBatch(batch)
        val failed = mutableListOf<EmbedItem>()
        var saved = 0
        for ((item, embedding) in batch.zip(results)) {
            if (embedding == null) { failed.add(item); continue }
            if (saveChunk(item, embedding)) saved++
        }
        if (failed.isNotEmpty()) saved += retryFailed(failed)
        return saved
    }

    private suspend fun embedBatch(batch: List<EmbedItem>): List<FloatArray?> {
        return batch.map { item -> embedSafe(item.text) }
    }

    private suspend fun embedSafe(text: String): FloatArray? {
        return try {
            embeddingService?.embed(text)
        } catch (e: Exception) {
            println("[BatchEmbedder] WARN: embed failed: ${e.message}")
            null
        }
    }

    private suspend fun retryFailed(items: List<EmbedItem>): Int {
        var saved = 0
        for (item in items) {
            val embedding = embedSafe(item.text) ?: continue
            if (saveChunk(item, embedding)) saved++
        }
        return saved
    }

    private suspend fun saveChunk(item: EmbedItem, embedding: FloatArray): Boolean {
        val chunk = AttachmentChunk(
            ticketId = item.ticketId,
            attachmentId = item.attachmentId,
            filename = item.filename,
            chunkIndex = 0,
            chunkText = item.text,
            embedding = embedding.toList(),
            createdAt = Instant.now().toString(),
            chunkType = item.chunkType
        )
        return try {
            vectorStore.saveChunk(chunk)
        } catch (e: Exception) {
            println("[BatchEmbedder] WARN: saveChunk failed: ${e.message}")
            false
        }
    }
}

/**
 * Represents a single item to be embedded and saved.
 */
data class EmbedItem(
    val text: String,
    val ticketId: String,
    val attachmentId: String,
    val chunkType: String,
    val filename: String = ticketId
)
