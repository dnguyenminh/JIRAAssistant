package com.assistant.server.document.cache

import com.assistant.db.JiraDatabase
import com.assistant.server.document.models.TicketGraph
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for traversal cache persistence backed by SQLDelight.
 *
 * Serializes [TicketGraph] to/from JSON for the `graph_json` column.
 * Provides get, put, and invalidate operations on the `traversal_cache` table.
 *
 * Requirements: 15.1
 */
class TraversalCacheRepository(
    private val database: JiraDatabase
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Retrieve a cached entry by root ticket ID, or null if not found. */
    fun get(rootTicketId: String): CacheEntry? {
        return database.knowledgeBaseQueries
            .findTraversalCache(rootTicketId)
            .executeAsOneOrNull()
            ?.let { row ->
                CacheEntry(
                    rootTicketId = row.root_ticket_id,
                    graph = deserializeGraph(row.graph_json),
                    cachedAt = row.cached_at,
                    rootUpdatedAt = row.root_updated_at
                )
            }
    }

    /** Insert or replace a cache entry for the given root ticket. */
    fun put(
        rootTicketId: String,
        graph: TicketGraph,
        cachedAt: String,
        rootUpdatedAt: String
    ) {
        database.knowledgeBaseQueries.insertOrReplaceTraversalCache(
            root_ticket_id = rootTicketId,
            graph_json = json.encodeToString(graph),
            cached_at = cachedAt,
            root_updated_at = rootUpdatedAt
        )
    }

    /** Delete the cache entry for the given root ticket. */
    fun invalidate(rootTicketId: String) {
        database.knowledgeBaseQueries.deleteTraversalCache(rootTicketId)
    }

    private fun deserializeGraph(raw: String): TicketGraph? {
        return try {
            json.decodeFromString<TicketGraph>(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** Lightweight data holder for a cached traversal entry. */
    data class CacheEntry(
        val rootTicketId: String,
        val graph: TicketGraph?,
        val cachedAt: String,
        val rootUpdatedAt: String
    )
}
