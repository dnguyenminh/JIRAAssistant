package com.assistant.server.document.cache

import com.assistant.server.document.models.TicketGraph

/**
 * Cache for [TicketGraph] results to avoid redundant BFS traversals.
 *
 * Implementations should check TTL and root ticket updated_at
 * to determine cache validity.
 *
 * Requirements: 15.1, 15.2, 15.3, 15.4, 15.5
 */
interface TraversalCache {
    /**
     * Retrieve cached graph if still valid (within TTL and root unchanged).
     * @return cached [TicketGraph] or null if cache miss / expired.
     */
    suspend fun get(rootTicketId: String, cacheTtlMinutes: Int): TicketGraph?

    /** Store a traversal result in the cache. */
    suspend fun put(rootTicketId: String, graph: TicketGraph)

    /** Invalidate cache for a specific root ticket (e.g. on RE-ANALYZE). */
    suspend fun invalidate(rootTicketId: String)
}
