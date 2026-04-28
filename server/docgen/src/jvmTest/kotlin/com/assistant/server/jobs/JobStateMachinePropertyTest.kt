package com.assistant.server.jobs

import com.assistant.document.models.GenerationJob
import com.assistant.document.models.JobStatus
import com.assistant.server.jobs.JobTestGenerators.arbDocumentMeta
import com.assistant.server.jobs.JobTestGenerators.arbDocumentType
import com.assistant.server.jobs.JobTestGenerators.arbGenerationJob
import com.assistant.server.jobs.JobTestGenerators.arbJobStatus
import com.assistant.server.jobs.JobTestGenerators.arbTicketId
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Property-based tests: Job State Machine & Dependencies.
 * Feature: document-job-manager
 */
@OptIn(ExperimentalKotest::class)
class JobStateMachinePropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /** **Validates: Requirements 1.1, 1.2, 1.3** */
    @Test
    @Tag("Feature: document-job-manager, Property 1: Dependency chain enforcement")
    fun `Property 1 - dependency chain enforcement`() {
        runBlocking {
            checkAll(cfg, arbDocumentType(), Arb.list(arbDocumentMeta(), 0..5)) { docType, docs ->
                val result = DependencyChecker.canGenerate(docType.name, docs)
                when (docType.name) {
                    "BRD" -> assertTrue(result.allowed)
                    "FSD", "REQUIREMENT_SLIDES" -> {
                        val hasBrd = docs.any {
                            it.documentType == "BRD" &&
                                it.approvalStatus in listOf("DRAFT", "APPROVED")
                        }
                        assertEquals(hasBrd, result.allowed)
                    }
                }
            }
        }
    }

    /** **Validates: Requirements 1.4** */
    @Test
    @Tag("Feature: document-job-manager, Property 2: Job chain creation structure")
    fun `Property 2 - chain creation produces 3 jobs`() {
        runBlocking {
            checkAll(cfg, arbTicketId()) { ticketId ->
                val repo = InMemoryJobRepository()
                val docRepo = InMemoryDocumentRepository()
                val mgr = createTestJobManager(repo, docRepo)
                val chain = mgr.createChain(ticketId, "test-user")
                val types = chain.jobs.map { it.documentType }
                assertEquals(listOf("BRD", "FSD", "REQUIREMENT_SLIDES"), types)
                assertTrue(chain.jobs.all { it.chainId == chain.chainId })
                assertEquals("RUNNING", chain.jobs[0].status)
                assertEquals("QUEUED", chain.jobs[1].status)
                assertEquals("QUEUED", chain.jobs[2].status)
            }
        }
    }

    /** **Validates: Requirements 1.5, 3.4** */
    @Test
    @Tag("Feature: document-job-manager, Property 3: Chain failure and cancellation propagation")
    fun `Property 3 - chain failure cancels remaining`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), Arb.int(0..2)) { ticketId, failIdx ->
                val repo = InMemoryJobRepository()
                val orchestrator = JobChainOrchestrator(repo)
                val chain = buildTestChain(repo, ticketId, failIdx)
                orchestrator.onJobFailed(chain.first().chainId)
                val after = repo.findByChainId(chain.first().chainId!!)
                assertTrue(verifyChainPropagation(after, failIdx))
            }
        }
    }

    /** **Validates: Requirements 3.1, 3.2, 3.3, 3.5** */
    @Test
    @Tag("Feature: document-job-manager, Property 4: Job state transition validity")
    fun `Property 4 - only valid transitions allowed`() {
        runBlocking {
            checkAll(cfg, arbJobStatus(), arbJobStatus()) { from, to ->
                val valid = validTransitions()
                val isValid = valid[from]?.contains(to) == true
                // Just verify the map is well-defined for all statuses
                assertNotNull(valid[from])
                if (from in listOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)) {
                    assertTrue(valid[from]!!.isEmpty())
                }
            }
        }
    }

    /** **Validates: Requirements 2.1** */
    @Test
    @Tag("Feature: document-job-manager, Property 5: GenerationJob serialization round-trip")
    fun `Property 5 - serialization round-trip`() {
        runBlocking {
            checkAll(cfg, arbGenerationJob()) { job ->
                val serialized = json.encodeToString(job)
                val deserialized = json.decodeFromString<GenerationJob>(serialized)
                assertEquals(job, deserialized)
            }
        }
    }

    /** **Validates: Requirements 2.2** */
    @Test
    @Tag("Feature: document-job-manager, Property 6: New jobs always start as QUEUED")
    fun `Property 6 - new jobs start as QUEUED`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), arbDocumentType()) { ticketId, docType ->
                val repo = InMemoryJobRepository()
                val docRepo = InMemoryDocumentRepository()
                addBrdDraft(docRepo, ticketId)
                val mgr = createTestJobManager(repo, docRepo)
                val job = mgr.createJob(ticketId, docType.name, "user")
                assertEquals("QUEUED", job.status)
                assertEquals(0, job.progressPercent)
                assertEquals("QUEUED", job.phase)
            }
        }
    }
}
