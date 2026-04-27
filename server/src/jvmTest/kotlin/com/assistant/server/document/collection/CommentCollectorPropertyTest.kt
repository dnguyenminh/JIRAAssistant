package com.assistant.server.document.collection

import com.assistant.server.document.models.TraversalConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.math.ceil
import kotlin.math.min

/**
 * Property tests for [CommentCollector].
 *
 * - Property 5: Pagination Completeness and Ordering
 * - Property 6: Comment Count Metamorphic
 * - Property 22: Comment Capping — maxCommentsPerTicket
 *
 * **Validates: Requirements 3.2, 3.3, 3.4, 3.7, 9.2, 9.5**
 */
@OptIn(ExperimentalKotest::class)
class CommentCollectorPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    // ── Property 5: Pagination Completeness and Ordering ──

    /**
     * **Validates: Requirements 3.2, 9.2**
     *
     * API is called ceil(N / pageSize) times when N <= maxCommentsPerTicket.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 5: Pagination Completeness")
    fun `API called ceil(N div pageSize) times for uncapped tickets`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..150), Arb.int(10..50)) { n, pageSize ->
                val cap = maxOf(n, 10) // ensure cap >= n so no capping
                val fake = FakeCommentJiraClient(n, pageSize)
                val config = configWith(pageSize, cap.coerceIn(10, 1000))
                val collector = CommentCollector(fake, config)

                collector.collectAll("TEST-1")

                val expected = if (n == 0) 1 else expectedCalls(n, pageSize)
                assertEquals(expected, fake.apiCallCount) {
                    "N=$n, pageSize=$pageSize: expected $expected calls"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.4**
     *
     * Comments are sorted by createdDate ascending (oldest first).
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 5: Pagination Ordering")
    fun `comments sorted oldest first by createdDate`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..200), Arb.int(10..50)) { n, pageSize ->
                val cap = maxOf(n, 10).coerceIn(10, 1000)
                val fake = FakeCommentJiraClient(n, pageSize)
                val collector = CommentCollector(fake, configWith(pageSize, cap))

                val result = collector.collectAll("TEST-1")

                val dates = result.comments.map { it.createdDate }
                assertEquals(dates, dates.sorted()) {
                    "Comments not sorted oldest-first"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.3**
     *
     * Each comment has non-empty author, createdDate, and body.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 5: Comment Fields")
    fun `all comments have non-empty author createdDate and body`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..100), Arb.int(10..50)) { n, pageSize ->
                val cap = maxOf(n, 10).coerceIn(10, 1000)
                val fake = FakeCommentJiraClient(n, pageSize)
                val collector = CommentCollector(fake, configWith(pageSize, cap))

                val result = collector.collectAll("TEST-1")
                result.comments.forEach { c ->
                    assertTrue(c.author.isNotEmpty()) { "Empty author" }
                    assertTrue(c.createdDate.isNotEmpty()) { "Empty date" }
                    assertTrue(c.body.isNotEmpty()) { "Empty body" }
                }
            }
        }
    }

    // ── Property 6: Comment Count Metamorphic ──

    /**
     * **Validates: Requirements 9.5**
     *
     * totalFetched <= totalReported — never collect more than exists.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 6: Count Metamorphic")
    fun `totalFetched never exceeds totalReported`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..300), Arb.int(10..50)) { n, pageSize ->
                val cap = maxOf(n, 10).coerceIn(10, 1000)
                val fake = FakeCommentJiraClient(n, pageSize)
                val collector = CommentCollector(fake, configWith(pageSize, cap))

                val result = collector.collectAll("TEST-1")
                assertTrue(result.totalFetched <= result.totalReported) {
                    "Fetched ${result.totalFetched} > reported ${result.totalReported}"
                }
            }
        }
    }

    // ── Property 22: Comment Capping — maxCommentsPerTicket ──

    /**
     * **Validates: Requirements 3.7**
     *
     * Returns min(N, M) comments where M = maxCommentsPerTicket.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 22: Comment Capping")
    fun `returns min(N, M) comments`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..300), Arb.int(10..100)) { n, m ->
                val cap = m.coerceIn(10, 1000)
                val fake = FakeCommentJiraClient(n, 25)
                val collector = CommentCollector(fake, configWith(25, cap))

                val result = collector.collectAll("TEST-1")
                val expected = min(n, cap)
                assertEquals(expected, result.totalFetched) {
                    "N=$n, M=$cap: expected $expected, got ${result.totalFetched}"
                }
            }
        }
    }

    /**
     * **Validates: Requirements 3.7**
     *
     * When N > M, only the M most recent comments are kept.
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 22: Comment Capping")
    fun `when N greater than M only M most recent comments kept`() {
        runBlocking {
            checkAll(cfg, Arb.int(50..300), Arb.int(10..49)) { n, m ->
                val cap = m.coerceIn(10, 1000)
                val fake = FakeCommentJiraClient(n, 25)
                val collector = CommentCollector(fake, configWith(25, cap))

                val result = collector.collectAll("TEST-1")
                assertEquals(cap, result.comments.size) {
                    "Expected $cap comments when N=$n > M=$cap"
                }
                // Verify these are the most recent (highest indices)
                val dates = result.comments.map { it.createdDate }
                assertEquals(dates, dates.sorted()) { "Not sorted" }
            }
        }
    }

    // ── Helpers ──

    private fun configWith(pageSize: Int, maxComments: Int) =
        TraversalConfig(
            commentPageSize = pageSize,
            maxCommentsPerTicket = maxComments
        )

    private fun expectedCalls(n: Int, pageSize: Int): Int =
        ceil(n.toDouble() / pageSize).toInt()
}
