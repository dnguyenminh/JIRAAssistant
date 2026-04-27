package com.assistant.server.document.jobs

import com.assistant.server.document.models.CollectionJob
import com.assistant.server.document.models.TicketGraph

/**
 * Manages lifecycle of background [CollectionJob]s created after
 * Deep_Collector completes its initial traversal.
 *
 * Responsible for creating jobs, executing items, tracking progress,
 * and handling conflict resolution with manual analysis.
 *
 * Requirements: 13.1, 13.2, 13.3, 13.4, 13.10, 13.11, 13.13
 */
interface CollectionJobManager {
    /**
     * Create background jobs for linked ticket analysis and attachment processing.
     *
     * @param parentTicketId Root ticket that triggered the collection.
     * @param graph The traversal result containing all discovered tickets.
     * @param missingKBTicketIds Ticket IDs that need AI analysis (no KBRecord).
     * @param unprocessedAttachmentTicketIds Ticket IDs with unprocessed attachments.
     * @return List of created [CollectionJob]s (may be empty if nothing to do).
     */
    suspend fun createJobs(
        parentTicketId: String,
        graph: TicketGraph,
        missingKBTicketIds: List<String>,
        unprocessedAttachmentTicketIds: List<String>
    ): List<CollectionJob>

    /**
     * Execute a Collection_Job — process each item independently (Req 13.10).
     * KB-First check before each item (Req 14.2).
     * Uses aiAnalysisSemaphore and jiraApiSemaphore for concurrency control.
     */
    suspend fun executeJob(jobId: String)

    /**
     * Preempt a PENDING item when manual analysis is triggered (Req 14.1).
     * Marks the item as SKIPPED with reason "preempted by manual analysis".
     */
    suspend fun preemptItem(jobId: String, ticketId: String)

    /** Check if a ticket is currently being processed by a background job. */
    suspend fun isTicketProcessing(ticketId: String): Boolean

    /**
     * Preempt all PENDING items for a ticket across active jobs (Req 14.1).
     * Called when manual analysis is triggered — manual has higher priority.
     */
    suspend fun preemptPendingForTicket(ticketId: String)
}
