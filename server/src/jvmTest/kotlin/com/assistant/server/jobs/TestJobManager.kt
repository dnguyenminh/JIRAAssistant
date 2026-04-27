package com.assistant.server.jobs

import com.assistant.document.models.GenerationJob
import com.assistant.document.models.JobChainResponse
import com.assistant.server.db.JobRepository
import java.time.Instant
import java.util.UUID

/**
 * Simplified JobManager for tests — creates jobs without executing.
 */
class TestJobManager(
    private val jobRepository: JobRepository,
    private val docRepository: InMemoryDocumentRepository,
    private val dependencyChecker: DependencyChecker
) {

    suspend fun createJob(
        ticketId: String, documentType: String, userId: String
    ): GenerationJob {
        val existing = jobRepository.findByTicketIdAndTypeActive(
            ticketId, documentType
        )
        if (existing != null) throw GenerationLockException(existing.jobId)
        val docs = docRepository.listByTicketId(ticketId)
        val check = dependencyChecker.canGenerate(documentType, docs)
        if (!check.allowed) throw DependencyException(
            check.reason ?: "Dependency not met"
        )
        val now = Instant.now().toString()
        val job = GenerationJob(
            jobId = UUID.randomUUID().toString(),
            ticketId = ticketId,
            documentType = documentType,
            status = "QUEUED",
            createdBy = userId,
            createdAt = now,
            updatedAt = now
        )
        jobRepository.create(job)
        return job
    }

    suspend fun createChain(
        ticketId: String, userId: String
    ): JobChainResponse {
        val chainId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val types = listOf("BRD", "FSD", "REQUIREMENT_SLIDES")
        val jobs = types.mapIndexed { i, type ->
            GenerationJob(
                jobId = UUID.randomUUID().toString(),
                ticketId = ticketId,
                documentType = type,
                status = if (i == 0) "RUNNING" else "QUEUED",
                chainId = chainId,
                createdBy = userId,
                createdAt = now,
                updatedAt = now
            )
        }
        jobs.forEach { jobRepository.create(it) }
        return JobChainResponse(chainId = chainId, jobs = jobs)
    }
}
