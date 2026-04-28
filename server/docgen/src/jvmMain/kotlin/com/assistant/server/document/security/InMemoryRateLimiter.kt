package com.assistant.server.document.security

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory per-user hourly rate limiter for deep collection requests.
 *
 * Tracks request timestamps in memory. Suitable for single-instance
 * deployments; for multi-instance, replace with a DB-backed version.
 *
 * Requirements: 16.1
 */
class InMemoryRateLimiter(
    private val maxPerUserPerHour: Int = 10
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(InMemoryRateLimiter::class.java)
    private val requests = ConcurrentHashMap<String, MutableList<Instant>>()

    override suspend fun check(userId: String) {
        cleanup(userId)
        val count = requests[userId]?.size ?: 0
        if (count >= maxPerUserPerHour) {
            logger.warn("Rate limit exceeded for user {}: {} >= {}", userId, count, maxPerUserPerHour)
            throw RateLimitExceededException(
                "Đã vượt giới hạn deep collection ($maxPerUserPerHour/hour). Vui lòng thử lại sau."
            )
        }
    }

    override suspend fun record(userId: String) {
        requests.getOrPut(userId) { mutableListOf() }.add(Instant.now())
    }

    private fun cleanup(userId: String) {
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
        requests[userId]?.removeAll { it.isBefore(oneHourAgo) }
    }
}
