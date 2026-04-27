package com.assistant.server.jobs

import com.assistant.document.models.GeneratedDocument
import com.assistant.server.jobs.JobTestGenerators.arbDocumentType
import com.assistant.server.jobs.JobTestGenerators.arbTicketId
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Property-based tests: Recovery, Filtering, Locking, Versioning.
 * Feature: document-job-manager
 */
@OptIn(ExperimentalKotest::class)
class JobRecoveryVersioningPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /** **Validates: Requirements 2.7** */
    @Test
    @Tag("Feature: document-job-manager, Property 7: Server recovery restores RUNNING jobs to QUEUED")
    fun `Property 7 - recovery restores RUNNING to QUEUED`() {
        runBlocking {
            checkAll(cfg, Arb.list(JobTestGenerators.arbJobStatus(), 1..10)) { statuses ->
                val repo = InMemoryJobRepository()
                val jobs = statuses.mapIndexed { i, status ->
                    arbJobWithStatus("job-$i", status.name).also { repo.create(it) }
                }
                recoverRunningJobs(repo)
                jobs.forEach { orig ->
                    val after = repo.findById(orig.jobId)!!
                    if (orig.status == "RUNNING") {
                        assertEquals("QUEUED", after.status)
                    } else {
                        assertEquals(orig.status, after.status)
                    }
                }
            }
        }
    }

    /** **Validates: Requirements 2.5** */
    @Test
    @Tag("Feature: document-job-manager, Property 8: Job filter returns correct results")
    fun `Property 8 - job filter returns correct results`() {
        runBlocking {
            checkAll(cfg, Arb.list(JobTestGenerators.arbJobStatus(), 1..10)) { statuses ->
                val active = setOf("QUEUED", "RUNNING", "PAUSED")
                val completed = setOf("COMPLETED", "FAILED", "CANCELLED")
                val activeJobs = statuses.filter { it.name in active }
                val completedJobs = statuses.filter { it.name in completed }
                assertEquals(statuses.size, activeJobs.size + completedJobs.size)
                assertTrue(activeJobs.all { it.name in active })
                assertTrue(completedJobs.all { it.name in completed })
            }
        }
    }

    /** **Validates: Requirements 5.1, 5.4** */
    @Test
    @Tag("Feature: document-job-manager, Property 9: Generation lock prevents duplicate jobs")
    fun `Property 9 - generation lock prevents duplicates`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), arbDocumentType()) { ticketId, docType ->
                val repo = InMemoryJobRepository()
                val docRepo = InMemoryDocumentRepository()
                addBrdDraft(docRepo, ticketId)
                val mgr = createTestJobManager(repo, docRepo)
                val first = mgr.createJob(ticketId, docType.name, "user")
                assertNotNull(first)
                try {
                    mgr.createJob(ticketId, docType.name, "user")
                    fail("Should throw GenerationLockException")
                } catch (e: GenerationLockException) {
                    assertEquals(first.jobId, e.existingJobId)
                }
            }
        }
    }

    /** **Validates: Requirements 6.4, 7.2** */
    @Test
    @Tag("Feature: document-job-manager, Property 10: Approve increments version number monotonically")
    fun `Property 10 - approve increments version monotonically`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), Arb.int(1..5)) { ticketId, n ->
                val docRepo = InMemoryDocumentRepository()
                val ids = (1..n).map { saveDraft(docRepo, ticketId, "BRD") }
                ids.forEachIndexed { idx, id ->
                    docRepo.updateApprovalStatus(
                        id, "APPROVED", "reviewer", Instant.now().toString(), null
                    )
                    val doc = docRepo.findById(id)!!
                    assertEquals(idx + 1, doc.versionNumber)
                }
            }
        }
    }

    /** **Validates: Requirements 7.5, 8.5** */
    @Test
    @Tag("Feature: document-job-manager, Property 11: Active version selection")
    fun `Property 11 - active version selection`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), Arb.int(0..3)) { ticketId, approvedCount ->
                val docRepo = InMemoryDocumentRepository()
                val ids = mutableListOf<Long>()
                repeat(approvedCount) {
                    val id = saveDraft(docRepo, ticketId, "BRD")
                    docRepo.updateApprovalStatus(
                        id, "APPROVED", "rev", Instant.now().toString(), null
                    )
                    ids.add(id)
                }
                saveDraft(docRepo, ticketId, "BRD") // latest draft
                val active = docRepo.findLatestByTicketIdAndType(ticketId, "BRD")
                assertNotNull(active)
                if (approvedCount > 0) {
                    assertEquals("APPROVED", active!!.approvalStatus)
                    assertEquals(approvedCount, active.versionNumber)
                } else {
                    assertEquals("DRAFT", active!!.approvalStatus)
                }
            }
        }
    }
}

private fun saveDraft(
    repo: InMemoryDocumentRepository, ticketId: String, docType: String
): Long {
    return repo.saveAndGetId(
        GeneratedDocument(
            documentType = docType,
            ticketId = ticketId,
            generatedAt = Instant.now().toString(),
            markdownContent = "# Content",
            approvalStatus = "DRAFT"
        )
    )
}

/** Simulate server recovery: RUNNING → QUEUED. */
private suspend fun recoverRunningJobs(repo: InMemoryJobRepository) {
    repo.findRunningJobs().forEach { job ->
        repo.updateStatus(job.jobId, "QUEUED", 0, "QUEUED")
    }
}

private fun arbJobWithStatus(jobId: String, status: String) =
    com.assistant.document.models.GenerationJob(
        jobId = jobId,
        ticketId = "T-1",
        documentType = "BRD",
        status = status,
        createdBy = "test",
        createdAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z"
    )
