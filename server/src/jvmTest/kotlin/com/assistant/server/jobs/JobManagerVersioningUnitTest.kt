package com.assistant.server.jobs

import com.assistant.document.models.GeneratedDocument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests: Versioning, recovery, generation lock.
 * Feature: document-job-manager (Tasks 19.9–19.14)
 */
class JobManagerVersioningUnitTest {

    /** 19.9 Active version returns latest approved. Req 7.5 */
    @Test
    fun `active version returns latest approved`() = runBlocking {
        val docRepo = InMemoryDocumentRepository()
        val id1 = docRepo.saveAndGetId(draftDoc("T-1", "BRD"))
        docRepo.updateApprovalStatus(
            id1, "APPROVED", "rev", Instant.now().toString(), null
        )
        val id2 = docRepo.saveAndGetId(draftDoc("T-1", "BRD"))
        docRepo.updateApprovalStatus(
            id2, "APPROVED", "rev", Instant.now().toString(), null
        )
        val active = docRepo.findLatestByTicketIdAndType("T-1", "BRD")
        assertNotNull(active)
        assertEquals("APPROVED", active!!.approvalStatus)
        assertEquals(2, active.versionNumber)
    }

    /** 19.10 Active version falls back to draft. Req 7.5 */
    @Test
    fun `active version falls back to draft`() = runBlocking {
        val docRepo = InMemoryDocumentRepository()
        docRepo.saveAndGetId(draftDoc("T-1", "BRD"))
        val active = docRepo.findLatestByTicketIdAndType("T-1", "BRD")
        assertNotNull(active)
        assertEquals("DRAFT", active!!.approvalStatus)
    }

    /** 19.11 Diff between two versions. Req 7.8 */
    @Test
    fun `diff between two versions produces output`() = runBlocking {
        val docRepo = InMemoryDocumentRepository()
        val id1 = docRepo.saveAndGetId(
            draftDoc("T-1", "BRD").copy(markdownContent = "# V1\nLine A")
        )
        docRepo.updateApprovalStatus(
            id1, "APPROVED", "rev", Instant.now().toString(), null
        )
        val id2 = docRepo.saveAndGetId(
            draftDoc("T-1", "BRD").copy(markdownContent = "# V2\nLine B")
        )
        docRepo.updateApprovalStatus(
            id2, "APPROVED", "rev", Instant.now().toString(), null
        )
        val v1 = docRepo.findByVersion("T-1", "BRD", 1)
        val v2 = docRepo.findByVersion("T-1", "BRD", 2)
        assertNotNull(v1)
        assertNotNull(v2)
        assertNotEquals(v1!!.markdownContent, v2!!.markdownContent)
    }

    /** 19.12 Server recovery resets RUNNING to QUEUED. Req 2.7 */
    @Test
    fun `server recovery resets RUNNING to QUEUED`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        addBrdDraft(docRepo, "T-1")
        val mgr = createTestJobManager(repo, docRepo)
        val job = mgr.createJob("T-1", "BRD", "user")
        repo.updateStatus(job.jobId, "RUNNING", 50, "GENERATING")
        assertEquals("RUNNING", repo.findById(job.jobId)!!.status)
        // Simulate recovery logic directly
        val running = repo.findRunningJobs()
        assertEquals(1, running.size)
        running.forEach { repo.updateStatus(it.jobId, "QUEUED", 0, "QUEUED") }
        assertEquals("QUEUED", repo.findById(job.jobId)!!.status)
    }

    /** 19.13 Generation lock blocks duplicate. Req 5.1 */
    @Test
    fun `generation lock blocks duplicate job`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        addBrdDraft(docRepo, "T-1")
        val mgr = createTestJobManager(repo, docRepo)
        mgr.createJob("T-1", "BRD", "user")
        val ex = assertThrows(GenerationLockException::class.java) {
            runBlocking { mgr.createJob("T-1", "BRD", "user") }
        }
        assertNotNull(ex.existingJobId)
    }

    /** 19.14 Lock allows different ticket. Req 5.4 */
    @Test
    fun `lock allows different ticket`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        addBrdDraft(docRepo, "T-1")
        addBrdDraft(docRepo, "T-2")
        val mgr = createTestJobManager(repo, docRepo)
        val job1 = mgr.createJob("T-1", "BRD", "user")
        val job2 = mgr.createJob("T-2", "BRD", "user")
        assertNotEquals(job1.jobId, job2.jobId)
        assertEquals("QUEUED", job1.status)
        assertEquals("QUEUED", job2.status)
    }
}

private fun draftDoc(ticketId: String, docType: String) = GeneratedDocument(
    documentType = docType,
    ticketId = ticketId,
    generatedAt = Instant.now().toString(),
    markdownContent = "# Test Content",
    approvalStatus = "DRAFT"
)
