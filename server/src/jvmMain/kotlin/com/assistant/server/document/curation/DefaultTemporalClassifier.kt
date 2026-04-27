package com.assistant.server.document.curation

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.kb.KBRecord
import com.assistant.server.document.curation.models.ContentClassification
import com.assistant.server.document.curation.models.TemporalRelation
import com.assistant.server.document.curation.models.TicketClassification

/**
 * Default implementation of [TemporalClassifier].
 *
 * Classification logic:
 * 1. Compare creation dates → OLDER / NEWER / CONCURRENT
 * 2. If OLDER + status Closed/Done + no conflicts → AS_IS
 * 3. If OLDER + requirement conflicts with root → OUTDATED
 * 4. If NEWER or CONCURRENT → TO_BE
 * 5. Safe default: TO_BE when uncertain
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6
 */
class DefaultTemporalClassifier : TemporalClassifier {

    override fun classify(
        rootTicket: StructuredTicketContent,
        linkedTicket: StructuredTicketContent,
        linkedKb: KBRecord?
    ): TicketClassification {
        val temporal = determineTemporalRelation(rootTicket, linkedTicket)
        val content = classifyContent(temporal, linkedTicket, rootTicket, linkedKb)
        return TicketClassification(
            ticketId = extractTicketId(linkedTicket, linkedKb),
            temporalRelation = temporal,
            contentClassification = content,
            supersededBy = if (content == ContentClassification.OUTDATED) {
                extractTicketId(rootTicket, null)
            } else null
        )
    }

    private fun determineTemporalRelation(
        root: StructuredTicketContent,
        linked: StructuredTicketContent
    ): TemporalRelation {
        val rootDate = root.createdDate
        val linkedDate = linked.createdDate
        if (rootDate.isBlank() || linkedDate.isBlank()) {
            return TemporalRelation.CONCURRENT
        }
        return when {
            linkedDate < rootDate -> TemporalRelation.OLDER
            linkedDate > rootDate -> TemporalRelation.NEWER
            else -> TemporalRelation.CONCURRENT
        }
    }

    private fun classifyContent(
        temporal: TemporalRelation,
        linked: StructuredTicketContent,
        root: StructuredTicketContent,
        linkedKb: KBRecord?
    ): ContentClassification = when (temporal) {
        TemporalRelation.NEWER, TemporalRelation.CONCURRENT -> {
            ContentClassification.TO_BE
        }
        TemporalRelation.OLDER -> classifyOlderTicket(linked, root, linkedKb)
    }

    private fun classifyOlderTicket(
        linked: StructuredTicketContent,
        root: StructuredTicketContent,
        linkedKb: KBRecord?
    ): ContentClassification {
        if (hasConflictingRequirements(linked, root, linkedKb)) {
            return ContentClassification.OUTDATED
        }
        if (isResolved(linked)) {
            return ContentClassification.AS_IS
        }
        // Safe default: TO_BE when uncertain (Req 2.5)
        return ContentClassification.TO_BE
    }

    private fun hasConflictingRequirements(
        linked: StructuredTicketContent,
        root: StructuredTicketContent,
        linkedKb: KBRecord?
    ): Boolean {
        val linkedReqs = linkedKb?.extractedRequirements ?: emptyList()
        val rootReqs = root.classifiedContent.toString()
        if (linkedReqs.isEmpty()) return false
        return linkedReqs.any { req ->
            rootReqs.contains(req.take(30), ignoreCase = true)
        }
    }

    private fun isResolved(ticket: StructuredTicketContent): Boolean {
        val status = ticket.status.lowercase()
        return status in RESOLVED_STATUSES
    }

    private fun extractTicketId(
        ticket: StructuredTicketContent,
        kb: KBRecord?
    ): String = kb?.ticketId ?: ticket.summary.substringBefore(" ").ifBlank {
        "UNKNOWN"
    }

    companion object {
        private val RESOLVED_STATUSES = setOf(
            "closed", "done", "resolved", "cancelled"
        )
    }
}
