package com.assistant.server.analysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketEdge
import com.assistant.server.document.models.TicketGraph
import com.assistant.server.document.models.TicketNode
import com.assistant.server.document.models.TraversalMetadata
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int

/**
 * Kotest Arb generators for [TicketGraph] and related models.
 *
 * Generates valid TicketGraphs with:
 * - 1..100 nodes at depths 0..5
 * - Root ticket always at depth 0
 * - Edges between parent-child nodes
 * - Valid TraversalMetadata
 */

/** Generate a valid [TicketGraph] with 1..100 nodes. */
fun Arb.Companion.arbTicketGraph(): Arb<TicketGraph> = arbitrary {
    val nodeCount = Arb.int(1..100).bind()
    val maxDepth = Arb.int(0..5).bind()
    val rootId = "ROOT-1"

    val nodes = buildNodes(rootId, nodeCount, maxDepth)
    val edges = buildEdges(nodes)
    val metadata = TraversalMetadata(
        totalDiscovered = nodes.size,
        totalFetched = nodes.size,
        totalSkipped = 0,
        maxDepthReached = nodes.values.maxOf { it.depth },
        traversalTimeMs = 100L
    )
    TicketGraph(
        rootTicketId = rootId,
        nodes = nodes,
        edges = edges,
        metadata = metadata
    )
}

/** Build node map: root at depth 0, rest distributed across depths. */
private fun buildNodes(
    rootId: String,
    count: Int,
    maxDepth: Int
): Map<String, TicketNode> {
    val nodes = mutableMapOf<String, TicketNode>()
    nodes[rootId] = TicketNode(
        ticketId = rootId,
        depth = 0,
        discoveredVia = RelationshipType.ROOT,
        parentDiscoveryId = rootId,
        issue = StructuredTicketContent(summary = "Root"),
        relevanceScore = 1.0
    )
    for (i in 1 until count) {
        val id = "TICKET-$i"
        val depth = if (maxDepth == 0) 0 else (i % (maxDepth + 1))
        val parentId = if (depth == 0) rootId else "TICKET-${i - 1}"
        nodes[id] = TicketNode(
            ticketId = id,
            depth = depth,
            discoveredVia = RelationshipType.ISSUE_LINK,
            parentDiscoveryId = parentId,
            issue = StructuredTicketContent(summary = "Ticket $i"),
            relevanceScore = 1.0 / (depth + 1)
        )
    }
    return nodes
}

/** Build edges from parent discovery relationships. */
private fun buildEdges(
    nodes: Map<String, TicketNode>
): List<TicketEdge> {
    return nodes.values
        .filter { it.discoveredVia != RelationshipType.ROOT }
        .map { node ->
            TicketEdge(
                sourceId = node.parentDiscoveryId,
                targetId = node.ticketId,
                relationshipType = RelationshipType.ISSUE_LINK
            )
        }
}
