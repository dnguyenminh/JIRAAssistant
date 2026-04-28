package com.assistant.server.document.jobs

import com.assistant.server.document.models.CollectionJob
import com.assistant.server.document.models.CollectionJobItem
import com.assistant.server.document.models.CollectionJobStatus

/**
 * Interface for [CollectionJob] persistence.
 *
 * Abstracts storage operations so implementations can use SQLDelight (tests)
 * or PostgreSQL JDBC (production) interchangeably.
 *
 * Requirements: 13.3, 13.5, 13.6, 14.7
 */
interface CollectionJobRepository {

    /** Persist a new [CollectionJob]. */
    fun save(job: CollectionJob)

    /** Find a single job by its ID, or null if not found. */
    fun findById(jobId: String): CollectionJob?

    /** Find all jobs for a given parent ticket, ordered by created_at DESC. */
    fun findByParentTicketId(parentTicketId: String): List<CollectionJob>

    /** Find all QUEUED or RUNNING jobs, ordered by created_at ASC. */
    fun findActive(): List<CollectionJob>

    /**
     * Update job status with optimistic locking.
     * @return true if the update succeeded (version matched), false on conflict.
     */
    fun updateJobStatus(
        jobId: String,
        newStatus: CollectionJobStatus,
        updatedAt: String,
        expectedVersion: Int
    ): Boolean

    /**
     * Update item progress (completed/failed counts + items JSON) with optimistic locking.
     * @return true if the update succeeded (version matched), false on conflict.
     */
    fun updateItemStatus(
        jobId: String,
        completedItems: Int,
        failedItems: Int,
        items: List<CollectionJobItem>,
        updatedAt: String,
        expectedVersion: Int
    ): Boolean

    /** Delete a job by ID. */
    fun delete(jobId: String)
}
