package com.assistant.server.document.jobs

import com.assistant.server.document.models.CollectionJob
import com.assistant.server.document.models.CollectionJobItem
import com.assistant.server.document.models.CollectionJobStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [CollectionJobRepository] for property tests.
 *
 * Stores jobs in a ConcurrentHashMap. Supports optimistic locking
 * via version field — returns false when version doesn't match.
 */
class InMemoryCollectionJobRepository : CollectionJobRepository {

    private val store = ConcurrentHashMap<String, CollectionJob>()

    override fun save(job: CollectionJob) {
        store[job.jobId] = job
    }

    override fun findById(jobId: String): CollectionJob? = store[jobId]

    override fun findByParentTicketId(parentTicketId: String): List<CollectionJob> =
        store.values.filter { it.parentTicketId == parentTicketId }
            .sortedByDescending { it.createdAt }

    override fun findActive(): List<CollectionJob> =
        store.values.filter {
            it.status == CollectionJobStatus.QUEUED ||
                it.status == CollectionJobStatus.RUNNING
        }.sortedBy { it.createdAt }

    override fun updateJobStatus(
        jobId: String,
        newStatus: CollectionJobStatus,
        updatedAt: String,
        expectedVersion: Int
    ): Boolean {
        val current = store[jobId] ?: return false
        if (current.version != expectedVersion) return false
        store[jobId] = current.copy(
            status = newStatus,
            updatedAt = updatedAt,
            version = current.version + 1
        )
        return true
    }

    override fun updateItemStatus(
        jobId: String,
        completedItems: Int,
        failedItems: Int,
        items: List<CollectionJobItem>,
        updatedAt: String,
        expectedVersion: Int
    ): Boolean {
        val current = store[jobId] ?: return false
        if (current.version != expectedVersion) return false
        store[jobId] = current.copy(
            completedItems = completedItems,
            failedItems = failedItems,
            items = items,
            updatedAt = updatedAt,
            version = current.version + 1
        )
        return true
    }

    override fun delete(jobId: String) {
        store.remove(jobId)
    }

    /** Clear all jobs — used between test iterations. */
    fun clear() = store.clear()
}
