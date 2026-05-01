package com.assistant.server.document.jobs

import com.assistant.kb.KBRecord
import com.assistant.server.document.FakeKBRepository
import com.assistant.server.document.NoOpScanLogRepository
import com.assistant.server.document.models.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlinx.coroutines.sync.Semaphore
import java.time.Instant

/**
 * Test infrastructure for CollectionJobManager property tests.
 *
 * Provides in-memory database setup, fake dependencies, and
 * Kotest generators for random CollectionJob scenarios.
 *
 * Requirements: 13.3, 13.4, 13.10, 14.1, 14.2
 */

/** Create an in-memory [CollectionJobRepository] for tests. */
fun createInMemoryJobRepo(): InMemoryCollectionJobRepository {
    return InMemoryCollectionJobRepository()
}

/** Build a [CollectionJobManagerImpl] with fake dependencies. */
fun buildTestManager(
    repo: CollectionJobRepository,
    kbRecords: Map<String, KBRecord> = emptyMap()
): CollectionJobManagerImpl = CollectionJobManagerImpl(
    collectionJobRepository = repo,
    kbRepository = FakeKBRepository(kbRecords),
    scanLogRepository = NoOpScanLogRepository(),
    attachmentPipeline = noOpAttachmentPipeline(),
    aiAnalysisSemaphore = Semaphore(5),
    jiraApiSemaphore = Semaphore(5)
)

/** Create and persist a QUEUED job with N PENDING items. */
fun createTestJob(
    repo: CollectionJobRepository,
    itemIds: List<String>,
    jobType: CollectionJobType = CollectionJobType.LINKED_TICKET_ANALYSIS
): CollectionJob {
    val now = Instant.now().toString()
    val items = itemIds.map { CollectionJobItem(itemId = it) }
    val job = CollectionJob(
        jobId = "job-${System.nanoTime()}",
        parentTicketId = "ROOT-1",
        jobType = jobType,
        status = CollectionJobStatus.QUEUED,
        totalItems = items.size,
        items = items,
        createdAt = now,
        updatedAt = now
    )
    repo.save(job)
    return job
}

/** Spec describing a random collection job for property tests. */
data class CollectionJobSpec(
    val totalItems: Int,
    val itemIds: List<String>,
    val kbTicketIds: Set<String>,
    val failingItemIds: Set<String>,
    val preemptItemIds: Set<String>
)

/** Generator for random collection job specs with 1..12 items. */
fun arbCollectionJobSpec(): Arb<CollectionJobSpec> = arbitrary {
    val n = Arb.int(1..12).bind()
    val ids = (1..n).map { "ITEM-$it" }
    val kbCount = Arb.int(0..n / 3).bind()
    val kbIds = ids.shuffled().take(kbCount).toSet()
    val remaining = ids.filter { it !in kbIds }
    val failCount = Arb.int(0..remaining.size).bind()
    val failIds = remaining.shuffled().take(failCount).toSet()
    val pendingIds = remaining.filter { it !in failIds }
    val preemptCount = Arb.int(0..pendingIds.size).bind()
    val preemptIds = pendingIds.shuffled().take(preemptCount).toSet()
    CollectionJobSpec(n, ids, kbIds, failIds, preemptIds)
}
