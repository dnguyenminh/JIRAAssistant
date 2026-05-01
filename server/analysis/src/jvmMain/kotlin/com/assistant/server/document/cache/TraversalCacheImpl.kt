package com.assistant.server.document.cache

import com.assistant.jira.JiraClient
import com.assistant.server.document.models.TicketGraph
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * DB-backed implementation of [TraversalCache] with TTL validation.
 *
 * Cache is valid when:
 * - `now - cachedAt < cacheTtlMinutes`
 * - Root ticket `updated_at <= cachedAt` (lightweight Jira check)
 *
 * Requirements: 15.1, 15.2, 15.3, 15.4, 15.5
 */
class TraversalCacheImpl(
    private val cacheRepository: TraversalCacheRepository,
    private val jiraClient: JiraClient
) : TraversalCache {

    private val logger = LoggerFactory.getLogger(TraversalCacheImpl::class.java)

    override suspend fun get(
        rootTicketId: String,
        cacheTtlMinutes: Int
    ): TicketGraph? {
        val entry = cacheRepository.get(rootTicketId) ?: return null
        val graph = entry.graph ?: return null

        if (isTtlExpired(entry.cachedAt, cacheTtlMinutes)) {
            logger.info("Cache expired for {} (TTL {}m)", rootTicketId, cacheTtlMinutes)
            return null
        }

        if (isRootUpdated(rootTicketId, entry.cachedAt)) {
            logger.info("Cache invalidated for {} — root ticket updated", rootTicketId)
            cacheRepository.invalidate(rootTicketId)
            return null
        }

        val minutesAgo = minutesSince(entry.cachedAt)
        logger.info(
            "Reusing cached TicketGraph for {} (cached {}m ago, {} nodes)",
            rootTicketId, minutesAgo, graph.nodes.size
        )
        return graph
    }

    override suspend fun put(rootTicketId: String, graph: TicketGraph) {
        val now = Instant.now().toString()
        val rootUpdatedAt = extractRootUpdatedAt(graph)
        cacheRepository.put(rootTicketId, graph, now, rootUpdatedAt)
        logger.info(
            "Cached TicketGraph for {} ({} nodes)",
            rootTicketId, graph.nodes.size
        )
    }

    override suspend fun invalidate(rootTicketId: String) {
        cacheRepository.invalidate(rootTicketId)
        logger.info("Invalidated cache for {}", rootTicketId)
    }

    // ── Internal helpers ─────────────────────────────────────────

    internal fun isTtlExpired(cachedAt: String, ttlMinutes: Int): Boolean {
        return try {
            val cached = Instant.parse(cachedAt)
            val expiry = cached.plus(ttlMinutes.toLong(), ChronoUnit.MINUTES)
            Instant.now().isAfter(expiry)
        } catch (_: Exception) {
            true
        }
    }

    private suspend fun isRootUpdated(
        rootTicketId: String,
        cachedAt: String
    ): Boolean {
        return try {
            val issue = jiraClient.getIssueDetails(rootTicketId) ?: return true
            val jiraUpdated = issue.fields.updated ?: return false
            val cachedInstant = Instant.parse(cachedAt)
            val updatedInstant = Instant.parse(jiraUpdated)
            updatedInstant.isAfter(cachedInstant)
        } catch (_: Exception) {
            false // On error, assume cache is still valid
        }
    }

    private fun minutesSince(cachedAt: String): Long {
        return try {
            ChronoUnit.MINUTES.between(Instant.parse(cachedAt), Instant.now())
        } catch (_: Exception) {
            -1
        }
    }

    private fun extractRootUpdatedAt(graph: TicketGraph): String {
        val rootNode = graph.nodes[graph.rootTicketId]
        return rootNode?.issue?.updatedDate?.ifBlank { null }
            ?: Instant.now().toString()
    }
}
