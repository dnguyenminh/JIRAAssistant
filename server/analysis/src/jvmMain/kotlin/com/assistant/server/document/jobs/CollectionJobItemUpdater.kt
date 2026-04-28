package com.assistant.server.document.jobs

import com.assistant.server.document.models.CollectionJobItemStatus
import com.assistant.server.document.models.CollectionJobStatus
import java.time.Instant

/**
 * Item status update helpers for [CollectionJobManagerImpl].
 *
 * Handles individual item transitions (PENDING → PROCESSING → COMPLETED/FAILED/SKIPPED)
 * and job finalization (RUNNING → COMPLETED/FAILED).
 *
 * Requirements: 13.4, 13.10, 14.1
 */

/** Mark an item as PROCESSING and persist. */
internal fun CollectionJobManagerImpl.updateItemProcessing(jobId: String, itemId: String) {
    updateItemInJob(jobId, itemId, CollectionJobItemStatus.PROCESSING)
}

/** Mark an item as COMPLETED and persist. */
internal fun CollectionJobManagerImpl.updateItemCompleted(jobId: String, itemId: String) {
    updateItemInJob(jobId, itemId, CollectionJobItemStatus.COMPLETED)
}

/** Mark an item as FAILED with error message and persist. */
internal fun CollectionJobManagerImpl.updateItemFailed(
    jobId: String,
    itemId: String,
    errorMessage: String
) {
    updateItemInJob(jobId, itemId, CollectionJobItemStatus.FAILED, errorMessage = errorMessage)
}

/** Mark an item as SKIPPED with reason and persist. */
internal fun CollectionJobManagerImpl.updateItemSkipped(
    jobId: String,
    itemId: String,
    reason: String
) {
    updateItemInJob(jobId, itemId, CollectionJobItemStatus.SKIPPED, skipReason = reason)
}

/**
 * Finalize a job after all items are processed (Req 13.10).
 *
 * Job is COMPLETED if at least one item succeeded.
 * Job is FAILED only when ALL items failed.
 */
internal fun CollectionJobManagerImpl.finalizeJob(jobId: String) {
    val job = collectionJobRepository.findById(jobId) ?: return
    val completed = job.items.count { it.status == CollectionJobItemStatus.COMPLETED }
    val failed = job.items.count { it.status == CollectionJobItemStatus.FAILED }
    val skipped = job.items.count { it.status == CollectionJobItemStatus.SKIPPED }
    val processed = completed + failed + skipped

    val finalStatus = when {
        processed == 0 -> CollectionJobStatus.COMPLETED
        failed == job.totalItems -> CollectionJobStatus.FAILED
        else -> CollectionJobStatus.COMPLETED
    }

    collectionJobRepository.updateJobStatus(
        jobId = jobId,
        newStatus = finalStatus,
        updatedAt = Instant.now().toString(),
        expectedVersion = job.version
    )
    logger.info(
        "Job {} finalized as {}: {}/{} completed, {} failed, {} skipped",
        jobId, finalStatus, completed, job.totalItems, failed, skipped
    )
}

/**
 * Preempt a PENDING item when manual analysis is triggered (Req 14.1).
 * Only preempts items that are still PENDING — PROCESSING items are not affected.
 */
internal fun CollectionJobManagerImpl.preemptItemInternal(jobId: String, ticketId: String) {
    val job = collectionJobRepository.findById(jobId)
    if (job == null) {
        logger.warn("Cannot preempt: job {} not found", jobId)
        return
    }
    val item = job.items.find { it.itemId == ticketId }
    if (item == null) {
        logger.warn("Cannot preempt: item {} not found in job {}", ticketId, jobId)
        return
    }
    if (item.status != CollectionJobItemStatus.PENDING) {
        logger.info("Cannot preempt item {} in job {}: status is {}", ticketId, jobId, item.status)
        return
    }
    updateItemSkipped(jobId, ticketId, "preempted by manual analysis")
    logger.info("Preempted item {} in job {} for manual analysis", ticketId, jobId)
}

// ── Private helpers ──────────────────────────────────────────

/**
 * Update a single item's status within a job, recomputing progress counters.
 * Uses optimistic locking via [CollectionJobRepository.updateItemStatus].
 */
private fun CollectionJobManagerImpl.updateItemInJob(
    jobId: String,
    itemId: String,
    newStatus: CollectionJobItemStatus,
    skipReason: String? = null,
    errorMessage: String? = null
) {
    val job = collectionJobRepository.findById(jobId) ?: return
    val now = Instant.now().toString()
    val updatedItems = job.items.map { item ->
        if (item.itemId == itemId) {
            item.copy(
                status = newStatus,
                skipReason = skipReason ?: item.skipReason,
                errorMessage = errorMessage ?: item.errorMessage,
                updatedAt = now
            )
        } else item
    }
    val completedCount = countTerminal(updatedItems, CollectionJobItemStatus.COMPLETED)
    val failedCount = countTerminal(updatedItems, CollectionJobItemStatus.FAILED)

    collectionJobRepository.updateItemStatus(
        jobId = jobId,
        completedItems = completedCount,
        failedItems = failedCount,
        items = updatedItems,
        updatedAt = now,
        expectedVersion = job.version
    )
}

/** Count items with a specific terminal status (including SKIPPED as completed). */
private fun countTerminal(
    items: List<com.assistant.server.document.models.CollectionJobItem>,
    status: CollectionJobItemStatus
): Int {
    return when (status) {
        CollectionJobItemStatus.COMPLETED -> items.count {
            it.status == CollectionJobItemStatus.COMPLETED ||
                it.status == CollectionJobItemStatus.SKIPPED
        }
        else -> items.count { it.status == status }
    }
}
