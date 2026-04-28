package com.assistant.server.document

import com.assistant.document.DocumentAggregator
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.GenerationContext
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import org.slf4j.LoggerFactory

/**
 * Server-side DocumentAggregator implementation.
 * Collects main ticket KBRecord, linked ticket analyses, and
 * semantic-search attachment chunks into a GenerationContext (Req 1.1–1.5).
 */
class DocumentAggregatorImpl(
    private val kbRepository: KBRepository,
    private val vectorStore: VectorStore,
    private val embeddingService: EmbeddingService
) : DocumentAggregator {

    private val logger = LoggerFactory.getLogger(DocumentAggregatorImpl::class.java)

    companion object {
    }

    override suspend fun aggregate(ticketId: String): GenerationContext {
        val mainTicket = fetchMainTicket(ticketId)
        val linkedAnalyses = fetchLinkedTicketAnalyses(mainTicket)
        val attachmentChunks = searchAttachmentChunks(mainTicket)
        return GenerationContext(
            mainTicket = mainTicket,
            linkedTicketAnalyses = linkedAnalyses,
            attachmentChunks = attachmentChunks
        )
    }

    /** Fetch main ticket KBRecord — error if missing or no deep analysis (Req 1.1, 2.5). */
    private suspend fun fetchMainTicket(ticketId: String): KBRecord {
        val record = kbRepository.findByTicketId(ticketId)
            ?: error("Ticket $ticketId not found in Knowledge Base")
        require(record.businessSummary.isNotBlank()) {
            "Ticket $ticketId must be analyzed before generating documents"
        }
        logDeepAnalysisWarningIfNeeded(ticketId, record)
        return record
    }

    /** Log warning when deep analysis fields are empty — does NOT block generation (Req 2.5). */
    private fun logDeepAnalysisWarningIfNeeded(ticketId: String, record: KBRecord) {
        val available = mutableListOf<String>()
        val missing = mutableListOf<String>()
        checkField("asIsState", record.asIsState.isNotBlank(), available, missing)
        checkField("toBeState", record.toBeState.isNotBlank(), available, missing)
        checkField("extractedRequirements", record.extractedRequirements.isNotEmpty(), available, missing)
        checkField("acceptanceCriteria", record.acceptanceCriteria.isNotEmpty(), available, missing)
        if (missing.size == 4) {
            logger.warn(
                "Ticket {} has limited deep analysis data. Available: [{}], Missing: [{}]",
                ticketId, available.joinToString(), missing.joinToString()
            )
        }
    }

    private fun checkField(name: String, present: Boolean, available: MutableList<String>, missing: MutableList<String>) {
        if (present) available.add(name) else missing.add(name)
    }

    /** Fetch linked tickets + sub-tasks KBRecords, max 20, skip missing (Req 1.2, 1.5). */
    private suspend fun fetchLinkedTicketAnalyses(mainTicket: KBRecord): List<KBRecord> {
        val linkedIds = collectLinkedTicketIds(mainTicket)
        if (linkedIds.isEmpty()) return emptyList()

        return linkedIds.mapNotNull { id -> fetchLinkedOrNull(id) }
    }

    /** Collect unique linked ticket IDs from dependencies and similarTicketRefs. */
    private fun collectLinkedTicketIds(record: KBRecord): List<String> {
        val ids = mutableSetOf<String>()
        record.dependencies.blockingIssues
            .filter { it.key.isNotBlank() }
            .forEach { ids.add(it.key) }
        record.dependencies.relatedIssues
            .filter { it.key.isNotBlank() }
            .forEach { ids.add(it.key) }
        record.similarTicketRefs
            .filter { it.isNotBlank() }
            .forEach { ids.add(it) }
        ids.remove(record.ticketId)
        return ids.toList()
    }

    /** Fetch a single linked ticket KBRecord; null + log warning if missing (Req 1.5). */
    private suspend fun fetchLinkedOrNull(ticketId: String): KBRecord? {
        return try {
            kbRepository.findByTicketId(ticketId).also {
                if (it == null) logger.warn("Linked ticket {} not found in KB, skipping", ticketId)
            }
        } catch (e: Exception) {
            logger.warn("Failed to fetch linked ticket {}: {}", ticketId, e.message)
            null
        }
    }

    /** Semantic search attachment chunks via EmbeddingService + VectorStore (Req 1.3). */
    private suspend fun searchAttachmentChunks(mainTicket: KBRecord): List<AttachmentChunkInfo> {
        val query = buildSearchQuery(mainTicket)
        if (query.isBlank()) return emptyList()

        val embedding = embedQueryOrNull(query) ?: return emptyList()
        return searchChunks(embedding)
    }

    /** Build search query from businessSummary + extractedRequirements. */
    private fun buildSearchQuery(record: KBRecord): String {
        return buildString {
            append(record.businessSummary)
            if (record.extractedRequirements.isNotEmpty()) {
                append(" ")
                append(record.extractedRequirements.joinToString(" "))
            }
        }.trim()
    }

    /** Embed query text; null + log warning on failure. */
    private suspend fun embedQueryOrNull(query: String): FloatArray? {
        return try {
            embeddingService.embed(query).also {
                if (it == null) logger.warn("EmbeddingService returned null, skipping attachment search")
            }
        } catch (e: Exception) {
            logger.warn("EmbeddingService.embed() failed: {}", e.message)
            null
        }
    }

    /** Search VectorStore for top-K chunks; empty list on failure. */
    private suspend fun searchChunks(embedding: FloatArray): List<AttachmentChunkInfo> {
        return try {
            vectorStore.search(embedding, topK = 100)
                .map { chunk ->
                    AttachmentChunkInfo(
                        filename = chunk.filename,
                        content = chunk.chunkText,
                        similarityScore = 0f
                    )
                }
        } catch (e: Exception) {
            logger.warn("VectorStore search failed: {}", e.message)
            emptyList()
        }
    }
}
