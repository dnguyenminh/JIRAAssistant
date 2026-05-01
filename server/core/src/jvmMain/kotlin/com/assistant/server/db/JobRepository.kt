package com.assistant.server.db

import com.assistant.document.models.GenerationJob

/**
 * Repository interface for generation job persistence (Req 2.1, 2.5, 2.6).
 */
interface JobRepository {
    suspend fun create(job: GenerationJob)
    suspend fun findById(jobId: String): GenerationJob?
    suspend fun findByTicketIdAndTypeActive(ticketId: String, documentType: String): GenerationJob?
    suspend fun findActiveByTicketId(ticketId: String): List<GenerationJob>
    suspend fun findByUser(userId: String, statusFilter: List<String>?): List<GenerationJob>
    suspend fun findByChainId(chainId: String): List<GenerationJob>
    suspend fun updateStatus(jobId: String, status: String, progress: Int, phase: String, error: String? = null)
    suspend fun updateStartedAt(jobId: String, startedAt: String)
    suspend fun findRunningJobs(): List<GenerationJob>
}
