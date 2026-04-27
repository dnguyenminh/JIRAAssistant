package com.assistant.document

import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Fallback serialization functions for BRD prompt building when
 * linkedTicketAnalyses is empty but raw ticket data is available.
 *
 * These functions accept raw data parameters (not EnrichedContext)
 * because shared module cannot import server module classes.
 *
 * Requirements: 7.1, 7.4, 2.2, 2.3
 */

/**
 * Fallback: serialize raw ticket data from allTickets when
 * linkedTicketAnalyses is empty. Skips the root ticket.
 *
 * @param allTickets all tickets discovered during BFS traversal
 * @param rootTicketId ticket ID of the root ticket to exclude
 */
internal fun StringBuilder.appendFallbackLinkedTickets(
    allTickets: List<StructuredTicketContent>,
    @Suppress("UNUSED_PARAMETER") rootTicketId: String
) {
    // Root ticket is always first in allTickets (BFS convention) — skip it
    val nonRoot = allTickets.drop(1).filter { it.summary.isNotBlank() }
    if (nonRoot.isEmpty()) return
    appendLine("--- Linked Tickets (Raw Data, ${nonRoot.size}) ---")
    nonRoot.forEach { ticket -> appendFallbackTicket(ticket) }
}

/** Serialize a single fallback ticket with annotation. Req 7.4. */
private fun StringBuilder.appendFallbackTicket(
    ticket: StructuredTicketContent
) {
    val ticketId = ticket.summary.take(20)
    appendLine("[Note: Ticket $ticketId chưa có deep analysis — sử dụng raw Jira data.]")
    appendLine("  Summary: ${ticket.summary}")
    if (ticket.description.isNotBlank()) {
        appendLine("  Description: ${ticket.description.take(500)}")
    }
    if (ticket.status.isNotBlank()) {
        appendLine("  Status: ${ticket.status}")
    }
    if (ticket.priority.isNotBlank()) {
        appendLine("  Priority: ${ticket.priority}")
    }
}

/**
 * Serialize comments from ALL tickets into prompt context.
 *
 * @param rawComments map of ticketId → list of comment data (author, date, body)
 */
internal fun StringBuilder.appendEnrichedComments(
    rawComments: Map<String, List<CommentData>>
) {
    if (rawComments.isEmpty()) return
    appendLine("--- Comments from All Tickets ---")
    rawComments.forEach { (ticketId, comments) ->
        if (comments.isEmpty()) return@forEach
        appendLine("Comments for $ticketId (${comments.size}):")
        comments.forEach { comment ->
            appendLine("  [Comment by ${comment.author} on ${comment.date} for $ticketId]: ${comment.body}")
        }
    }
}

/**
 * Simple data holder for comment info, usable from shared module
 * without depending on server module's FullComment.
 */
data class CommentData(
    val author: String,
    val date: String,
    val body: String
)
