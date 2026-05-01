package com.assistant.server.jobs

import com.assistant.server.db.JobRepository

/**
 * Manages Job_Chain lifecycle (Req 1.4, 1.5, 3.4).
 * When job COMPLETED → start next QUEUED. When FAILED → cancel remaining.
 */
class JobChainOrchestrator(
    private val jobRepository: JobRepository
) {

    suspend fun onJobCompleted(chainId: String?, jobManager: JobManager) {
        if (chainId == null) return
        val jobs = jobRepository.findByChainId(chainId)
        val nextQueued = jobs.firstOrNull { it.status == "QUEUED" }
        if (nextQueued != null) {
            jobManager.executeJob(nextQueued.jobId)
        }
    }

    suspend fun onJobFailed(chainId: String?) {
        if (chainId == null) return
        cancelRemainingInChain(chainId)
    }

    suspend fun onJobCancelled(chainId: String?) {
        if (chainId == null) return
        cancelRemainingInChain(chainId)
    }

    private suspend fun cancelRemainingInChain(chainId: String) {
        val jobs = jobRepository.findByChainId(chainId)
        jobs.filter { it.status in listOf("QUEUED", "PAUSED") }
            .forEach { job ->
                jobRepository.updateStatus(job.jobId, "CANCELLED", 0, job.phase)
            }
    }
}
