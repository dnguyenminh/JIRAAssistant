package com.assistant.server.document.security

import com.assistant.db.JiraDatabase
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Repository for deep collection rate limit persistence backed by SQLDelight.
 *
 * Tracks per-user request timestamps in the `deep_collection_rate_limits` table.
 * Provides count, record, and cleanup operations.
 *
 * Requirements: 16.1
 */
class RateLimitRepository(
    private val database: JiraDatabase
) {
    /** Count requests by [userId] in the last hour. */
    fun countInLastHour(userId: String): Long {
        val oneHourAgo = Instant.now()
            .minus(1, ChronoUnit.HOURS)
            .toString()
        return database.knowledgeBaseQueries
            .countRateLimitsByUser(userId, oneHourAgo)
            .executeAsOne()
    }

    private var counter = 0L

    /** Record a new request for [userId] at the current timestamp. */
    fun record(userId: String) {
        // Append monotonic counter to avoid PK collision on rapid calls.
        // The counter suffix is after the ISO timestamp so lexicographic
        // ordering used by countInLastHour still works correctly.
        val ts = Instant.now().toString() + "_" + counter++
        database.knowledgeBaseQueries.insertRateLimit(userId, ts)
    }

    /** Delete all rate limit entries older than 1 hour. */
    fun cleanup() {
        val oneHourAgo = Instant.now()
            .minus(1, ChronoUnit.HOURS)
            .toString()
        database.knowledgeBaseQueries.deleteExpiredRateLimits(oneHourAgo)
    }
}
