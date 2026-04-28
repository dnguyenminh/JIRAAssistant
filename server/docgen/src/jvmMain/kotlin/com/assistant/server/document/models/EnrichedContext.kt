package com.assistant.server.document.models

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.GenerationContext
import com.assistant.document.models.SprintMetadata
import com.assistant.kb.KBRecord
import kotlinx.serialization.Serializable

/**
 * Extended generation context containing all data from deep ticket traversal.
 *
 * Inherits from [GenerationContext] to maintain backward compatibility —
 * existing code that accepts [GenerationContext] (e.g. `JobExecutor`,
 * `BrdPromptBuilder`, `FsdPromptBuilder`) works transparently with this type
 * via Liskov Substitution.
 *
 * New fields provide raw Jira data, full comments, all attachment chunks,
 * and traversal metadata collected by `DeepCollector`.
 *
 * Uses [EnrichedContextSurrogate] for serialization to avoid duplicate
 * serial name conflicts with the parent [GenerationContext].
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4
 */
@Serializable(with = EnrichedContextSerializer::class)
class EnrichedContext(
    // Backward-compatible fields forwarded to GenerationContext
    mainTicket: KBRecord,
    linkedTicketAnalyses: List<KBRecord> = emptyList(),
    attachmentChunks: List<AttachmentChunkInfo> = emptyList(),
    sprintMetadata: SprintMetadata? = null,
    // === New deep collection fields ===
    /** All tickets discovered during BFS traversal with full Jira data. */
    val allTickets: List<StructuredTicketContent> = emptyList(),
    /** Directed edges representing relationships between tickets. */
    val ticketRelationships: List<TicketEdge> = emptyList(),
    /** Full comments per ticket — keyed by ticket ID, oldest-first. */
    val rawComments: Map<String, List<FullComment>> = emptyMap(),
    /** All attachment chunks across tickets, deduplicated by attachment ID. */
    val allAttachmentChunks: List<AttachmentChunkInfo> = emptyList(),
    /** Statistics and diagnostics from the BFS traversal process. */
    val traversalMetadata: TraversalMetadata? = null,
    /** BFS depth for each ticket — keyed by ticket ID, root = 0. */
    val ticketDepthMap: Map<String, Int> = emptyMap()
) : GenerationContext(
    mainTicket, linkedTicketAnalyses, attachmentChunks, sprintMetadata
) {

    /** Creates a copy with optionally overridden fields. */
    fun copy(
        mainTicket: KBRecord = this.mainTicket,
        linkedTicketAnalyses: List<KBRecord> = this.linkedTicketAnalyses,
        attachmentChunks: List<AttachmentChunkInfo> = this.attachmentChunks,
        sprintMetadata: SprintMetadata? = this.sprintMetadata,
        allTickets: List<StructuredTicketContent> = this.allTickets,
        ticketRelationships: List<TicketEdge> = this.ticketRelationships,
        rawComments: Map<String, List<FullComment>> = this.rawComments,
        allAttachmentChunks: List<AttachmentChunkInfo> = this.allAttachmentChunks,
        traversalMetadata: TraversalMetadata? = this.traversalMetadata,
        ticketDepthMap: Map<String, Int> = this.ticketDepthMap
    ): EnrichedContext = EnrichedContext(
        mainTicket, linkedTicketAnalyses, attachmentChunks, sprintMetadata,
        allTickets, ticketRelationships, rawComments,
        allAttachmentChunks, traversalMetadata, ticketDepthMap
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnrichedContext) return false
        if (!super.equals(other)) return false
        return allTickets == other.allTickets &&
            ticketRelationships == other.ticketRelationships &&
            rawComments == other.rawComments &&
            allAttachmentChunks == other.allAttachmentChunks &&
            traversalMetadata == other.traversalMetadata &&
            ticketDepthMap == other.ticketDepthMap
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + allTickets.hashCode()
        result = 31 * result + ticketRelationships.hashCode()
        result = 31 * result + rawComments.hashCode()
        result = 31 * result + allAttachmentChunks.hashCode()
        result = 31 * result + (traversalMetadata?.hashCode() ?: 0)
        result = 31 * result + ticketDepthMap.hashCode()
        return result
    }

    override fun toString(): String =
        "EnrichedContext(mainTicket=$mainTicket, " +
            "linkedTicketAnalyses=$linkedTicketAnalyses, " +
            "attachmentChunks=$attachmentChunks, " +
            "sprintMetadata=$sprintMetadata, " +
            "allTickets=${allTickets.size} items, " +
            "ticketRelationships=${ticketRelationships.size} edges, " +
            "rawComments=${rawComments.size} tickets, " +
            "allAttachmentChunks=${allAttachmentChunks.size} chunks, " +
            "traversalMetadata=$traversalMetadata, " +
            "ticketDepthMap=$ticketDepthMap)"
}
