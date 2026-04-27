package com.assistant.server.jobs

import com.assistant.document.models.GeneratedDocument
import com.assistant.document.models.GenerationJob
import com.assistant.document.models.JobStatus
import java.time.Instant
import java.util.UUID

/** Valid state transitions per the design state machine. */
fun validTransitions(): Map<JobStatus, Set<JobStatus>> = mapOf(
    JobStatus.QUEUED to setOf(
        JobStatus.RUNNING, JobStatus.PAUSED, JobStatus.CANCELLED
    ),
    JobStatus.PAUSED to setOf(JobStatus.QUEUED, JobStatus.CANCELLED),
    JobStatus.RUNNING to setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED),
    JobStatus.COMPLETED to emptySet(),
    JobStatus.FAILED to emptySet(),
    JobStatus.CANCELLED to emptySet()
)

/** Create a TestJobManager wired to in-memory repos. */
fun createTestJobManager(
    jobRepo: InMemoryJobRepository,
    docRepo: InMemoryDocumentRepository
): TestJobManager {
    return TestJobManager(jobRepo, docRepo, DependencyChecker)
}

/** Build a test chain with job at failIdx marked FAILED. */
suspend fun buildTestChain(
    repo: InMemoryJobRepository,
    ticketId: String,
    failIdx: Int
): List<GenerationJob> {
    val chainId = UUID.randomUUID().toString()
    val now = Instant.now().toString()
    val types = listOf("BRD", "FSD", "REQUIREMENT_SLIDES")
    val jobs = types.mapIndexed { i, type ->
        val status = when {
            i < failIdx -> "COMPLETED"
            i == failIdx -> "FAILED"
            else -> "QUEUED"
        }
        GenerationJob(
            jobId = UUID.randomUUID().toString(),
            ticketId = ticketId,
            documentType = type,
            status = status,
            chainId = chainId,
            createdBy = "test",
            createdAt = now,
            updatedAt = now
        )
    }
    jobs.forEach { repo.create(it) }
    return jobs
}

/** Verify chain propagation after failure. */
fun verifyChainPropagation(
    jobs: List<GenerationJob>, failIdx: Int
): Boolean {
    val typeOrder = listOf("BRD", "FSD", "REQUIREMENT_SLIDES")
    val sorted = jobs.sortedBy { typeOrder.indexOf(it.documentType) }
    return sorted.withIndex().all { (i, job) ->
        when {
            i < failIdx -> job.status == "COMPLETED"
            i == failIdx -> job.status == "FAILED"
            else -> job.status == "CANCELLED"
        }
    }
}

/** Add a BRD DRAFT doc so dependency checks pass. */
suspend fun addBrdDraft(
    docRepo: InMemoryDocumentRepository, ticketId: String
) {
    docRepo.save(
        GeneratedDocument(
            documentType = "BRD",
            ticketId = ticketId,
            generatedAt = Instant.now().toString(),
            markdownContent = "# BRD",
            approvalStatus = "DRAFT"
        )
    )
}

/** Build a real JobManager for pause/cancel/resume tests. */
fun buildRealJobManager(
    jobRepo: InMemoryJobRepository,
    docRepo: InMemoryDocumentRepository
): JobManager {
    val checker = DependencyChecker
    val orchestrator = JobChainOrchestrator(jobRepo)
    val stubAggregator = object : com.assistant.document.DocumentAggregator {
        override suspend fun aggregate(ticketId: String) =
            error("Not used")
    }
    val executor = object : JobExecutor(
        aggregator = stubAggregator,
        documentRepository = docRepo,
        jobRepository = jobRepo
    ) {
        override suspend fun execute(
            jobId: String, ticketId: String, docType: String
        ) { /* no-op */ }
    }
    return JobManager(
        jobRepository = jobRepo,
        documentRepository = docRepo,
        jobExecutor = executor,
        chainOrchestrator = orchestrator,
        dependencyChecker = checker,
        scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Unconfined +
                kotlinx.coroutines.SupervisorJob()
        )
    )
}
