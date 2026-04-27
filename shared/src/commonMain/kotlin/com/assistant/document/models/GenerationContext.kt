package com.assistant.document.models

import com.assistant.kb.KBRecord
import kotlinx.serialization.Serializable

/**
 * Aggregated context for document generation.
 * Contains main ticket analysis + linked ticket analyses + attachment chunks + sprint metadata.
 * Used as input for BRD/FSD prompt builders (Req 1.4).
 *
 * Open class to support inheritance by EnrichedContext (Req 5.2).
 * Manual copy()/equals()/hashCode()/toString() preserve data class behavior.
 */
@Serializable
open class GenerationContext(
    open val mainTicket: KBRecord,
    open val linkedTicketAnalyses: List<KBRecord> = emptyList(),
    open val attachmentChunks: List<AttachmentChunkInfo> = emptyList(),
    open val sprintMetadata: SprintMetadata? = null
) {
    /** Creates a copy with optionally overridden fields (mirrors data class copy). */
    open fun copy(
        mainTicket: KBRecord = this.mainTicket,
        linkedTicketAnalyses: List<KBRecord> = this.linkedTicketAnalyses,
        attachmentChunks: List<AttachmentChunkInfo> = this.attachmentChunks,
        sprintMetadata: SprintMetadata? = this.sprintMetadata
    ): GenerationContext = GenerationContext(mainTicket, linkedTicketAnalyses, attachmentChunks, sprintMetadata)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GenerationContext) return false
        return mainTicket == other.mainTicket &&
            linkedTicketAnalyses == other.linkedTicketAnalyses &&
            attachmentChunks == other.attachmentChunks &&
            sprintMetadata == other.sprintMetadata
    }

    override fun hashCode(): Int {
        var result = mainTicket.hashCode()
        result = 31 * result + linkedTicketAnalyses.hashCode()
        result = 31 * result + attachmentChunks.hashCode()
        result = 31 * result + (sprintMetadata?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "GenerationContext(mainTicket=$mainTicket, linkedTicketAnalyses=$linkedTicketAnalyses, " +
            "attachmentChunks=$attachmentChunks, sprintMetadata=$sprintMetadata)"
}

/**
 * Represents a chunk of attachment content retrieved from VectorStore
 * via semantic search (Req 1.3).
 */
@Serializable
data class AttachmentChunkInfo(
    val filename: String,
    val content: String,
    val similarityScore: Float = 0f
)

/**
 * Sprint metadata from Jira, included in GenerationContext
 * when available for timeline-related document sections.
 */
@Serializable
data class SprintMetadata(
    val sprintName: String = "",
    val startDate: String = "",
    val endDate: String = ""
)
