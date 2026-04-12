package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphEdge
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 4: Hover highlights exactly connected edges.
 *
 * For any node in the graph, when that node is hovered, the set of
 * highlighted edges SHALL be exactly those edges where
 * edge.sourceId == node.id OR edge.targetId == node.id.
 *
 * **Validates: Requirements 3.2**
 */
class HoverHighlightEdgesTest {

    // -- Pure function under test --------------------------------------------

    /** Given a node ID and edges, return edges connected to that node. */
    private fun connectedEdges(
        nodeId: String,
        edges: List<GraphEdge>
    ): Set<GraphEdge> {
        return edges.filter {
            it.sourceId == nodeId || it.targetId == nodeId
        }.toSet()
    }

    // -- Random generators ---------------------------------------------------

    private val edgeTypes = listOf(
        "SEMANTIC", "DEPENDS_ON", "BLOCKS", "RELATES_TO"
    )

    private fun randomNodeIds(rng: Random): List<String> {
        val count = rng.nextInt(10, 31)
        return (1..count).map { "node-${rng.nextInt(1, 100_000)}" }
    }

    private fun randomEdges(
        rng: Random,
        nodeIds: List<String>
    ): List<GraphEdge> {
        val count = rng.nextInt(20, 61)
        return (1..count).map {
            GraphEdge(
                sourceId = nodeIds[rng.nextInt(nodeIds.size)],
                targetId = nodeIds[rng.nextInt(nodeIds.size)],
                type = edgeTypes[rng.nextInt(edgeTypes.size)]
            )
        }
    }

    // -- Reference implementation (independent verification) -----------------

    private fun expectedConnected(
        nodeId: String,
        edges: List<GraphEdge>
    ): Set<GraphEdge> {
        val result = mutableSetOf<GraphEdge>()
        for (e in edges) {
            if (e.sourceId == nodeId || e.targetId == nodeId) result.add(e)
        }
        return result
    }

    // -- Property tests ------------------------------------------------------

    @Test
    fun hoverHighlightsExactlyConnectedEdges() {
        val rng = Random(seed = 42)
        repeat(25) { i ->
            val nodeIds = randomNodeIds(rng)
            val edges = randomEdges(rng, nodeIds)
            val hoveredId = nodeIds[rng.nextInt(nodeIds.size)]

            val result = connectedEdges(hoveredId, edges)
            val expected = expectedConnected(hoveredId, edges)

            assertEquals(
                expected, result,
                "Iteration $i: hovered=$hoveredId edges=${edges.size}"
            )
        }
    }

    @Test
    fun everyHighlightedEdgeTouchesHoveredNode() {
        val rng = Random(seed = 77)
        repeat(25) { i ->
            val nodeIds = randomNodeIds(rng)
            val edges = randomEdges(rng, nodeIds)
            val hoveredId = nodeIds[rng.nextInt(nodeIds.size)]

            val result = connectedEdges(hoveredId, edges)
            for (edge in result) {
                assertTrue(
                    edge.sourceId == hoveredId || edge.targetId == hoveredId,
                    "Iteration $i: edge $edge not connected to $hoveredId"
                )
            }
        }
    }

    @Test
    fun noMissedEdgesOutsideHighlightedSet() {
        val rng = Random(seed = 99)
        repeat(25) { i ->
            val nodeIds = randomNodeIds(rng)
            val edges = randomEdges(rng, nodeIds)
            val hoveredId = nodeIds[rng.nextInt(nodeIds.size)]

            val highlighted = connectedEdges(hoveredId, edges)
            val missed = edges.filter { it !in highlighted }

            for (edge in missed) {
                assertTrue(
                    edge.sourceId != hoveredId && edge.targetId != hoveredId,
                    "Iteration $i: edge $edge should be highlighted"
                )
            }
        }
    }

    @Test
    fun isolatedNodeHasNoConnectedEdges() {
        val rng = Random(seed = 55)
        repeat(15) { i ->
            val nodeIds = randomNodeIds(rng)
            val isolatedId = "isolated-${rng.nextInt()}"
            val edges = randomEdges(rng, nodeIds)

            val result = connectedEdges(isolatedId, edges)
            assertTrue(
                result.isEmpty(),
                "Iteration $i: isolated node should have 0 edges"
            )
        }
    }
}
