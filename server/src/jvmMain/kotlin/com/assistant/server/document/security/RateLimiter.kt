package com.assistant.server.document.security

/**
 * Per-user hourly rate limiter for deep collection requests.
 *
 * Prevents abuse of Jira API by capping the number of deep
 * collection traversals a single user can trigger per hour.
 *
 * Requirements: 16.1, 16.2
 */
interface RateLimiter {
    /**
     * Check whether the user is within the rate limit.
     * @throws RateLimitExceededException if the limit is exceeded.
     */
    suspend fun check(userId: String)

    /** Record a successful deep collection request for rate tracking. */
    suspend fun record(userId: String)
}

/**
 * Thrown when a user exceeds the per-hour deep collection limit.
 */
class RateLimitExceededException(
    message: String = "Rate limit exceeded for deep collection"
) : RuntimeException(message)
