package com.assistant.server.document.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.assistant.db.JiraDatabase
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Property-based tests for [RateLimiterImpl].
 *
 * **Property 18: Rate Limiting — Per-user Hourly Cap**
 *
 * Verifies:
 * - Throws exception when N >= maxPerUserPerHour
 * - Passes when N < maxPerUserPerHour
 *
 * **Validates: Requirements 16.1**
 */
@OptIn(ExperimentalKotest::class)
class RateLimiterPropertyTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var rateLimitRepo: RateLimitRepository
    private val cfg = PropTestConfig(iterations = 100)

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        val database = JiraDatabase(driver)
        rateLimitRepo = RateLimitRepository(database)
    }

    // ── Generators ───────────────────────────────────────────────

    private fun arbUserId(): Arb<String> = Arb.string(3..10, Codepoint.alphanumeric())

    private fun arbMaxPerHour(): Arb<Int> = Arb.int(1..20)

    /**
     * **Property 18a: Throws when N >= maxPerUserPerHour**
     *
     * After recording N requests where N >= max, check() throws.
     *
     * **Validates: Requirements 16.1**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 18: Rate Limiting")
    fun `Property 18a - throws when requests at or above limit`() {
        runBlocking {
            checkAll(cfg, arbUserId(), arbMaxPerHour()) { userId, max ->
                resetDb()
                val limiter = RateLimiterImpl(rateLimitRepo, max)

                // Record exactly max requests
                repeat(max) { rateLimitRepo.record(userId) }

                val ex = assertFailsWith<RateLimitExceededException> {
                    limiter.check(userId)
                }
                assertNotNull(ex.message)
            }
        }
    }

    /**
     * **Property 18b: Passes when N < maxPerUserPerHour**
     *
     * After recording N requests where N < max, check() succeeds.
     *
     * **Validates: Requirements 16.1**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 18: Rate Limiting")
    fun `Property 18b - passes when requests below limit`() {
        runBlocking {
            checkAll(cfg, arbUserId(), arbMaxPerHour()) { userId, max ->
                resetDb()
                val limiter = RateLimiterImpl(rateLimitRepo, max)

                // Record fewer than max requests
                val count = if (max > 1) max - 1 else 0
                repeat(count) { rateLimitRepo.record(userId) }

                // Should not throw
                limiter.check(userId)
            }
        }
    }

    /**
     * **Property 18c: Different users have independent limits**
     *
     * User A at limit does not affect User B's check.
     *
     * **Validates: Requirements 16.1**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 18: Rate Limiting")
    fun `Property 18c - different users have independent limits`() {
        runBlocking {
            checkAll(cfg, arbMaxPerHour()) { max ->
                resetDb()
                val limiter = RateLimiterImpl(rateLimitRepo, max)
                val userA = "userA"
                val userB = "userB"

                // Fill user A to the limit
                repeat(max) { rateLimitRepo.record(userA) }

                // User A should be blocked
                assertFailsWith<RateLimitExceededException> {
                    limiter.check(userA)
                }

                // User B should pass (no requests)
                limiter.check(userB)
            }
        }
    }

    private fun resetDb() {
        driver.execute(null, "DELETE FROM deep_collection_rate_limits", 0)
    }
}
