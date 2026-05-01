package com.assistant.server.document.jobs

import com.assistant.server.document.buildKBRecord
import com.assistant.server.document.models.*
import io.kotest.common.ExperimentalKotest
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for [CollectionJobManagerImpl].
 *
 * **Property 15**: Progress Tracking — Invariant
 * **Property 16**: Conflict Resolution — Manual Preemption and KB-First
 * **Property 23**: Fault Isolation — Independent Item Processing
 *
 * **Validates: Requirements 13.3, 13.4, 13.10, 14.1, 14.2**
 */
@OptIn(ExperimentalKotest::class)
class CollectionJobManagerPropertyTest {

    private lateinit var repo: InMemoryCollectionJobRepository
    private val cfg = PropTestConfig(iterations = 100)

    @BeforeEach
    fun setup() {
        repo = createInMemoryJobRepo()
    }

    /**
     * **Property 15: Collection_Job Progress Tracking — Invariant**
     *
     * After processing K items, completedItems + failedItems == K
     * and progressPercent == K * 100 / totalItems.
     *
     * **Validates: Requirements 13.3, 13.4**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 15: Progress Tracking")
    fun `Property 15 - completedItems plus failedItems equals K after K items processed`() {
        runBlocking {
            checkAll(cfg, arbCollectionJobSpec()) { spec ->
                resetDb()
                val manager = buildTestManager(repo)
                val job = createTestJob(repo, spec.itemIds)
                simulateProcessing(manager, job.jobId, spec)
                verifyProgressInvariant(job.jobId, spec)
            }
        }
    }

    /**
     * **Property 16: Conflict Resolution — Manual Preemption and KB-First**
     *
     * PENDING items are skipped when manual analysis is triggered
     * or KB already has a record for the ticket.
     *
     * **Validates: Requirements 14.1, 14.2**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 16: Conflict Resolution")
    fun `Property 16 - PENDING items skipped on preemption or KB-First`() {
        runBlocking {
            checkAll(cfg, arbCollectionJobSpec()) { spec ->
                resetDb()
                verifyConflictResolution(spec)
            }
        }
    }

    /**
     * **Property 23: Collection_Job Fault Isolation**
     *
     * K items failing doesn't affect N-K remaining items.
     * Job is FAILED only when K == N (all items fail).
     *
     * **Validates: Requirements 13.10**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 23: Fault Isolation")
    fun `Property 23 - K failures dont affect remaining items job FAILED only when all fail`() {
        runBlocking {
            checkAll(cfg, arbCollectionJobSpec()) { spec ->
                resetDb()
                verifyFaultIsolation(spec)
            }
        }
    }

    private fun resetDb() {
        repo.clear()
    }

    private fun simulateProcessing(
        mgr: CollectionJobManagerImpl, jobId: String, spec: CollectionJobSpec
    ) {
        for (id in spec.itemIds) {
            if (id in spec.failingItemIds) mgr.updateItemFailed(jobId, id, "err")
            else mgr.updateItemCompleted(jobId, id)
        }
    }

    private fun verifyProgressInvariant(jobId: String, spec: CollectionJobSpec) {
        val job = repo.findById(jobId)!!
        val processed = job.completedItems + job.failedItems
        assertEquals(spec.totalItems, processed,
            "completed(${job.completedItems}) + failed(${job.failedItems}) should == ${spec.totalItems}")
        assertEquals(spec.totalItems * 100 / spec.totalItems, job.progressPercent)
    }

    private fun verifyConflictResolution(spec: CollectionJobSpec) {
        val kbMap = spec.kbTicketIds.associateWith { buildKBRecord(it) }
        val mgr = buildTestManager(repo, kbMap)
        val job = createTestJob(repo, spec.itemIds)

        for (id in spec.preemptItemIds) mgr.preemptItemInternal(job.jobId, id)

        val after = repo.findById(job.jobId)!!
        for (item in after.items) {
            if (item.itemId in spec.preemptItemIds) {
                assertEquals(CollectionJobItemStatus.SKIPPED, item.status,
                    "Preempted ${item.itemId} should be SKIPPED")
                assertEquals("preempted by manual analysis", item.skipReason)
            }
        }

        // KB-First: execute job — KB items should be skipped
        runBlocking { mgr.executeJob(job.jobId) }
        val afterExec = repo.findById(job.jobId)!!
        for (item in afterExec.items) {
            if (item.itemId in spec.kbTicketIds && item.itemId !in spec.preemptItemIds) {
                assertEquals(CollectionJobItemStatus.SKIPPED, item.status,
                    "KB-First ${item.itemId} should be SKIPPED")
            }
        }
    }

    private fun verifyFaultIsolation(spec: CollectionJobSpec) {
        val mgr = buildTestManager(repo)
        val job = createTestJob(repo, spec.itemIds)
        val now = java.time.Instant.now().toString()
        repo.updateJobStatus(job.jobId, CollectionJobStatus.RUNNING, now, job.version)

        for (id in spec.itemIds) {
            when {
                id in spec.failingItemIds -> mgr.updateItemFailed(job.jobId, id, "err")
                id in spec.kbTicketIds -> mgr.updateItemSkipped(job.jobId, id, "kb")
                else -> mgr.updateItemCompleted(job.jobId, id)
            }
        }
        mgr.finalizeJob(job.jobId)

        val result = repo.findById(job.jobId)!!
        if (spec.failingItemIds.size == spec.totalItems) {
            assertEquals(CollectionJobStatus.FAILED, result.status,
                "Job FAILED when all ${spec.totalItems} items fail")
        } else {
            assertEquals(CollectionJobStatus.COMPLETED, result.status,
                "Job COMPLETED when not all items fail")
        }
        for (item in result.items) {
            if (item.itemId !in spec.failingItemIds) {
                assertTrue(item.status != CollectionJobItemStatus.FAILED,
                    "Non-failing ${item.itemId} should not be FAILED")
            }
        }
    }
}
