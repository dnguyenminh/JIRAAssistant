package com.assistant.server.document

import com.assistant.document.DocumentAggregator
import com.assistant.document.models.GenerationContext
import com.assistant.jira.JiraClient
import com.assistant.kb.KBRepository
import com.assistant.scan.ScanLogRepository
import com.assistant.server.attachment.VectorStore
import com.assistant.server.document.cache.TraversalCache
import com.assistant.server.document.jobs.CollectionJobManager
import com.assistant.server.document.models.TraversalConfig
import com.assistant.server.document.security.RateLimiter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Deep ticket data collection orchestrator — replaces [DocumentAggregatorImpl].
 *
 * Phases:
 * 0. Rate limit check + cache check
 * 1. BFS Traversal via [TraversalEngine] (progress 5-25%)
 * 2. Collect comments + attachments (progress 25-30%)
 * 3. Build [EnrichedContext] + create Collection_Jobs
 *
 * Implements collection-level lock to coalesce concurrent requests
 * for the same root ticket (Req 10.7).
 *
 * Requirements: 1.8, 5.3, 8.1, 8.2, 8.3, 8.5, 8.6, 10.4, 10.6,
 *              10.7, 11.1–11.6, 12.3, 12.5
 */
class DeepCollector(
    internal val jiraClientProvider: () -> JiraClient,
    internal val kbRepository: KBRepository,
    internal val vectorStore: VectorStore,
    internal val scanLogRepository: ScanLogRepository,
    internal val configProvider: () -> TraversalConfig,
    internal val traversalCache: TraversalCache,
    internal val rateLimiter: RateLimiter,
    internal val collectionJobManager: CollectionJobManager,
    internal val jiraApiSemaphore: Semaphore,
    internal val aiAnalysisSemaphore: Semaphore
) : DocumentAggregator {

    internal val logger = LoggerFactory.getLogger(DeepCollector::class.java)

    /** Coalesce concurrent requests for the same root ticket (Req 10.7). */
    internal val activeLocks = ConcurrentHashMap<String, Deferred<GenerationContext>>()

    /** DocumentAggregator interface — delegates to aggregate with no progress callback. */
    override suspend fun aggregate(ticketId: String): GenerationContext =
        aggregate(ticketId, progressCallback = null)

    /**
     * Aggregate with optional progress callback for document-job-manager (Req 8.3).
     * Maps to AGGREGATING_DATA phase (0-30%).
     */
    suspend fun aggregate(
        ticketId: String,
        progressCallback: ((progressPercent: Int, phase: String) -> Unit)? = null
    ): GenerationContext {
        return coalesceOrExecute(ticketId) {
            executeCollection(ticketId, progressCallback)
        }
    }

    /**
     * Coalesce pattern: if another coroutine is already collecting for the same
     * ticket, wait for its result instead of running a duplicate traversal.
     */
    private suspend fun coalesceOrExecute(
        ticketId: String,
        block: suspend () -> GenerationContext
    ): GenerationContext {
        val existing = activeLocks[ticketId]
        if (existing != null) {
            logger.info("Coalescing request for ticket {} — waiting for active collection", ticketId)
            return existing.await()
        }

        val deferred = CompletableDeferred<GenerationContext>()
        val previous = activeLocks.putIfAbsent(ticketId, deferred)
        if (previous != null) {
            logger.info("Coalescing request for ticket {} — waiting for active collection", ticketId)
            return previous.await()
        }

        return try {
            val result = block()
            deferred.complete(result)
            result
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            activeLocks.remove(ticketId)
        }
    }
}
