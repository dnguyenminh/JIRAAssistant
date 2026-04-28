package com.assistant.server.jobs

import com.assistant.document.models.GenerationJob
import com.assistant.server.db.JobRepository
import java.util.concurrent.ConcurrentHashMap

/** In-memory JobRepository for unit/property tests. */
class InMemoryJobRepository : JobRepository {

    private val store = ConcurrentHashMap<String, GenerationJob>()

    override suspend fun create(job: GenerationJob) {
        store[job.jobId] = job
    }

    override suspend fun findById(jobId: String): GenerationJob? =
        store[jobId]

    override suspend fun findByTicketIdAndTypeActive(
        ticketId: String, documentType: String
    ): GenerationJob? = store.values.firstOrNull {
        it.ticketId == ticketId &&
            it.documentType == documentType &&
            it.status in listOf("QUEUED", "RUNNING", "PAUSED")
    }

    override suspend fun findActiveByTicketId(ticketId: String): List<GenerationJob> =
        store.values.filter {
            it.ticketId == ticketId &&
                it.status in listOf("QUEUED", "RUNNING", "PAUSED")
        }

    override suspend fun findByUser(
        userId: String, statusFilter: List<String>?
    ): List<GenerationJob> = store.values.filter {
        it.createdBy == userId &&
            (statusFilter == null || it.status in statusFilter)
    }

    override suspend fun findByChainId(chainId: String): List<GenerationJob> =
        store.values.filter { it.chainId == chainId }

    override suspend fun updateStatus(
        jobId: String, status: String, progress: Int,
        phase: String, error: String?
    ) {
        store.computeIfPresent(jobId) { _, job ->
            job.copy(
                status = status,
                progressPercent = progress,
                phase = phase,
                errorMessage = error
            )
        }
    }

    override suspend fun updateStartedAt(jobId: String, startedAt: String) {
        store.computeIfPresent(jobId) { _, job ->
            job.copy(startedAt = startedAt)
        }
    }

    override suspend fun findRunningJobs(): List<GenerationJob> =
        store.values.filter { it.status == "RUNNING" }

    fun allJobs(): List<GenerationJob> = store.values.toList()
}
