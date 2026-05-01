package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.document.models.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * BFS traversal engine that builds a [TicketGraph] from a root ticket.
 *
 * - Cycle detection via visitedSet
 * - Concurrent fetch per BFS level (semaphore-limited)
 * - Priority ordering: parent > blocking > other links > sub-tasks > text refs
 * - Respects max_depth, max_tickets, total_timeout
 * - Early termination when data > 3× maxPromptChars
 * - 403 tracking (count only, no IDs exposed)
 *
 * Requirements: 1.1–1.10, 7.4, 7.6, 10.1–10.3, 10.5
 */
class TraversalEngine(
    private val ticketFetcher: TicketFetcher,
    private val config: TraversalConfig,
    private val jiraApiSemaphore: Semaphore
) {
    private val logger = LoggerFactory.getLogger(TraversalEngine::class.java)

    /** BFS traversal from [rootTicketId]. Returns a [TicketGraph]. */
    suspend fun traverse(rootTicketId: String): TicketGraph {
        val startTime = System.currentTimeMillis()
        val state = TraversalState(config)

        val rootContent = fetchRoot(rootTicketId)
        val rootNode = createRootNode(rootTicketId, rootContent)
        state.addNode(rootNode)
        state.updateDataSize(rootContent)
        logger.debug("Fetched root ticket {} at depth 0", rootTicketId)

        enqueueDiscoveries(rootContent, rootTicketId, 1, state)
        bfsLoop(state, startTime)
        return buildGraph(rootTicketId, state, startTime)
    }

    /** Compute relevance score for a [TicketNode]. */
    internal fun computeRelevanceScore(node: TicketNode): Double =
        RelevanceScorer.compute(node)

    private suspend fun fetchRoot(rootTicketId: String): StructuredTicketContent {
        val result = ticketFetcher.fetch(rootTicketId)
        if (result !is FetchResult.Success) {
            throw IllegalStateException("Failed to fetch root ticket $rootTicketId")
        }
        return result.content
    }

    /** Main BFS loop — one depth level per iteration. */
    private suspend fun bfsLoop(state: TraversalState, startTime: Long) {
        while (state.hasWork()) {
            coroutineContext.ensureActive()
            if (shouldStop(state, startTime)) break
            processNextLevel(state, startTime)
        }
    }

    private fun shouldStop(state: TraversalState, startTime: Long): Boolean {
        if (isTimedOut(startTime)) { logTimeout(state, startTime); return true }
        if (state.isMaxTicketsReached()) return true
        if (state.isEarlyTermination()) { logEarlyTermination(state); return true }
        return false
    }

    /** Process all tickets at the current BFS depth level concurrently. */
    private suspend fun processNextLevel(state: TraversalState, startTime: Long) {
        coroutineContext.ensureActive()
        val levelItems = state.dequeueCurrentLevel()
        if (levelItems.isEmpty()) return
        val results = fetchConcurrently(levelItems, state)
        for ((item, result) in levelItems.zip(results)) {
            processResult(item, result, state)
            if (shouldStop(state, startTime)) break
        }
    }

    /** Fetch tickets concurrently, limited by jiraApiSemaphore. */
    private suspend fun fetchConcurrently(
        items: List<BfsQueueItem>,
        state: TraversalState
    ): List<FetchResult> = coroutineScope {
        items.map { item ->
            async {
                if (state.isVisited(item.ticketId)) {
                    return@async FetchResult.Failed(item.ticketId, "Already visited")
                }
                jiraApiSemaphore.withPermit { ticketFetcher.fetch(item.ticketId) }
            }
        }.map { it.await() }
    }

    private fun processResult(item: BfsQueueItem, result: FetchResult, state: TraversalState) {
        when (result) {
            is FetchResult.Success -> handleSuccess(item, result, state)
            is FetchResult.PermissionDenied -> state.incrementPermissionDenied()
            is FetchResult.Failed -> {
                logger.warn("Skipped ticket {}: {}", item.ticketId, result.error)
                state.addSkipped(item.ticketId)
            }
        }
    }

    private fun handleSuccess(item: BfsQueueItem, result: FetchResult.Success, state: TraversalState) {
        val node = TicketNode(
            ticketId = item.ticketId, depth = item.depth,
            discoveredVia = item.relationshipType, parentDiscoveryId = item.parentId,
            issue = result.content
        )
        val scored = node.copy(relevanceScore = computeRelevanceScore(node))
        state.addNode(scored)
        state.updateDataSize(result.content)
        state.addEdge(item.parentId, item.ticketId, item.relationshipType, item.linkDescription)

        val newCount = enqueueDiscoveries(result.content, item.ticketId, item.depth + 1, state)
        logger.debug("Fetched ticket {} at depth {}, discovered {} new related tickets",
            item.ticketId, item.depth, newCount)
    }

    private fun enqueueDiscoveries(
        content: StructuredTicketContent, parentId: String, depth: Int, state: TraversalState
    ): Int {
        if (depth > config.maxDepth) return 0
        val discoveries = RelatedTicketDiscovery.discover(
            content, parentId, state.visitedIds(), config.projectScope
        )
        for (d in discoveries) {
            state.enqueue(BfsQueueItem(d.ticketId, depth, d.relationshipType, parentId, ""))
        }
        return discoveries.size
    }

    private fun createRootNode(ticketId: String, content: StructuredTicketContent): TicketNode {
        val node = TicketNode(ticketId, 0, RelationshipType.ROOT, ticketId, content)
        return node.copy(relevanceScore = computeRelevanceScore(node))
    }

    private fun isTimedOut(startTime: Long): Boolean =
        (System.currentTimeMillis() - startTime) >= config.totalTimeoutMs

    private fun logTimeout(state: TraversalState, startTime: Long) {
        val elapsed = System.currentTimeMillis() - startTime
        logger.warn("Traversal timeout after {}ms, collected {}/{} tickets",
            elapsed, state.nodeCount(), config.maxTickets)
    }

    private fun logEarlyTermination(state: TraversalState) {
        val limit = config.maxPromptChars * 3
        logger.info("Early termination: collected data ({} chars) exceeds 3× prompt capacity ({} chars)",
            state.dataSize(), limit)
    }

    private fun buildGraph(rootId: String, state: TraversalState, startTime: Long): TicketGraph {
        val elapsed = System.currentTimeMillis() - startTime
        return TicketGraph(
            rootTicketId = rootId,
            nodes = state.nodes(),
            edges = state.edges(),
            metadata = TraversalMetadata(
                totalDiscovered = state.totalDiscovered(),
                totalFetched = state.nodeCount(),
                totalSkipped = state.skippedCount(),
                maxDepthReached = state.maxDepthReached(),
                traversalTimeMs = elapsed,
                skippedTicketIds = state.skippedIds(),
                permissionDeniedCount = state.permissionDeniedCount(),
                earlyTerminated = state.isEarlyTermination()
            )
        )
    }
}
