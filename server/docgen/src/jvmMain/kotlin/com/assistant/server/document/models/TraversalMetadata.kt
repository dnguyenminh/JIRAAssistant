package com.assistant.server.document.models

import kotlinx.serialization.Serializable

/**
 * Statistics and diagnostics collected during BFS traversal.
 *
 * Captures counts, timing, and error information to support logging,
 * observability, and frontend display of traversal results.
 *
 * Requirements: 1.9, 1.10
 */
@Serializable
data class TraversalMetadata(
    /** Total ticket IDs discovered (including skipped). */
    val totalDiscovered: Int,
    /** Tickets successfully fetched from Jira API. */
    val totalFetched: Int,
    /** Tickets skipped due to errors, limits, or permissions. */
    val totalSkipped: Int,
    /** Deepest BFS level reached during traversal. */
    val maxDepthReached: Int,
    /** Wall-clock time for the entire traversal in milliseconds. */
    val traversalTimeMs: Long,
    /** IDs of tickets that were discovered but not fetched. */
    val skippedTicketIds: List<String> = emptyList(),
    /**
     * Number of tickets skipped due to 403 Forbidden.
     * Only the count is exposed — ticket IDs are NOT included
     * to avoid information disclosure. (Req 1.10)
     */
    val permissionDeniedCount: Int = 0,
    /** Whether traversal was terminated early (data > 3× maxPromptChars). */
    val earlyTerminated: Boolean = false
)
