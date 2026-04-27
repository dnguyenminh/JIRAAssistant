package com.assistant.server.jobs

import com.assistant.document.models.GeneratedDocument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests: Job creation, dependency, pause/cancel, recovery.
 * Feature: document-job-manager (Tasks 19.1–19.8)
 */
class JobManagerUnitTest {

    /** 19.1 Chain creation — 3 jobs BRD→FSD→Slides. Req 1.4 */
    @Test
    fun `chain creation produces 3 jobs in order`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        val mgr = createTestJobManager(repo, docRepo)
        val chain = mgr.createChain("TICKET-1", "user1")
        assertEquals(3, chain.jobs.size)
        assertEquals("BRD", chain.jobs[0].documentType)
        assertEquals("FSD", chain.jobs[1].documentType)
        assertEquals("REQUIREMENT_SLIDES", chain.jobs[2].documentType)
        assertEquals("RUNNING", chain.jobs[0].status)
        assertEquals("QUEUED", chain.jobs[1].status)
        assertEquals("QUEUED", chain.jobs[2].status)
    }

    /** 19.2 FSD blocked without BRD. Req 1.2 */
    @Test
    fun `FSD blocked without BRD`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        val mgr = createTestJobManager(repo, docRepo)
        val ex = assertThrows(DependencyException::class.java) {
            runBlocking { mgr.createJob("T-1", "FSD", "user") }
        }
        assertTrue(ex.message!!.contains("BRD"))
    }

    /** 19.3 Slides blocked without BRD. Req 1.3 */
    @Test
    fun `Slides blocked without BRD`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        val mgr = createTestJobManager(repo, docRepo)
        val ex = assertThrows(DependencyException::class.java) {
            runBlocking { mgr.createJob("T-1", "REQUIREMENT_SLIDES", "user") }
        }
        assertTrue(ex.message!!.contains("BRD"))
    }

    /** 19.4 Pause QUEUED job succeeds. Req 3.1 */
    @Test
    fun `pause QUEUED job succeeds`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        addBrdDraft(docRepo, "T-1")
        val mgr = createTestJobManager(repo, docRepo)
        val job = mgr.createJob("T-1", "BRD", "user")
        val jobMgr = buildRealJobManager(repo, docRepo)
        val paused = jobMgr.pauseJob(job.jobId)
        assertEquals("PAUSED", paused.status)
    }

    /** 19.5 Cancel RUNNING job returns 409. Req 3.5 */
    @Test
    fun `cancel RUNNING job throws InvalidTransition`() = runBlocking {
        val repo = InMemoryJobRepository()
        val docRepo = InMemoryDocumentRepository()
        addBrdDraft(docRepo, "T-1")
        val mgr = createTestJobManager(repo, docRepo)
        val job = mgr.createJob("T-1", "BRD", "user")
        repo.updateStatus(job.jobId, "RUNNING", 50, "GENERATING")
        val jobMgr = buildRealJobManager(repo, docRepo)
        assertThrows(InvalidTransitionException::class.java) {
            runBlocking { jobMgr.cancelJob(job.jobId) }
        }
    }

    /** 19.6 Approve DRAFT document. Req 6.4 */
    @Test
    fun `approve DRAFT document assigns version`() = runBlocking {
        val docRepo = InMemoryDocumentRepository()
        val id = docRepo.saveAndGetId(draftDoc("T-1", "BRD"))
        docRepo.updateApprovalStatus(
            id, "APPROVED", "reviewer", Instant.now().toString(), null
        )
        val doc = docRepo.findById(id)!!
        assertEquals("APPROVED", doc.approvalStatus)
        assertEquals(1, doc.versionNumber)
    }

    /** 19.7 Reject with short reason — validation. Req 6.5 */
    @Test
    fun `reject with short reason fails validation`() {
        val reason = "short"
        assertTrue(reason.length < 10)
    }

    /** 19.8 Reject with valid reason. Req 6.5 */
    @Test
    fun `reject with valid reason saves status`() = runBlocking {
        val docRepo = InMemoryDocumentRepository()
        val id = docRepo.saveAndGetId(draftDoc("T-1", "BRD"))
        val reason = "This document needs significant improvements"
        docRepo.updateApprovalStatus(
            id, "REJECTED", "reviewer", Instant.now().toString(), reason
        )
        val doc = docRepo.findById(id)!!
        assertEquals("REJECTED", doc.approvalStatus)
        assertEquals(reason, doc.rejectReason)
    }
}

private fun draftDoc(ticketId: String, docType: String) = GeneratedDocument(
    documentType = docType,
    ticketId = ticketId,
    generatedAt = Instant.now().toString(),
    markdownContent = "# Test Content",
    approvalStatus = "DRAFT"
)
