package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.CascadeResult

/**
 * Cascading Analysis Engine — BFS-based recursive analysis
 * of related tickets not yet in Knowledge Base.
 *
 * After analyzing a ticket, discovers related tickets (issue links,
 * sub-tasks, parent, comment mentions) and analyzes them recursively
 * until no unanalyzed related tickets remain or safety limit is reached.
 *
 * Uses same AI semaphore as BatchScanEngine (sequential processing).
 *
 * Requirements: 26.1-26.7
 */
interface CascadingAnalysisEngine {

    /**
     * Start cascading analysis from a seed ticket.
     * BFS traversal: discovers related tickets, checks KB,
     * analyzes missing ones, repeats until done or limit reached.
     *
     * @param ticketId Jira issue key (e.g., "PROJ-123")
     * @return CascadeResult with log entries and progress counters
     */
    suspend fun cascade(ticketId: String): CascadeResult
}
