package com.assistant.server.jobs

import com.assistant.server.db.JobRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Tracks granular progress for document generation jobs (Req 1.1-1.6).
 * Provides heartbeat mechanism during AI generation phase.
 */
class DocGenProgressTracker(
    private val jobId: String,
    private val jobRepository: JobRepository,
    private val scope: CoroutineScope
) {
    private var heartbeatJob: Job? = null

    suspend fun markStarted() {
        jobRepository.updateStartedAt(jobId, Instant.now().toString())
    }

    suspend fun updateProgress(percent: Int, phase: String) {
        jobRepository.updateStatus(jobId, "RUNNING", percent, phase)
    }

    fun startHeartbeat(fromPercent: Int, maxPercent: Int, intervalMs: Long) {
        stopHeartbeat()
        var current = fromPercent
        heartbeatJob = scope.launch {
            while (current < maxPercent) {
                delay(intervalMs)
                current = minOf(current + 1, maxPercent)
                jobRepository.updateStatus(jobId, "RUNNING", current, "GENERATING_DOCUMENT")
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
