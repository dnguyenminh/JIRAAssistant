package com.assistant.server.document.prompt

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.server.document.models.EnrichedContext
import com.assistant.server.document.models.FullComment

/**
 * Builds formatted text sections from [EnrichedContext] data.
 *
 * Each method produces a self-contained text block for a specific
 * priority level, ready to be assembled by [PromptAssembler].
 *
 * Requirements: 6.4, 6.5, 6.6
 */
internal object PromptSectionBuilder {

    /** Format a single comment per Req 6.4. */
    fun formatComment(comment: FullComment): String =
        "[Comment by ${comment.author} on ${comment.createdDate}]: ${comment.body}"

    /** Format a single attachment chunk per Req 6.5. */
    fun formatChunk(chunk: AttachmentChunkInfo, ticketId: String): String =
        "[Attachment: ${chunk.filename} from $ticketId]: ${chunk.content}"

    /** Build root ticket raw data: summary, description, metadata, full comments (Req 4.2, 7.2). */
    fun buildRootRaw(context: EnrichedContext): String = buildString {
        val rootId = context.mainTicket.ticketId
        appendLine("--- Root Ticket: $rootId (Raw Data) ---")
        appendLine("Summary: ${context.mainTicket.requirementSummary}")
        appendLine("Description: ${context.mainTicket.businessSummary}")
        // Include metadata from StructuredTicketContent (Req 4.2)
        val raw = findRawTicket(rootId, context)
        appendTicketMetadata(raw)
        // Include comments from ALL tickets, not just root (Req 7.2)
        appendAllTicketComments(rootId, context)
    }

    /** Build root ticket KB analysis section. */
    fun buildRootKb(context: EnrichedContext): String = buildString {
        val kb = context.mainTicket
        appendLine("--- Root Ticket: ${kb.ticketId} (KB Analysis) ---")
        appendKbFields(kb)
    }

    /** Build raw data for tickets at a specific depth, sorted by relevance. */
    fun buildTicketsRaw(context: EnrichedContext, depth: Int): String = buildString {
        val ids = ticketIdsAtDepth(context, depth)
        if (ids.isEmpty()) return@buildString
        appendLine("--- Depth-$depth Tickets (${ids.size}) ---")
        for (id in ids) {
            appendTicketRaw(id, context)
        }
    }

    /** Build raw data for tickets at depth >= minDepth. */
    fun buildDeeperTickets(context: EnrichedContext, minDepth: Int): String = buildString {
        val ids = context.ticketDepthMap.entries
            .filter { it.value >= minDepth && it.key != context.mainTicket.ticketId }
            .sortedByDescending { relevanceFor(it.key, context) }
            .map { it.key }
        if (ids.isEmpty()) return@buildString
        appendLine("--- Deeper Tickets depth>=$minDepth (${ids.size}) ---")
        for (id in ids) {
            appendTicketRaw(id, context)
        }
    }

    /** Build attachment section for root ticket. */
    fun buildRootAttachments(context: EnrichedContext): String = buildString {
        val rootId = context.mainTicket.ticketId
        val chunks = context.attachmentChunks
        if (chunks.isEmpty()) return@buildString
        appendLine("--- Root Attachments ($rootId) ---")
        for (chunk in chunks) {
            appendLine(formatChunk(chunk, rootId))
        }
    }

    /** Build attachment section for depth-1 tickets. */
    fun buildDepth1Attachments(context: EnrichedContext): String = buildString {
        val rootFilenames = context.attachmentChunks.map { it.filename }.toSet()
        val depth1Ids = ticketIdsAtDepth(context, 1).toSet()
        val chunks = context.allAttachmentChunks
            .filter { it.filename !in rootFilenames }
        if (chunks.isEmpty()) return@buildString
        appendLine("--- Depth-1 Attachment Content ---")
        for (chunk in chunks) {
            appendLine(formatChunk(chunk, findChunkTicketId(chunk, depth1Ids)))
        }
    }

    /** Build attachment section for deeper tickets (depth >= 2). */
    fun buildDeeperAttachments(context: EnrichedContext): String = buildString {
        val rootFilenames = context.attachmentChunks.map { it.filename }.toSet()
        val remaining = context.allAttachmentChunks
            .filter { it.filename !in rootFilenames }
        if (remaining.isEmpty()) return@buildString
        appendLine("--- Deeper Attachment Content ---")
        for (chunk in remaining) {
            appendLine(formatChunk(chunk, "linked"))
        }
    }

    /** Build ticket graph metadata section per Req 6.6. */
    fun buildGraphMetadata(context: EnrichedContext): String = buildString {
        val meta = context.traversalMetadata ?: return@buildString
        val rootId = context.mainTicket.ticketId
        val ticketCount = context.ticketDepthMap.size
        appendLine("[Ticket Graph] Root: $rootId → $ticketCount related tickets" +
            " across ${meta.maxDepthReached} levels.")
        appendRelationships(context.ticketRelationships)
    }

    /** Build truncation annotation with detailed info (Req 5.3). */
    fun truncationAnnotation(
        removedTickets: Int,
        removedChunks: Int,
        keptFullTickets: Int = 0,
        keptSummaryTickets: Int = 0,
        originalSize: Int = 0,
        budget: Int = 0
    ): String {
        return "[TRUNCATED: Giữ lại $keptFullTickets tickets đầy đủ, " +
            "$keptSummaryTickets tickets chỉ summary, " +
            "cắt $removedTickets tickets và $removedChunks attachment chunks. " +
            "Tổng data gốc: $originalSize chars, budget: $budget chars]"
    }
}
