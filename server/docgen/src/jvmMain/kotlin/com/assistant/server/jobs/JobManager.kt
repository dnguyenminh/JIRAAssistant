package com.assistant.server.jobs

import com.assistant.document.SlideGenerator
import com.assistant.document.models.GeneratedDocument
import com.assistant.document.models.GenerationJob
import com.assistant.document.models.JobChainResponse
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.JobRepository
import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val JOB_TIMEOUT_MINUTES = 5L

/**
 * Singleton job manager — create, execute, pause, cancel, chain, recovery (Req 2.2–2.7, 3.1–3.5, 5.1).
 */
class JobManager(
    private val jobRepository: JobRepository,
    private val documentRepository: DocumentRepository,
    private val jobExecutor: JobExecutor,
    private val chainOrchestrator: JobChainOrchestrator,
    private val dependencyChecker: DependencyChecker,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    /** Track running coroutine jobs for cancellation. */
    private val activeCoroutines = java.util.concurrent.ConcurrentHashMap<String, Job>()

    suspend fun createJob(ticketId: String, documentType: String, userId: String): GenerationJob {
        val existing = jobRepository.findByTicketIdAndTypeActive(ticketId, documentType)
        if (existing != null) {
            if (isStaleJob(existing)) {
                failStaleJob(existing)
            } else {
                throw GenerationLockException(existing.jobId)
            }
        }

        val docs = documentRepository.listByTicketId(ticketId)
        val check = dependencyChecker.canGenerate(documentType, docs)
        if (!check.allowed) throw DependencyException(check.reason ?: "Dependency not met")

        val now = Instant.now().toString()
        val job = GenerationJob(
            jobId = UUID.randomUUID().toString(), ticketId = ticketId,
            documentType = documentType, status = "QUEUED",
            createdBy = userId, createdAt = now, updatedAt = now
        )
        jobRepository.create(job)
        activeCoroutines[job.jobId] = scope.launch { executeJobSafe(job.jobId) }
        return job
    }

    suspend fun createChain(ticketId: String, userId: String): JobChainResponse {
        val chainId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        val types = listOf("BRD", "FSD", "REQUIREMENT_SLIDES")
        val jobs = types.mapIndexed { i, type ->
            GenerationJob(
                jobId = UUID.randomUUID().toString(), ticketId = ticketId,
                documentType = type, status = if (i == 0) "RUNNING" else "QUEUED",
                chainId = chainId, createdBy = userId, createdAt = now, updatedAt = now
            )
        }
        jobs.forEach { jobRepository.create(it) }
        activeCoroutines[jobs.first().jobId] = scope.launch { executeJobSafe(jobs.first().jobId) }
        return JobChainResponse(chainId = chainId, jobs = jobs)
    }

    fun executeJob(jobId: String) {
        activeCoroutines[jobId] = scope.launch { executeJobSafe(jobId) }
    }

    private suspend fun executeJobSafe(jobId: String) {
        val job = jobRepository.findById(jobId) ?: return
        try {
            withTimeout(JOB_TIMEOUT_MINUTES * 60 * 1000) {
                if (job.documentType == "REQUIREMENT_SLIDES") {
                    executeSlideJob(job)
                } else {
                    jobExecutor.execute(jobId, job.ticketId, job.documentType)
                }
            }
            jobRepository.updateStatus(jobId, "COMPLETED", 100, "COMPLETE")
            chainOrchestrator.onJobCompleted(job.chainId, this@JobManager)
        } catch (e: CancellationException) {
            println("[JobManager] Job $jobId cancelled")
            // Status already set to CANCELLED by cancelJob(), just propagate
            throw e
        } catch (e: TimeoutCancellationException) {
            val msg = "Job timed out after ${JOB_TIMEOUT_MINUTES}m"
            val current = jobRepository.findById(jobId)
            val lastPhase = current?.phase ?: "GENERATING_DOCUMENT"
            jobRepository.updateStatus(jobId, "FAILED", current?.progressPercent ?: 0, lastPhase, msg)
            chainOrchestrator.onJobFailed(job.chainId)
            println("[JobManager] Job $jobId timed out")
        } catch (e: Exception) {
            val current = jobRepository.findById(jobId)
            val lastPhase = current?.phase ?: "GENERATING_DOCUMENT"
            jobRepository.updateStatus(jobId, "FAILED", current?.progressPercent ?: 0, lastPhase, e.message)
            chainOrchestrator.onJobFailed(job.chainId)
            println("[JobManager] Job $jobId failed: ${e.message}")
        } finally {
            activeCoroutines.remove(jobId)
        }
    }

    private suspend fun executeSlideJob(job: GenerationJob) {
        jobRepository.updateStatus(job.jobId, "RUNNING", 10, "AGGREGATING_DATA")
        val brd = documentRepository.findLatestByTicketIdAndType(job.ticketId, "BRD")
            ?: error("BRD not found for slide generation")
        jobRepository.updateStatus(job.jobId, "RUNNING", 50, "GENERATING_DOCUMENT")
        val slideMarkdown = SlideGenerator.generate(brd.markdownContent)
        jobRepository.updateStatus(job.jobId, "RUNNING", 90, "SAVING")
        documentRepository.save(GeneratedDocument(
            documentType = "REQUIREMENT_SLIDES", ticketId = job.ticketId,
            generatedAt = Instant.now().toString(), markdownContent = slideMarkdown,
            sourceTicketIds = brd.sourceTicketIds, attachmentSources = brd.attachmentSources,
            aiProviderUsed = "SlideGenerator", approvalStatus = "DRAFT"
        ))
    }

    suspend fun pauseJob(jobId: String): GenerationJob {
        val job = jobRepository.findById(jobId) ?: throw JobNotFoundException(jobId)
        if (job.status != "QUEUED") throw InvalidTransitionException(job.status, "PAUSED")
        jobRepository.updateStatus(jobId, "PAUSED", job.progressPercent, job.phase)
        return jobRepository.findById(jobId)!!
    }

    suspend fun resumeJob(jobId: String): GenerationJob {
        val job = jobRepository.findById(jobId) ?: throw JobNotFoundException(jobId)
        if (job.status != "PAUSED") throw InvalidTransitionException(job.status, "QUEUED")
        jobRepository.updateStatus(jobId, "QUEUED", job.progressPercent, job.phase)
        activeCoroutines[jobId] = scope.launch { executeJobSafe(jobId) }
        return jobRepository.findById(jobId)!!
    }

    suspend fun cancelJob(jobId: String): GenerationJob {
        val job = jobRepository.findById(jobId) ?: throw JobNotFoundException(jobId)
        if (job.status !in listOf("QUEUED", "PAUSED", "RUNNING")) {
            throw InvalidTransitionException(job.status, "CANCELLED")
        }
        jobRepository.updateStatus(jobId, "CANCELLED", job.progressPercent, job.phase)
        activeCoroutines.remove(jobId)?.cancel()
        chainOrchestrator.onJobCancelled(job.chainId)
        return jobRepository.findById(jobId)!!
    }

    suspend fun recoverOnStartup() {
        val running = jobRepository.findRunningJobs()
        running.forEach { job ->
            println("[JobManager] Recovering RUNNING job ${job.jobId} → QUEUED")
            jobRepository.updateStatus(job.jobId, "QUEUED", 0, job.phase)
            activeCoroutines[job.jobId] = scope.launch { executeJobSafe(job.jobId) }
        }
    }

    /** Detect zombie jobs that have been RUNNING/QUEUED longer than the timeout. */
    private fun isStaleJob(job: GenerationJob): Boolean {
        if (job.status !in listOf("RUNNING", "QUEUED")) return false
        return try {
            val updated = Instant.parse(job.updatedAt)
            val age = Duration.between(updated, Instant.now())
            age.toMinutes() >= JOB_TIMEOUT_MINUTES
        } catch (_: Exception) { true }
    }

    /** Fail a stale zombie job so a new one can be created. */
    private suspend fun failStaleJob(job: GenerationJob) {
        val msg = "Stale job auto-recovered after ${JOB_TIMEOUT_MINUTES}m"
        jobRepository.updateStatus(job.jobId, "FAILED", job.progressPercent, job.phase, msg)
        println("[JobManager] Stale job ${job.jobId} auto-failed: $msg")
    }
}

class GenerationLockException(val existingJobId: String) :
    RuntimeException("Tài liệu đang được sinh, vui lòng chờ hoàn tất")

class DependencyException(message: String) : RuntimeException(message)
class JobNotFoundException(jobId: String) : RuntimeException("Job not found: $jobId")
class InvalidTransitionException(from: String, to: String) :
    RuntimeException("Job status $from không cho phép thao tác chuyển sang $to")
