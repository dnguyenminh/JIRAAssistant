package com.assistant.server.document.collection

import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.document.extraction.TicketIdExtractor
import com.assistant.server.document.models.AttachmentCollectionResult
import org.slf4j.LoggerFactory

/**
 * Collects ALL attachment chunks for a ticket from VectorStore.
 *
 * - Uses [VectorStore.findByTicketId] (not semantic search)
 * - Groups chunks by filename, sorts by chunkIndex ascending
 * - Deduplicates by (attachmentId, chunkIndex) across tickets
 * - Extracts ticket IDs from chunk text for further traversal
 *
 * Requirements: 4.1, 4.3, 4.5, 4.6
 */
class AttachmentContentCollector(
    private val vectorStore: VectorStore,
    private val ticketIdExtractor: TicketIdExtractor
) {
    private val logger = LoggerFactory.getLogger(AttachmentContentCollector::class.java)

    /**
     * Fetch all attachment chunks for [ticketId] from VectorStore.
     *
     * @param ticketId The Jira ticket ID to collect attachments for.
     * @return [AttachmentCollectionResult] with deduplicated chunks and discovered ticket IDs.
     */
    suspend fun collectAll(ticketId: String): AttachmentCollectionResult {
        val rawChunks = vectorStore.findByTicketId(ticketId)

        if (rawChunks.isEmpty()) {
            logger.info("No attachment chunks found for ticket {}", ticketId)
            return AttachmentCollectionResult(chunks = emptyList(), discoveredTicketIds = emptyList())
        }

        val deduplicated = deduplicateChunks(rawChunks)
        val sorted = groupAndSort(deduplicated)
        val discoveredIds = extractTicketIds(sorted, excludeId = ticketId)

        logger.debug(
            "Collected {} attachment chunks for ticket {} ({} unique attachments, {} ticket refs)",
            sorted.size, ticketId, countUniqueAttachments(sorted), discoveredIds.size
        )

        return AttachmentCollectionResult(chunks = sorted, discoveredTicketIds = discoveredIds)
    }

    /**
     * Deduplicate chunks by (attachmentId, chunkIndex).
     * Keeps the first occurrence when the same attachment is referenced from multiple tickets.
     */
    private fun deduplicateChunks(chunks: List<AttachmentChunk>): List<AttachmentChunk> {
        val seen = mutableSetOf<String>()
        return chunks.filter { chunk ->
            val key = "${chunk.attachmentId}:${chunk.chunkIndex}"
            seen.add(key)
        }
    }

    /**
     * Group chunks by filename and sort by chunkIndex ascending within each group.
     * Groups are ordered by the first chunk's filename for deterministic output.
     */
    private fun groupAndSort(chunks: List<AttachmentChunk>): List<AttachmentChunk> {
        return chunks
            .groupBy { it.filename }
            .toSortedMap()
            .flatMap { (_, group) -> group.sortedBy { it.chunkIndex } }
    }

    /**
     * Extract ticket IDs from all chunk text content.
     * Excludes the current ticket ID to avoid self-references.
     */
    private fun extractTicketIds(
        chunks: List<AttachmentChunk>,
        excludeId: String
    ): List<String> {
        val allText = chunks.joinToString(separator = " ") { it.chunkText }
        return ticketIdExtractor.extract(
            text = allText,
            excludeIds = setOf(excludeId)
        )
    }

    private fun countUniqueAttachments(chunks: List<AttachmentChunk>): Int {
        return chunks.map { it.attachmentId }.distinct().size
    }
}
