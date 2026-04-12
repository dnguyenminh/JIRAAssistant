package com.assistant.graph

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property-based tests for Graph Engine (Properties 10, 11, 12).
 *
 * Feature: jira-assistant-app
 */
class GraphEnginePropertyTest {

    private val engine = ForceDirectedGraphEngine(iterations = 75, seed = 42)

    // --- Generators ---

    private fun arbAlphanumeric(range: IntRange = 1..20): Arb<String> =
        Arb.string(minSize = range.first, maxSize = range.last, codepoints = Codepoint.alphanumeric())

    private fun arbTicketNode(id: String): Arb<TicketNode> = arbitrary {
        TicketNode(
            id = id,
            key = "PROJ-${Arb.int(1..9999).bind()}",
            summary = arbAlphanumeric(5..50).bind(),
            status = Arb.element("To Do", "In Progress", "Done").bind()
        )
    }

    /**
     * Generates a valid NetworkGraph with 1-50 nodes and random edges between them.
     */
    private fun arbNetworkGraph(): Arb<NetworkGraph> = arbitrary {
        val nodeCount = Arb.int(1..50).bind()
        val ids = (1..nodeCount).map { "node-$it" }
        val nodes = ids.map { id -> arbTicketNode(id).bind() }

        val maxEdges = minOf(nodeCount * 2, nodeCount * (nodeCount - 1) / 2)
        val edgeCount = if (nodeCount > 1) Arb.int(0..maxEdges).bind() else 0
        val edgeSet = mutableSetOf<Pair<String, String>>()
        val edges = mutableListOf<TicketEdge>()

        repeat(edgeCount) {
            if (ids.size >= 2) {
                val fromIdx = Arb.int(0 until ids.size).bind()
                var toIdx = Arb.int(0 until ids.size).bind()
                if (toIdx == fromIdx) toIdx = (toIdx + 1) % ids.size
                val pair = if (fromIdx < toIdx) Pair(ids[fromIdx], ids[toIdx]) else Pair(ids[toIdx], ids[fromIdx])
                if (pair !in edgeSet) {
                    edgeSet.add(pair)
                    edges.add(TicketEdge(pair.first, pair.second, "relates_to", false))
                }
            }
        }

        NetworkGraph(nodes, edges)
    }

    private fun arbCanvasSize(): Arb<Pair<Double, Double>> = arbitrary {
        val w = Arb.double(100.0..2000.0).bind()
        val h = Arb.double(100.0..2000.0).bind()
        Pair(w, h)
    }

    // --- Property 10: Force-Directed Layout Bounds Invariant ---

