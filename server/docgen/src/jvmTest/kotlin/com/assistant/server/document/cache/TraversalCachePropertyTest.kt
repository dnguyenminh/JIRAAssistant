package com.assistant.server.document.cache

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
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Property-based tests for [TraversalCacheImpl].
 *
 * **Property 17: Cache TTL and Invalidation**
 *
 * **Validates: Requirements 15.1, 15.2, 15.3, 15.4**
 */
@OptIn(ExperimentalKotest::class)
class TraversalCachePropertyTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var cacheRepo: TraversalCacheRepository
    private val cfg = PropTestConfig(iterations = 100)

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        cacheRepo = TraversalCacheRepository(JiraDatabase(driver))
    }

    private fun arbTicketId(): Arb<String> = arbitrary {
        val prefix = Arb.string(2..4, Codepoint.alphanumeric())
            .bind().uppercase()
        "$prefix-${Arb.int(1..9999).bind()}"
    }

    private fun arbTtlMinutes(): Arb<Int> = Arb.int(5..120)

    /**
     * **Property 17a: Cache hit when within TTL + root unchanged**
     *
     * **Validates: Requirements 15.1, 15.2**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 17: Cache TTL")
    fun `Property 17a - cache hit when within TTL and root unchanged`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), arbTtlMinutes()) { rootId, ttl ->
                resetDb()
                val now = Instant.now()
                val updatedAt = now.minus(10, ChronoUnit.MINUTES).toString()
                val graph = buildTestGraph(rootId, updatedAt)

                cacheRepo.put(rootId, graph, now.toString(), updatedAt)

                val cache = TraversalCacheImpl(cacheRepo, FakeCacheJiraClient(updatedAt))
                val result = cache.get(rootId, ttl)

                assertNotNull(result, "Should hit cache within TTL")
                assertEquals(rootId, result.rootTicketId)
                assertEquals(graph.nodes.size, result.nodes.size)
            }
        }
    }

    /**
     * **Property 17b: Cache miss when TTL expired**
     *
     * **Validates: Requirements 15.2**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 17: Cache TTL")
    fun `Property 17b - cache miss when TTL expired`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), arbTtlMinutes()) { rootId, ttl ->
                resetDb()
                val updatedAt = Instant.now().minus(2, ChronoUnit.HOURS).toString()
                val graph = buildTestGraph(rootId, updatedAt)
                val expiredAt = Instant.now()
                    .minus(ttl.toLong() + 1, ChronoUnit.MINUTES).toString()

                cacheRepo.put(rootId, graph, expiredAt, updatedAt)

                val cache = TraversalCacheImpl(cacheRepo, FakeCacheJiraClient(updatedAt))
                assertNull(cache.get(rootId, ttl), "Should miss when TTL expired")
            }
        }
    }

    /**
     * **Property 17c: Cache miss when root ticket updated**
     *
     * **Validates: Requirements 15.4**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 17: Cache TTL")
    fun `Property 17c - cache miss when root ticket updated after cache`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), arbTtlMinutes()) { rootId, ttl ->
                resetDb()
                val cachedAt = Instant.now().toString()
                val oldUpdatedAt = Instant.now()
                    .minus(10, ChronoUnit.MINUTES).toString()
                val graph = buildTestGraph(rootId, oldUpdatedAt)

                cacheRepo.put(rootId, graph, cachedAt, oldUpdatedAt)

                val newUpdatedAt = Instant.now()
                    .plus(5, ChronoUnit.MINUTES).toString()
                val cache = TraversalCacheImpl(
                    cacheRepo, FakeCacheJiraClient(newUpdatedAt)
                )
                assertNull(cache.get(rootId, ttl), "Should miss when root updated")
            }
        }
    }

    /**
     * **Property 17d: Null after invalidate**
     *
     * **Validates: Requirements 15.3**
     */
    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 17: Cache TTL")
    fun `Property 17d - null after invalidate`() {
        runBlocking {
            checkAll(cfg, arbTicketId(), arbTtlMinutes()) { rootId, ttl ->
                resetDb()
                val now = Instant.now()
                val updatedAt = now.minus(10, ChronoUnit.MINUTES).toString()
                val graph = buildTestGraph(rootId, updatedAt)

                cacheRepo.put(rootId, graph, now.toString(), updatedAt)

                val cache = TraversalCacheImpl(cacheRepo, FakeCacheJiraClient(updatedAt))
                cache.invalidate(rootId)

                assertNull(cache.get(rootId, ttl), "Should be null after invalidate")
            }
        }
    }

    private fun resetDb() {
        driver.execute(null, "DELETE FROM traversal_cache", 0)
    }
}
