package com.assistant.server.document.prompt

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.kb.KBRecord
import com.assistant.server.document.models.EnrichedContext
import com.assistant.server.document.models.TicketEdge

/**
 * Internal helper extensions for [PromptSectionBuilder].
 *
 * Formatting utilities for KB fields, comments, ticket raw data,
 * and relationship metadata. Kept separate to respect the 200-line limit.
 *
 * Requirements: 6.4, 6.5, 6.6
 */

/** Append KB analysis fields for a KBRecord (Req 4.1). */
internal fun StringBuilder.appendKbFields(kb: KBRecord) {
    appendLine("Business Summary: ${kb.businessSummary}")
    if (kb.asIsState.isNotBlank()) appendLine("As-Is State: ${kb.asIsState}")
    if (kb.toBeState.isNotBlank()) appendLine("To-Be State: ${kb.toBeState}")
    if (kb.extractedRequirements.isNotEmpty()) {
        appendLine("Extracted Requirements:")
        kb.extractedRequirements.forEach { appendLine("  - $it") }
    }
    if (kb.acceptanceCriteria.isNotEmpty()) {
        appendLine("Acceptance Criteria:")
        kb.acceptanceCriteria.forEach {
            appendLine("  - ${it.id}: ${it.description}")
        }
    }
    // Expanded fields (Req 4.1): dependencies, technicalDetails, diagrams
    appendKbDependencies(kb)
    appendKbTechnicalDetails(kb)
    appendKbDiagrams(kb)
}

/** Append formatted comments for a ticket from rawComments. */
internal fun StringBuilder.appendComments(
    ticketId: String,
    context: EnrichedContext
) {
    val comments = context.rawComments[ticketId] ?: return
    if (comments.isEmpty()) return
    appendLine("Comments (${comments.size}):")
    comments.forEach { appendLine(PromptSectionBuilder.formatComment(it)) }
}

/** Append comments from ALL tickets in rawComments (Req 7.2). */
internal fun StringBuilder.appendAllTicketComments(
    rootId: String,
    context: EnrichedContext
) {
    if (context.rawComments.isEmpty()) return
    // Root comments first
    appendComments(rootId, context)
    // Then comments from all other tickets
    context.rawComments.keys
        .filter { it != rootId }
        .forEach { ticketId ->
            val comments = context.rawComments[ticketId] ?: return@forEach
            if (comments.isEmpty()) return@forEach
            appendLine("Comments for $ticketId (${comments.size}):")
            comments.forEach { appendLine(PromptSectionBuilder.formatComment(it)) }
        }
}

/** Append raw ticket data: KB summary + metadata + relationship + comments (Req 4.2, 4.3, 7.1, 7.4). */
internal fun StringBuilder.appendTicketRaw(
    ticketId: String,
    context: EnrichedContext
) {
    val kb = findKbForTicket(ticketId, context)
    val raw = findRawTicket(ticketId, context)
    appendLine("Ticket: $ticketId")
    if (kb != null) {
        appendLine("  Summary: ${kb.businessSummary}")
        if (kb.extractedRequirements.isNotEmpty()) {
            kb.extractedRequirements.forEach { appendLine("  - $it") }
        }
    } else if (raw != null) {
        // Fallback: use raw Jira data when no KBRecord (Req 7.1, 7.4)
        appendLine("  [Note: Ticket $ticketId chưa có deep analysis — sử dụng raw Jira data.]")
        appendLine("  Summary: ${raw.summary}")
        if (raw.description.isNotBlank()) {
            appendLine("  Description: ${raw.description.take(500)}")
        }
    }
    appendTicketMetadata(raw)
    appendRelationshipType(ticketId, context)
    appendComments(ticketId, context)
}

/** Append status, priority, labels, components from raw ticket (Req 4.2). */
internal fun StringBuilder.appendTicketMetadata(
    raw: StructuredTicketContent?
) {
    if (raw == null) return
    if (raw.status.isNotBlank()) appendLine("  Status: ${raw.status}")
    if (raw.priority.isNotBlank()) appendLine("  Priority: ${raw.priority}")
    if (raw.labels.isNotEmpty()) appendLine("  Labels: ${raw.labels.joinToString()}")
    if (raw.components.isNotEmpty()) appendLine("  Components: ${raw.components.joinToString()}")
}

/** Append relationship type between ticket and root (Req 4.3). */
internal fun StringBuilder.appendRelationshipType(
    ticketId: String,
    context: EnrichedContext
) {
    val edge = context.ticketRelationships.find {
        it.targetId == ticketId || it.sourceId == ticketId
    } ?: return
    val desc = if (edge.linkDescription.isNotBlank()) edge.linkDescription
        else edge.relationshipType.name
    appendLine("  Relationship: $desc")
}

/** Lookup StructuredTicketContent from allTickets by summary match or position. */
internal fun findRawTicket(
    ticketId: String,
    context: EnrichedContext
): StructuredTicketContent? {
    val depthEntry = context.ticketDepthMap.entries
        .sortedBy { it.value }
        .withIndex()
        .find { it.value.key == ticketId }
    val index = depthEntry?.index ?: return null
    return context.allTickets.getOrNull(index)
}

/** Append relationship edges in human-readable format. */
internal fun StringBuilder.appendRelationships(edges: List<TicketEdge>) {
    if (edges.isEmpty()) return
    appendLine("Relationships:")
    edges.forEach { edge ->
        val desc = if (edge.linkDescription.isNotBlank()) edge.linkDescription
            else edge.relationshipType.name
        appendLine("  ${edge.sourceId} --${desc}--> ${edge.targetId}")
    }
}

/** Get ticket IDs at a specific depth, sorted by relevance score. */
internal fun ticketIdsAtDepth(context: EnrichedContext, depth: Int): List<String> =
    context.ticketDepthMap.entries
        .filter { it.value == depth && it.key != context.mainTicket.ticketId }
        .sortedByDescending { relevanceFor(it.key, context) }
        .map { it.key }

/** Look up relevance score for a ticket from linkedTicketAnalyses. */
internal fun relevanceFor(ticketId: String, context: EnrichedContext): Double =
    context.linkedTicketAnalyses
        .find { it.ticketId == ticketId }
        ?.confidenceScore ?: 0.0

/** Find KBRecord for a ticket from linkedTicketAnalyses or mainTicket. */
internal fun findKbForTicket(ticketId: String, context: EnrichedContext): KBRecord? {
    if (ticketId == context.mainTicket.ticketId) return context.mainTicket
    return context.linkedTicketAnalyses.find { it.ticketId == ticketId }
}

/** Best-effort mapping of attachment chunk to a ticket ID. */
@Suppress("UNUSED_PARAMETER")
internal fun findChunkTicketId(
    chunk: AttachmentChunkInfo,
    candidateIds: Set<String>
): String = candidateIds.firstOrNull() ?: "linked"
