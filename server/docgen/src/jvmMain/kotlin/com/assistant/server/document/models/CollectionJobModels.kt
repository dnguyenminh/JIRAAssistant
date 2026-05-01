package com.assistant.server.document.models

import kotlinx.serialization.Serializable

/**
 * Type of background [CollectionJob] created after Deep_Collector traversal.
 *
 * - [LINKED_TICKET_ANALYSIS] — AI analysis for linked tickets missing a KBRecord.
 * - [ATTACHMENT_PROCESSING] — Download, convert, chunk, and embed unprocessed attachments.
 *
 * Requirements: 13.2
 */
@Serializable
enum class CollectionJobType {
    LINKED_TICKET_ANALYSIS,
    ATTACHMENT_PROCESSING
}

/**
 * Lifecycle status of a [CollectionJob].
 *
 * Requirements: 13.3
 */
@Serializable
enum class CollectionJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * Lifecycle status of a single item inside a [CollectionJob].
 *
 * Requirements: 13.4
 */
@Serializable
enum class CollectionJobItemStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * A single work item inside a [CollectionJob].
 *
 * Each item represents either a ticket to analyze or an attachment to process.
 * Items are processed independently — a failure in one does not affect others.
 *
 * Requirements: 13.4
 */
@Serializable
data class CollectionJobItem(
    /** Ticket key or attachment filename identifying this item. */
    val itemId: String,
    /** Current processing status of this item. */
    val status: CollectionJobItemStatus = CollectionJobItemStatus.PENDING,
    /** Reason the item was skipped (e.g. "already in KB", "preempted"). Null if not skipped. */
    val skipReason: String? = null,
    /** Error message if the item failed. Null on success or pending. */
    val errorMessage: String? = null,
    /** ISO-8601 timestamp of the last status change. Null if still pending. */
    val updatedAt: String? = null
)

/**
 * A background job that processes linked tickets or attachments asynchronously
 * after [DeepCollector] completes its initial traversal.
 *
 * Progress is tracked per-item and exposed via REST API so the frontend can
 * display a progress bar. The [version] field enables optimistic locking to
 * prevent lost updates when concurrent workers modify the same job.
 *
 * Requirements: 13.3, 14.7
 */
@Serializable
data class CollectionJob(
    /** Unique job identifier (UUID). */
    val jobId: String,
    /** Root ticket that triggered this collection. */
    val parentTicketId: String,
    /** Whether this job analyzes linked tickets or processes attachments. */
    val jobType: CollectionJobType,
    /** Current lifecycle status. */
    val status: CollectionJobStatus = CollectionJobStatus.QUEUED,
    /** Total number of items to process. */
    val totalItems: Int,
    /** Number of items that completed successfully. */
    val completedItems: Int = 0,
    /** Number of items that failed. */
    val failedItems: Int = 0,
    /** Individual work items with per-item status. */
    val items: List<CollectionJobItem> = emptyList(),
    /** ISO-8601 timestamp when the job was created. */
    val createdAt: String,
    /** ISO-8601 timestamp of the last update. */
    val updatedAt: String,
    /** Optimistic locking version — incremented on each update (Req 14.7). */
    val version: Int = 1
) {
    /**
     * Percentage of items processed (completed + failed) out of [totalItems].
     * Returns 0 when [totalItems] is zero to avoid division by zero.
     */
    val progressPercent: Int
        get() = if (totalItems > 0)
            (completedItems + failedItems) * 100 / totalItems
        else 0
}
