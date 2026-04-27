package com.assistant.server.document.security

import org.slf4j.LoggerFactory

/**
 * Per-user hourly rate limiter for deep collection requests.
 *
 * Checks the count of requests in the last hour against [maxPerUserPerHour].
 * Throws [RateLimitExceededException] when the limit is exceeded.
 *
 * Requirements: 16.1, 16.2
 */
class RateLimiterImpl(
    private val rateLimitRepository: RateLimitRepository,
    internal val maxPerUserPerHour: Int = 10
) : RateLimiter {

    private val logger = LoggerFactory.getLogger(RateLimiterImpl::class.java)

    override suspend fun check(userId: String) {
        val count = rateLimitRepository.countInLastHour(userId)
        if (count >= maxPerUserPerHour) {
            logger.warn(
                "Rate limit exceeded for user {}: {} >= {}",
                userId, count, maxPerUserPerHour
            )
            throw RateLimitExceededException(
                "Đã vượt giới hạn deep collection ($maxPerUserPerHour/hour). " +
                    "Vui lòng thử lại sau."
            )
        }
    }

    override suspend fun record(userId: String) {
        rateLimitRepository.record(userId)
        rateLimitRepository.cleanup()
    }
}
