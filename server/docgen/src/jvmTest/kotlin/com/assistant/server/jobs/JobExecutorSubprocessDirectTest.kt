package com.assistant.server.jobs

import com.assistant.document.models.GeneratedDocument
import com.assistant.server.db.DocumentRepository
import com.assistant.server.db.GeneratedDocumentMeta
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Tests for JobExecutor subprocess-only execution path.
 * No multi-tier fallback chain, no agent pipeline, no legacy prompt.
 *
 * Validates: Requirements 12.3, 12.4
 */
@Tag("legacy-pipeline-removal")
class JobExecutorSubprocessDirectTest {

    @Test
    fun `subprocess success saves document with subprocess provider`(): Unit = runBlocking {
        val docRepo = CapturingDocRepo()
        val orch = FakeOrchestrator(successDoc = "# BRD\n## Revision History\nv1.0")
        val exec = buildExecutor(orch = orch, docRepo = docRepo)

        exec.execute("job-1", "T-100", "BRD")

        orch.called shouldBe true
        docRepo.savedDocs.size shouldBe 1
        docRepo.savedDocs[0].aiProviderUsed shouldBe "BA Subprocess Orchestrator"
    }

    @Test
    fun `subprocess FAILED marks job as failed`(): Unit = runBlocking {
        val jobRepo = TrackingJobRepo()
        val orch = FakeOrchestrator(successDoc = null) // returns FAILED status
        val exec = buildExecutor(orch = orch, jobRepo = jobRepo)

        exec.execute("job-2", "T-200", "BRD")

        orch.called shouldBe true
        jobRepo.lastStatus shouldBe "FAILED"
    }

    @Test
    fun `null orchestrator marks job as failed`(): Unit = runBlocking {
        val jobRepo = TrackingJobRepo()
        val exec = buildExecutor(orch = null, jobRepo = jobRepo)

        exec.execute("job-3", "T-300", "BRD")

        jobRepo.lastStatus shouldBe "FAILED"
    }

    @Test
    fun `subprocess exception marks job as failed`(): Unit = runBlocking {
        val jobRepo = TrackingJobRepo()
        val orch = FakeOrchestrator(shouldThrow = true)
        val exec = buildExecutor(orch = orch, jobRepo = jobRepo)

        exec.execute("job-4", "T-400", "BRD")

        orch.called shouldBe true
        jobRepo.lastStatus shouldBe "FAILED"
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun buildExecutor(
        orch: FakeOrchestrator? = null,
        docRepo: DocumentRepository = NoOpDocRepo(),
        jobRepo: com.assistant.server.db.JobRepository = NoOpJobRepo()
    ) = JobExecutor(
        aggregator = TrackingAggregator(),
        documentRepository = docRepo,
        jobRepository = jobRepo,
        settingsRepository = com.assistant.server.document.InMemorySettings(
            mutableMapOf("cli_backend" to "gemini")
        ),
        subprocessOrchestrator = orch
    )
}

// ── Capturing doc repo ──────────────────────────────────────

private class CapturingDocRepo : DocumentRepository {
    val savedDocs = mutableListOf<GeneratedDocument>()
    override suspend fun save(document: GeneratedDocument) { savedDocs.add(document) }
    override suspend fun findByTicketId(ticketId: String) = emptyList<GeneratedDocument>()
    override suspend fun findByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun listByTicketId(ticketId: String) = emptyList<GeneratedDocumentMeta>()
    override suspend fun findLatestByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun findLatestDraftByTicketIdAndType(ticketId: String, documentType: String) = null
    override suspend fun findAllVersions(ticketId: String, documentType: String) = emptyList<GeneratedDocumentMeta>()
    override suspend fun findByVersion(ticketId: String, documentType: String, versionNumber: Int) = null
    override suspend fun updateApprovalStatus(
        id: Long, status: String, reviewedBy: String?, reviewedAt: String?, rejectReason: String?
    ) {}
    override suspend fun getNextVersionNumber(ticketId: String, documentType: String) = 1
    override suspend fun findById(id: Long) = null
}

// ── Tracking job repo (captures last status) ────────────────

private class TrackingJobRepo : com.assistant.server.db.JobRepository {
    var lastStatus: String? = null
    override suspend fun create(job: com.assistant.document.models.GenerationJob) {}
    override suspend fun findById(jobId: String) = null
    override suspend fun findByTicketIdAndTypeActive(ticketId: String, documentType: String) = null
    override suspend fun findActiveByTicketId(ticketId: String) = emptyList<com.assistant.document.models.GenerationJob>()
    override suspend fun findByUser(userId: String, statusFilter: List<String>?) = emptyList<com.assistant.document.models.GenerationJob>()
    override suspend fun findByChainId(chainId: String) = emptyList<com.assistant.document.models.GenerationJob>()
    override suspend fun updateStatus(jobId: String, status: String, progress: Int, phase: String, error: String?) {
        lastStatus = status
    }
    override suspend fun updateStartedAt(jobId: String, startedAt: String) {}
    override suspend fun findRunningJobs() = emptyList<com.assistant.document.models.GenerationJob>()
}
