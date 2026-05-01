package com.assistant.server.chat

import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository
import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.attachment.models.ChunkType

/**
 * In-process executor for Local Knowledge Base MCP-style tools.
 * Handles 3 operations: search_knowledge, get_ticket_info, search_relationships.
 * Requirements: 19.64, 19.65, 19.66, 19.67, 19.69, 19.74
 */
class LocalKBToolExecutor(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val kbRepository: KBRepository
) {
    companion object {
        const val SERVER_ID = "local-knowledge-base"
        private const val DEFAULT_TOP_K = 10
        /** Minimum cosine similarity score to consider a result relevant. */
        private const val MIN_RELEVANCE_SCORE = 0.3f
    }

    /** Route tool call to appropriate operation. */
    suspend fun execute(toolName: String, arguments: Map<String, String>): String =
        when (toolName) {
            "search_knowledge" -> searchKnowledge(arguments)
            "get_ticket_info" -> getTicketInfo(arguments)
            "search_relationships" -> searchRelationships(arguments)
            "ingest_knowledge" -> ingestKnowledge(arguments)
            else -> "Tool error: Unknown tool '$toolName'"
        }

    /** Semantic search across all chunk types. Req: 19.64 */
    private suspend fun searchKnowledge(args: Map<String, String>): String {
        val query = args["query"] ?: return "Tool error: missing 'query'"
        val chunkType = args["chunkType"]
        val topK = args["topK"]?.toIntOrNull() ?: DEFAULT_TOP_K
        val embedding = embeddingService.embed(query)
            ?: return "Tool error: EmbeddingService unavailable"
        val chunks = vectorStore.searchWithScores(embedding, topK, chunkType)
        val relevant = chunks.filter { it.second >= MIN_RELEVANCE_SCORE }
        if (relevant.isEmpty()) return "No relevant results found for query: $query"
        return formatKnowledgeChunks(relevant.map { it.first })
    }

    /** Lookup ticket analysis from KBRepository. Req: 19.65 */
    private suspend fun getTicketInfo(args: Map<String, String>): String {
        val ticketId = args["ticketId"]
            ?: return "Tool error: missing 'ticketId'"
        val record = kbRepository.findByTicketId(ticketId)
            ?: return "Ticket không tìm thấy trong Knowledge Base."
        return formatKBRecord(record)
    }

    /** Search relationships only. Req: 19.66 */
    private suspend fun searchRelationships(args: Map<String, String>): String {
        val query = args["query"] ?: return "Tool error: missing 'query'"
        val embedding = embeddingService.embed(query)
            ?: return "Tool error: EmbeddingService unavailable"
        val chunks = vectorStore.search(embedding, DEFAULT_TOP_K, "RELATIONSHIP")
        if (chunks.isEmpty()) return "No relationships found for: $query"
        return formatKnowledgeChunks(chunks)
    }

    /** Ingest text content into KB as a searchable chunk. */
    private suspend fun ingestKnowledge(args: Map<String, String>): String {
        val title = args["title"] ?: return "Tool error: missing 'title'"
        val content = args["content"] ?: return "Tool error: missing 'content'"
        val ticketId = args["ticketId"] ?: title.take(20)
        val embedding = embeddingService.embed(content)
            ?: return "Tool error: EmbeddingService unavailable"
        val chunk = AttachmentChunk(
            ticketId = ticketId, attachmentId = "brd-ingest-$ticketId",
            filename = title, chunkIndex = 0, chunkText = content,
            embedding = embedding.toList(),
            createdAt = java.time.Instant.now().toString(),
            chunkType = ChunkType.ANALYSIS
        )
        val saved = vectorStore.saveChunk(chunk)
        return if (saved) "Ingested: $title" else "Tool error: save failed"
    }
}

/** Format chunks grouped by chunkType section — same logic as ChatServiceImpl. Req: 19.74 */
internal fun formatKnowledgeChunks(chunks: List<AttachmentChunk>): String {
    val grouped = chunks.groupBy { mapChunkTypeToSection(it.chunkType) }
    return SECTION_ORDER
        .filter { grouped.containsKey(it) }
        .joinToString("\n") { section ->
            val items = grouped[section]!!.joinToString("\n") { "[${it.filename}] ${it.chunkText}" }
            "$section\n$items"
        }
}

private val SECTION_ORDER = listOf(
    "--- RELEVANT TICKETS ---",
    "--- RELATIONSHIPS ---",
    "--- ANALYSIS ---",
    "--- CONFLUENCE DOCS ---",
    "--- ATTACHMENTS ---"
)

private fun mapChunkTypeToSection(chunkType: String): String = when (chunkType) {
    ChunkType.TICKET, ChunkType.CLUSTER -> "--- RELEVANT TICKETS ---"
    ChunkType.RELATIONSHIP -> "--- RELATIONSHIPS ---"
    ChunkType.ANALYSIS, ChunkType.EVOLUTION -> "--- ANALYSIS ---"
    ChunkType.CONFLUENCE -> "--- CONFLUENCE DOCS ---"
    else -> "--- ATTACHMENTS ---"
}

/** Format KBRecord fields into readable text. */
private fun formatKBRecord(r: KBRecord): String = buildString {
    appendLine("Ticket: ${r.ticketId}")
    appendLine("Summary: ${r.requirementSummary}")
    appendLine("Scrum Points: ${r.scrumPoints}")
    appendLine("Confidence: ${r.confidenceScore}")
    appendLine("Rationale: ${r.rationale}")
    if (r.evolutionHistory.isNotEmpty()) {
        appendLine("Evolution: ${r.evolutionHistory.joinToString(" → ") { it.description }}")
    }
    if (r.similarTicketRefs.isNotEmpty()) {
        appendLine("Similar: ${r.similarTicketRefs.joinToString(", ")}")
    }
}
