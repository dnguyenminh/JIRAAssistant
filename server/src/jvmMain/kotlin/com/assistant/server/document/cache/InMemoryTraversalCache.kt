package com.assistant.server.document.cache

import com.assistant.server.document.models.TicketGraph
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of [TraversalCache] for runtime use.
 *
 * Stores cached graphs in a ConcurrentHashMap with TTL validation.
 * Suitable for single-instance deployments; for multi-instance,
 * replace with a DB-backed implementation.
 *
 * Requirements: 15.1, 15.2, 15.3, 15.4, 15.5
 */
class InMemoryTraversalCache : TraversalCache {

    private val logger = LoggerFactory.getLogger(InMemoryTraversalCache::class.java)

    private data class CacheEntry(
        val graph: TicketGraph,
        val cachedAt: Instant
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override suspend fun get(rootTicketId: String, cacheTtlMinutes: Int): TicketGraph? {
        val entry = cache[rootTicketId] ?: return null
        val expiry = entry.cachedAt.plus(cacheTtlMinutes.toLong(), ChronoUnit.MINUTES)
        if (Instant.now().isAfter(expiry)) {
            cache.remove(rootTicketId)
            logger.info("Cache expired for {} (TTL {}m)", rootTicketId, cacheTtlMinutes)
            return null
        }
        val minutesAgo = ChronoUnit.MINUTES.between(entry.cachedAt, Instant.now())
        logger.info(
            "Reusing cached TicketGraph for {} (cached {}m ago, {} nodes)",
            rootTicketId, minutesAgo, entry.graph.nodes.size
        )
        return entry.graph
    }

    override suspend fun put(rootTicketId: String, graph: TicketGraph) {
        cache[rootTicketId] = CacheEntry(graph, Instant.now())
        logger.info("Cached TicketGraph for {} ({} nodes)", rootTicketId, graph.nodes.size)
    }

    override suspend fun invalidate(rootTicketId: String) {
        cache.remove(rootTicketId)
        logger.info("Invalidated cache for {}", rootTicketId)
    }
}
