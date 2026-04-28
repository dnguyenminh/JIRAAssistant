package com.assistant.server.document.curation.models

import com.assistant.kb.KBRecord
import kotlinx.serialization.Serializable

/**
 * Output of the Curation Pipeline — classified, deduplicated,
 * and budget-constrained context ready for prompt assembly.
 *
 * Requirements: 3.1, 8.4, 9.2
 */
@Serializable
data class CuratedContext(
    /** Root ticket KB record — always complete, never truncated. */
    val rootTicket: KBRecord,

    /** New requirements from root + newer/concurrent tickets. */
    val toBeSection: ToBeSection,

    /** Existing functionality from older resolved tickets. */
    val asIsSection: AsIsSection,

    /** One-line references for outdated/superseded tickets. */
    val outdatedMetadata: List<OutdatedReference> = emptyList(),

    /** Summarized comments per ticket (keyed by ticket ID). */
    val commentSummaries: Map<String, CommentSummary> = emptyMap(),

    /** Curated attachment previews. */
    val attachments: List<CuratedAttachment> = emptyList(),

    /** Tickets included as brief references (MCP lookup available). */
    val referenceOnlyTickets: List<TicketReference> = emptyList(),

    /** Observability metrics. */
    val metrics: CurationMetrics = CurationMetrics(
        originalContextSizeChars = 0,
        curatedContextSizeChars = 0,
        ticketsAsIs = 0,
        ticketsToBe = 0,
        ticketsOutdated = 0,
        ticketsReferenceOnly = 0,
        commentsSummarized = 0,
        attachmentsCurated = 0,
        curationTimeMs = 0L
    )
)
