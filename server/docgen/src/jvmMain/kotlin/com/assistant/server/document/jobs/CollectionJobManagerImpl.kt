package com.assistant.server.document.jobs

import com.assistant.kb.KBRepository
import com.assistant.scan.ScanLogRepository
import com.assistant.server.attachment.AttachmentPipeline
import com.assistant.server.document.models.*
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Implementation of [CollectionJobManager] — manages background
 * Collection_Jobs for linked ticket analysis and attachment processing.
 *
 * Job lifecycle: QUEUED → RUNNING → COMPLETED/FAILED
 * Item lifecycle: PENDING → PROCESSING → COMPLETED/FAILED/SKIPPED
 *
 * Requirements: 13.1, 13.2, 13.4, 13.10, 13.11, 13.13, 14.1, 14.2, 14.4
 */
class CollectionJobManagerImpl(
    internal val collectionJobRepository: CollectionJobRepository,
    internal val kbRepository: KBRepository,
    internal val scanLogRepository: ScanLogRepository,
    internal val attachmentPipeline: AttachmentPipeline,
    internal val aiAnalysisSemaphore: Semaphore,
    internal val jiraApiSemaphore: Semaphore
) : CollectionJobManager {

    internal val logger = LoggerFactory.getLogger(CollectionJobManagerImpl::class.java)

    override suspend fun createJobs(
        parentTicketId: String,
        graph: TicketGraph,
        missingKBTicketIds: List<String>,
        unprocessedAttachmentTicketIds: List<String>
    ): List<CollectionJob> {
        val jobs = mutableListOf<CollectionJob>()
        createLinkedTicketJob(parentTicketId, missingKBTicketIds)?.let { jobs.add(it) }
        createAttachmentJob(parentTicketId, unprocessedAttachmentTicketIds)?.let { jobs.add(it) }
        if (jobs.isNotEmpty()) {
            logger.info("Created {} background jobs for ticket {}", jobs.size, parentTicketId)
        }
        return jobs
    }

    override suspend fun executeJob(jobId: String) {
        executeJobInternal(jobId)
    }

    override suspend fun preemptItem(jobId: String, ticketId: String) {
        preemptItemInternal(jobId, ticketId)
    }

    override suspend fun isTicketProcessing(ticketId: String): Boolean {
        return findActiveJobForTicket(ticketId) != null
    }

    override suspend fun preemptPendingForTicket(ticketId: String) {
        val activeJobs = collectionJobRepository.findActive()
        for (job in activeJobs) {
            if (job.jobType != CollectionJobType.LINKED_TICKET_ANALYSIS) continue
            val hasPending = job.items.any { it.itemId == ticketId && it.status == CollectionJobItemStatus.PENDING }
            if (hasPending) {
                preemptItem(job.jobId, ticketId)
                logger.info("Preempted PENDING item {} in job {} for manual analysis", ticketId, job.jobId)
            }
        }
    }

    /** Find an active job that contains a PROCESSING item for the given ticket. */
    internal fun findActiveJobForTicket(ticketId: String): CollectionJob? {
        return collectionJobRepository.findActive().firstOrNull { job ->
            job.items.any { item ->
                item.itemId == ticketId &&
                    item.status == CollectionJobItemStatus.PROCESSING
            }
        }
    }

    // ── Job creation helpers ─────────────────────────────────────

    private fun createLinkedTicketJob(
        parentTicketId: String,
        missingKBTicketIds: List<String>
    ): CollectionJob? {
        if (missingKBTicketIds.isEmpty()) return null
        val now = Instant.now().toString()
        val items = missingKBTicketIds.map { CollectionJobItem(itemId = it) }
        val job = CollectionJob(
            jobId = UUID.randomUUID().toString(),
            parentTicketId = parentTicketId,
            jobType = CollectionJobType.LINKED_TICKET_ANALYSIS,
            status = CollectionJobStatus.QUEUED,
            totalItems = items.size,
            items = items,
            createdAt = now,
            updatedAt = now
        )
        collectionJobRepository.save(job)
        logger.info("Created LINKED_TICKET_ANALYSIS job {} with {} items", job.jobId, items.size)
        return job
    }

    private fun createAttachmentJob(
        parentTicketId: String,
        unprocessedAttachmentTicketIds: List<String>
    ): CollectionJob? {
        if (unprocessedAttachmentTicketIds.isEmpty()) return null
        val now = Instant.now().toString()
        val items = unprocessedAttachmentTicketIds.map { CollectionJobItem(itemId = it) }
        val job = CollectionJob(
            jobId = UUID.randomUUID().toString(),
            parentTicketId = parentTicketId,
            jobType = CollectionJobType.ATTACHMENT_PROCESSING,
            status = CollectionJobStatus.QUEUED,
            totalItems = items.size,
            items = items,
            createdAt = now,
            updatedAt = now
        )
        collectionJobRepository.save(job)
        logger.info("Created ATTACHMENT_PROCESSING job {} with {} items", job.jobId, items.size)
        return job
    }
}
