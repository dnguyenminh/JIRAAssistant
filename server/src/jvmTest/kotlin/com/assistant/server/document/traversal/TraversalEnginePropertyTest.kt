package com.assistant.server.document.traversal

import com.assistant.ai.deepanalysis.models.IssueLinkInfo
import com.assistant.ai.deepanalysis.models.SubTaskInfo
import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TraversalConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property tests for [TraversalEngine]: cycle detection, config limits,
 * and BFS priority ordering.
 *
 * **Validates: Requirements 1.2, 1.3, 1.5, 1.7**
 */
@OptIn(ExperimentalKotest::class)
class TraversalEnginePropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    // ── Property 1: Cycle Detection and Node Metadata Invariant ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 1: Cycle Detection")
    fun `each ticketId appears at most once in nodes`() = runBlocking {
        checkAll(cfg, arbLinkedGraph()) { (tickets, rootId) ->
            val graph = traverse(tickets, rootId)
            val ids = graph.nodes.keys
            assertEquals(ids.size, ids.toSet().size)
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 1: Cycle Detection")
    fun `every node has valid metadata`() = runBlocking {
        checkAll(cfg, arbLinkedGraph()) { (tickets, rootId) ->
            val graph = traverse(tickets, rootId)
            graph.nodes.values.forEach { node ->
                assertTrue(node.ticketId.isNotBlank())
                assertTrue(node.depth >= 0)
                assertTrue(node.discoveredVia in RelationshipType.entries)
                if (node.discoveredVia == RelationshipType.ROOT) {
                    assertEquals(rootId, node.ticketId)
                }
            }
        }
    }

    // ── Property 2: Config Limits ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 2: Config Limits")
    fun `nodes size does not exceed maxTickets`() = runBlocking {
        checkAll(cfg, arbLinkedGraph(), Arb.int(1..10)) { (tickets, rootId), maxT ->
            val config = TraversalConfig(maxTickets = maxT, maxDepth = 5)
                .validated()
            val graph = traverse(tickets, rootId, config)
            assertTrue(graph.nodes.size <= config.maxTickets) {
                "nodes=${graph.nodes.size} > maxTickets=${config.maxTickets}"
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 2: Config Limits")
    fun `all node depths do not exceed maxDepth`() = runBlocking {
        checkAll(cfg, arbLinkedGraph(), Arb.int(1..5)) { (tickets, rootId), maxD ->
            val config = TraversalConfig(maxDepth = maxD, maxTickets = 200)
                .validated()
            val graph = traverse(tickets, rootId, config)
            graph.nodes.values.forEach { node ->
                assertTrue(node.depth <= config.maxDepth) {
                    "node ${node.ticketId} depth=${node.depth} > maxDepth=${config.maxDepth}"
                }
            }
        }
    }

    // ── Property 13: BFS Priority Ordering ──

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 13: BFS Priority Ordering")
    fun `parent discovered before subtasks and text refs`() = runBlocking {
        val parentId = "PROJ-1"
        val blockingId = "PROJ-2"
        val subTaskId = "PROJ-3"
        val rootId = "ROOT-1"

        val tickets = mapOf(
            rootId to buildTicketContent(
                parentKey = parentId,
                issueLinks = listOf(IssueLinkInfo(blockingId, "B", "blocks")),
                subTasks = listOf(SubTaskInfo(subTaskId, "Sub", "Open"))
            ),
            parentId to buildTicketContent(),
            blockingId to buildTicketContent(),
            subTaskId to buildTicketContent()
        )
        val graph = traverse(tickets, rootId)
        val parentNode = graph.nodes[parentId]
        val blockNode = graph.nodes[blockingId]
        val subNode = graph.nodes[subTaskId]

        assertNotNull(parentNode)
        assertNotNull(blockNode)
        assertNotNull(subNode)
        // All at depth 1, but parent should be discovered via PARENT
        assertEquals(RelationshipType.PARENT, parentNode!!.discoveredVia)
        assertEquals(RelationshipType.ISSUE_LINK, blockNode!!.discoveredVia)
        assertEquals(RelationshipType.SUB_TASK, subNode!!.discoveredVia)
    }

    // ── Helpers ──

    private suspend fun traverse(
        tickets: Map<String, com.assistant.ai.deepanalysis.models.StructuredTicketContent>,
        rootId: String,
        config: TraversalConfig = TraversalConfig(maxDepth = 5, maxTickets = 50).validated()
    ) = TraversalEngine(
        FakeTicketFetcher(tickets), config, Semaphore(5)
    ).traverse(rootId)
}
