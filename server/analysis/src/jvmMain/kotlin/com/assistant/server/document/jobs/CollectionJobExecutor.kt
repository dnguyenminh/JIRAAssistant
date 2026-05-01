package com.assistant.server.document.jobs

import com.assistant.scan.ScanLogEntry
import com.assistant.scan.ScanLogStatus
import com.assistant.server.document.models.CollectionJobItemStatus
import com.assistant.server.document.models.CollectionJobStatus
import com.assistant.server.document.models.CollectionJobType
import kotlinx.coroutines.sync.withPermit
import java.time.Instant

/**
 * Job execution logic for [CollectionJobManagerImpl].
 *
 * Each item is processed independently — failure of one does not
 * affect others (Req 13.10). KB-First check before each item (Req 14.2).
 * Uses semaphores for concurrency control (Req 13.11).
 *
 * Requirements: 13.4, 13.10, 13.11, 13.13, 14.2
 */

/** Execute a Collection_Job — process each item independently. */
internal suspend fun CollectionJobManagerImpl.executeJobInternal(jobId: String) {
    val job = collectionJobRepository.findById(jobId)
    if (job == null) {
        logger.warn("Job {} not found, skipping execution", jobId)
        return
    }
    transitionJobToRunning(job)
    processAllItems(job)
    finalizeJob(jobId)
}

/** Transition job from QUEUED to RUNNING. */
private fun CollectionJobManagerImpl.transitionJobToRunning(
    job: com.assistant.server.document.models.CollectionJob
) {
    collectionJobRepository.updateJobStatus(
        jobId = job.jobId,
        newStatus = CollectionJobStatus.RUNNING,
        updatedAt = Instant.now().toString(),
        expectedVersion = job.version
    )
    logger.info("Job {} transitioned to RUNNING", job.jobId)
}

/** Process all items in the job independently. */
private suspend fun CollectionJobManagerImpl.processAllItems(
    job: com.assistant.server.document.models.CollectionJob
) {
    for (item in job.items) {
        if (item.status != CollectionJobItemStatus.PENDING) continue
        processItem(job.jobId, item.itemId, job.jobType, job.parentTicketId)
    }
}

/** Process a single item with KB-First check and semaphore control. */
private suspend fun CollectionJobManagerImpl.processItem(
    jobId: String,
    itemId: String,
    jobType: CollectionJobType,
    parentTicketId: String
) {
    val projectKey = extractProjectKey(parentTicketId)

    // KB-First check (Req 14.2)
    if (shouldSkipKBFirst(jobType, itemId)) {
        updateItemSkipped(jobId, itemId, "already in KB")
        logScanEntry(projectKey, itemId, ScanLogStatus.COMPLETED, "Skipped: already in KB")
        return
    }

    // Re-check item status (may have been preempted)
    if (isItemNoLongerPending(jobId, itemId)) return

    updateItemProcessing(jobId, itemId)
    logScanEntry(projectKey, itemId, ScanLogStatus.ANALYZING, "Processing item $itemId")

    try {
        executeItemWork(jobType, itemId, projectKey)
        updateItemCompleted(jobId, itemId)
        logScanEntry(projectKey, itemId, ScanLogStatus.COMPLETED, "Completed: $itemId")
    } catch (e: Exception) {
        logger.warn("Item {} in job {} failed: {}", itemId, jobId, e.message)
        updateItemFailed(jobId, itemId, e.message ?: "unknown error")
        logScanEntry(projectKey, itemId, ScanLogStatus.FAILED, "Failed: ${e.message}")
    }
}

/** Execute the actual work for an item using the appropriate semaphore. */
private suspend fun CollectionJobManagerImpl.executeItemWork(
    jobType: CollectionJobType,
    itemId: String,
    projectKey: String
) {
    when (jobType) {
        CollectionJobType.LINKED_TICKET_ANALYSIS -> {
            aiAnalysisSemaphore.withPermit {
                logger.debug("AI analysis for ticket {} (semaphore acquired)", itemId)
                // Actual AI analysis is handled by the analysis pipeline
                // This is a placeholder — real integration wires to TicketIntelligence
            }
        }
        CollectionJobType.ATTACHMENT_PROCESSING -> {
            jiraApiSemaphore.withPermit {
                logger.debug("Attachment processing for {} (semaphore acquired)", itemId)
                // Delegate to AttachmentPipeline for actual processing
            }
        }
    }
}

/** Check if item should be skipped due to existing KB record (Req 14.2). */
private suspend fun CollectionJobManagerImpl.shouldSkipKBFirst(
    jobType: CollectionJobType,
    itemId: String
): Boolean {
    if (jobType != CollectionJobType.LINKED_TICKET_ANALYSIS) return false
    return try {
        kbRepository.findByTicketId(itemId) != null
    } catch (_: Exception) {
        false
    }
}

/** Check if item was preempted or already processed between checks. */
private fun CollectionJobManagerImpl.isItemNoLongerPending(
    jobId: String,
    itemId: String
): Boolean {
    val current = collectionJobRepository.findById(jobId) ?: return true
    val item = current.items.find { it.itemId == itemId } ?: return true
    return item.status != CollectionJobItemStatus.PENDING
}

/** Extract project key from ticket ID (e.g. "ICL2-100" → "ICL2"). */
internal fun extractProjectKey(ticketId: String): String =
    ticketId.substringBeforeLast('-')

/** Log a scan entry for observability (Req 13.13). */
private suspend fun CollectionJobManagerImpl.logScanEntry(
    projectKey: String,
    ticketId: String,
    status: ScanLogStatus,
    message: String
) {
    try {
        scanLogRepository.addEntry(
            ScanLogEntry(
                projectKey = projectKey,
                ticketId = ticketId,
                status = status,
                message = message,
                timestamp = Instant.now().toString()
            )
        )
    } catch (e: Exception) {
        logger.warn("Failed to log scan entry for {}: {}", ticketId, e.message)
    }
}
