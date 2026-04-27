package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.models.IssueLinkInfo
import com.assistant.server.document.models.TraversalConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 21: Permission-Denied Isolation — 403 Ticket IDs Not Exposed.
 *
 * Verifies permissionDeniedCount is correct and no 403 ticket IDs
 * appear in the API response (nodes, edges, skippedTicketIds).
 *
 * **Validates: Requirements 1.10**
 */
@OptIn(ExperimentalKotest::class)
class TraversalEngineSecurityPropertyTest {

    private val cfg = PropTestConfig(iterations = 50)

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 21: Permission-Denied Isolation")
    fun `permissionDeniedCount matches number of 403 tickets`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..5)) { deniedCount ->
                val (tickets, denied) = buildGraphWith403(deniedCount)
                val config = defaultConfig()
                val graph = TraversalEngine(
                    FakeTicketFetcher(tickets, denied), config, Semaphore(5)
                ).traverse("ROOT-1")

                assertEquals(denied.size, graph.metadata.permissionDeniedCount) {
                    "Expected ${denied.size} denied, got " +
                        "${graph.metadata.permissionDeniedCount}"
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 21: Permission-Denied Isolation")
    fun `403 ticket IDs do not appear in nodes`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..5)) { deniedCount ->
                val (tickets, denied) = buildGraphWith403(deniedCount)
                val config = defaultConfig()
                val graph = TraversalEngine(
                    FakeTicketFetcher(tickets, denied), config, Semaphore(5)
                ).traverse("ROOT-1")

                denied.forEach { id ->
                    assertFalse(id in graph.nodes) {
                        "403 ticket $id found in nodes"
                    }
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 21: Permission-Denied Isolation")
    fun `403 ticket IDs do not appear in skippedTicketIds`() {
        runBlocking {
            checkAll(cfg, Arb.int(1..5)) { deniedCount ->
                val (tickets, denied) = buildGraphWith403(deniedCount)
                val config = defaultConfig()
                val graph = TraversalEngine(
                    FakeTicketFetcher(tickets, denied), config, Semaphore(5)
                ).traverse("ROOT-1")

                denied.forEach { id ->
                    assertFalse(id in graph.metadata.skippedTicketIds) {
                        "403 ticket $id found in skippedTicketIds"
                    }
                }
            }
        }
    }

    // ── Helpers ──

    private fun defaultConfig() = TraversalConfig(
        maxDepth = 5, maxTickets = 50
    ).validated()

    /**
     * Build a graph where root links to N denied tickets + N accessible.
     * Returns (allTickets, deniedIds).
     */
    private fun buildGraphWith403(
        deniedCount: Int
    ): Pair<Map<String, com.assistant.ai.deepanalysis.models.StructuredTicketContent>, Set<String>> {
        val deniedIds = (1..deniedCount).map { "DENIED-$it" }.toSet()
        val okIds = (1..deniedCount).map { "OK-$it" }
        val allLinked = (deniedIds + okIds).map {
            IssueLinkInfo(it, "Link", "relates to")
        }
        val tickets = mutableMapOf(
            "ROOT-1" to buildTicketContent(issueLinks = allLinked)
        )
        okIds.forEach { tickets[it] = buildTicketContent() }
        // Denied tickets also need content entries for the fetcher to know
        // they exist — but FakeTicketFetcher returns PermissionDenied for them
        deniedIds.forEach { tickets[it] = buildTicketContent() }
        return Pair(tickets, deniedIds)
    }
}