    /**
     * Property 10: Force-Directed Layout Bounds Invariant
     *
     * For any NetworkGraph (1-50 nodes) and canvas (width, height):
     * 1. All node positions within [0, width] × [0, height]
     * 2. Avg distance between connected nodes < avg distance between all node pairs
     *    (when graph has edges and more than 2 nodes)
     *
     * **Validates: Requirements 3.7**
     */
    @Test
    fun `Property 10 - all positions within bounds and connected nodes closer than average`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbNetworkGraph(), arbCanvasSize()) { graph, (width, height) ->
            val layout = engine.computeLayout(graph, width, height)

            // Every node must have a position
            assertTrue(
                layout.positions.size == graph.nodes.size,
                "Expected ${graph.nodes.size} positions, got ${layout.positions.size}"
            )

            // All positions within [0, width] × [0, height]
            for ((nodeId, pos) in layout.positions) {
                assertTrue(
                    pos.x >= 0.0 && pos.x <= width,
                    "Node $nodeId x=${pos.x} out of bounds [0, $width]"
                )
                assertTrue(
                    pos.y >= 0.0 && pos.y <= height,
                    "Node $nodeId y=${pos.y} out of bounds [0, $height]"
                )
            }

            // Check connected nodes are closer than average (only meaningful with edges and >2 nodes)
            if (graph.edges.isNotEmpty() && graph.nodes.size > 2) {
                val positions = layout.positions

                // Average distance between connected node pairs
                var connectedDistSum = 0.0
                var connectedCount = 0
                for (edge in graph.edges) {
                    val p1 = positions[edge.fromId]
                    val p2 = positions[edge.toId]
                    if (p1 != null && p2 != null) {
                        val dx = p1.x - p2.x
                        val dy = p1.y - p2.y
                        connectedDistSum += sqrt(dx * dx + dy * dy)
                        connectedCount++
                    }
                }

                // Average distance between all node pairs
                val nodeList = graph.nodes
                var allDistSum = 0.0
                var allCount = 0
                for (i in nodeList.indices) {
                    for (j in i + 1 until nodeList.size) {
                        val p1 = positions[nodeList[i].id]
                        val p2 = positions[nodeList[j].id]
                        if (p1 != null && p2 != null) {
                            val dx = p1.x - p2.x
                            val dy = p1.y - p2.y
                            allDistSum += sqrt(dx * dx + dy * dy)
                            allCount++
                        }
                    }
                }

                if (connectedCount > 0 && allCount > 0) {
                    val avgConnected = connectedDistSum / connectedCount
                    val avgAll = allDistSum / allCount
                    assertTrue(
                        avgConnected <= avgAll,
                        "Avg connected distance ($avgConnected) should be <= avg all distance ($avgAll)"
                    )
                }
            }
        }
    }

    // --- Property 11: Cluster Detection Partitioning ---

    /**
     * Property 11: Cluster Detection Partitioning
     *
     * For any NetworkGraph with ≥2 nodes:
     * (a) Each node belongs to exactly one cluster
     * (b) Total nodes across all clusters = total graph nodes
     * (c) No empty clusters
     *
     * **Validates: Requirements 3.10**
     */
    @Test
    fun `Property 11 - each node in exactly one cluster and total equals graph nodes`() = runTest {
        val arbGraphWith2Plus = arbitrary {
            val nodeCount = Arb.int(2..50).bind()
            val ids = (1..nodeCount).map { "node-$it" }
            val nodes = ids.map { id -> arbTicketNode(id).bind() }

            val maxEdges = minOf(nodeCount * 2, nodeCount * (nodeCount - 1) / 2)
            val edgeCount = Arb.int(0..maxEdges).bind()
            val edgeSet = mutableSetOf<Pair<String, String>>()
            val edges = mutableListOf<TicketEdge>()

            repeat(edgeCount) {
                val fromIdx = Arb.int(0 until ids.size).bind()
                var toIdx = Arb.int(0 until ids.size).bind()
                if (toIdx == fromIdx) toIdx = (toIdx + 1) % ids.size
                val pair = if (fromIdx < toIdx) Pair(ids[fromIdx], ids[toIdx]) else Pair(ids[toIdx], ids[fromIdx])
                if (pair !in edgeSet) {
                    edgeSet.add(pair)
                    edges.add(TicketEdge(pair.first, pair.second, "relates_to", false))
                }
            }

            NetworkGraph(nodes, edges)
        }

        checkAll(PropTestConfig(iterations = 25), arbGraphWith2Plus) { graph ->
            val clusters = engine.detectClusters(graph)

            // (c) No empty clusters
            for (cluster in clusters) {
                assertTrue(
                    cluster.nodeIds.isNotEmpty(),
                    "Cluster ${cluster.id} is empty"
                )
            }

            // Collect all node IDs from clusters
            val allClusterNodeIds = clusters.flatMap { it.nodeIds }
            val graphNodeIds = graph.nodes.map { it.id }.toSet()

            // (b) Total nodes in clusters = total graph nodes
            assertTrue(
                allClusterNodeIds.size == graphNodeIds.size,
                "Total cluster nodes (${allClusterNodeIds.size}) != graph nodes (${graphNodeIds.size})"
            )

            // (a) Each node in exactly one cluster (no duplicates)
            assertTrue(
                allClusterNodeIds.toSet().size == allClusterNodeIds.size,
                "Some nodes appear in multiple clusters"
            )

            // All graph nodes are covered
            assertTrue(
                allClusterNodeIds.toSet() == graphNodeIds,
                "Cluster nodes don't match graph nodes"
            )
        }
    }

    // --- Property 12: Graph Search Filter Correctness ---

    /**
     * Property 12: Graph Search Filter Correctness
     *
     * For any search query and NetworkGraph:
     * - Results contain ONLY nodes where key or summary contains query (case-insensitive)
     * - Results contain ALL such matching nodes
     *
     * **Validates: Requirements 3.6**
     */
    @Test
    fun `Property 12 - filterNodes returns exactly the matching nodes`() = runTest {
        val arbQuery = Arb.string(minSize = 1, maxSize = 5, codepoints = Codepoint.az())

        checkAll(PropTestConfig(iterations = 25), arbNetworkGraph(), arbQuery) { graph, query ->
            val result = filterNodes(graph.nodes, query)
            val lowerQuery: String = query.lowercase()

            // All returned nodes must match
            for (node in result) {
                val keyLower: String = node.key.lowercase()
                val summaryLower: String = node.summary.lowercase()
                assertTrue(
                    keyLower.contains(lowerQuery) || summaryLower.contains(lowerQuery),
                    "Node ${node.key} (summary='${node.summary}') doesn't match query '$query'"
                )
            }

            // All matching nodes must be returned
            val expected = graph.nodes.filter { node ->
                val kl: String = node.key.lowercase()
                val sl: String = node.summary.lowercase()
                kl.contains(lowerQuery) || sl.contains(lowerQuery)
            }
            assertTrue(
                result.size == expected.size,
                "Expected ${expected.size} matching nodes but got ${result.size} for query '$query'"
            )
            assertTrue(
                result.map { it.id }.toSet() == expected.map { it.id }.toSet(),
                "Returned node IDs don't match expected for query '$query'"
            )
        }
    }
}
