package com.assistant.server.document.models

import kotlinx.serialization.Serializable

/**
 * Configuration for deep ticket traversal.
 *
 * Controls BFS depth, ticket limits, timeouts, concurrency, and caching behavior.
 * Use [validated] to clamp values into their allowed ranges before use.
 *
 * Requirements: 7.1, 7.3, 3.7, 15.2, 1.2, 1.5
 */
@Serializable
data class TraversalConfig(
    /** BFS max depth from root ticket. Clamped to 1..20. */
    val maxDepth: Int = 5,
    /** Maximum tickets to collect. Clamped to 1..1000. */
    val maxTickets: Int = 50,
    /** Project keys allowed for traversal. Empty = all projects. */
    val projectScope: List<String> = emptyList(),
    /** Timeout per Jira API request in milliseconds. */
    val requestTimeoutMs: Long = 10_000,
    /** Total timeout for the entire traversal in milliseconds. */
    val totalTimeoutMs: Long = 120_000,
    /** Max concurrent Jira API calls per BFS level. */
    val maxConcurrency: Int = 5,
    /** Page size for Jira comment pagination. */
    val commentPageSize: Int = 50,
    /** Max prompt size in characters for early termination. */
    val maxPromptChars: Int = 100_000,
    /** Max comments per ticket. Clamped to 10..1000. (Req 3.7) */
    val maxCommentsPerTicket: Int = 200,
    /** Cache TTL in minutes. Clamped to 5..1440. (Req 15.2) */
    val cacheTtlMinutes: Int = 60,
    /** When true, skip early termination based on data size. Used in map-reduce mode. (Req 1.5) */
    val disableEarlyTermination: Boolean = false
) {

    /**
     * Returns a copy with all clamped fields coerced into valid ranges.
     *
     * - [maxDepth]: 1..20
     * - [maxTickets]: 1..1000
     * - [maxCommentsPerTicket]: 10..1000
     * - [cacheTtlMinutes]: 5..1440
     */
    fun validated(): TraversalConfig = copy(
        maxDepth = maxDepth.coerceIn(1, 20),
        maxTickets = maxTickets.coerceIn(1, 1000),
        maxCommentsPerTicket = maxCommentsPerTicket.coerceIn(10, 1000),
        cacheTtlMinutes = cacheTtlMinutes.coerceIn(5, 1440)
    )
}
