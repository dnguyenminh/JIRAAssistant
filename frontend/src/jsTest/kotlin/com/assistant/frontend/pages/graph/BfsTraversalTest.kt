package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphEdge
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 3: BFS Traversal Correctness + Property 4: BFS Result Cap.
 *
 * P3: For any graph and any starting node with depth D (1 ≤ D ≤ 5),
 * bfsTraversal returns exactly the set of all nodes with shortest
 * path ≤ D hops from the starting node (including the start).
 *
 * P4: For any graph with > 500 nodes and depth > 3,
 * |BFS result| ≤ 500 (Req 7.4 cap).
 *
 * **Validates: Requirements 3.1, 3.3, 4.2, 7.4**
 */
class BfsTraversalTest {

    // -- In-memory graph test double -----------------------------------------

    private class TestGraph {
        val adj = mutableMapOf<String, MutableSet<String>>()
        val edges = mutableListOf<GraphEdge>()

        fun addNode(id: String) { adj.getOrPut(id) { mutableSetOf() } }

        fun addEdge(a: String, b: String) {
            adj.getOrPut(a) { mutableSetOf() }.add(b)
            adj.getOrPut(b) { mutableSetOf() }.add(a)
            edges.add(GraphEdge(sourceId = a, targetId = b, type = "STRUCTURAL"))
        }
    }

    // -- Graph generator -----------------------------------------------------

    private fun genGraph(rng: Random, nodeCount: Int, edgeCount: Int): TestGraph {
        val g = TestGraph()
        repeat(nodeCount) { g.addNode("n$it") }
        repeat(edgeCount) {
            val a = "n${rng.nextInt(nodeCount)}"
            val b = "n${rng.nextInt(nodeCount)}"
            if (a != b) g.addEdge(a, b)
        }
        return g
    }

    // -- Reference BFS oracle ------------------------------------------------

    private fun referenceBfs(
        start: String, depth: Int, adj: Map<String, Set<String>>
    ): Set<String> {
        val visited = mutableSetOf(start)
        var frontier = listOf(start)
        var d = 0
        while (d < depth && frontier.isNotEmpty()) {
            val next = mutableListOf<String>()
            for (node in frontier) {
                for (nb in adj[node].orEmpty()) {
                    if (nb !in visited) { visited.add(nb); next.add(nb) }
                }
            }
            frontier = next; d++
        }
        return visited
    }

    // -- Property test -------------------------------------------------------

    @Test
    fun bfsTraversalCorrectness() {
        val rng = Random(seed = 314)
        repeat(25) { i ->
            val nodeCount = rng.nextInt(3, 30)
            val edgeCount = rng.nextInt(1, nodeCount * 3)
            val graph = genGraph(rng, nodeCount, edgeCount)
            val startId = "n${rng.nextInt(nodeCount)}"
            val depth = rng.nextInt(1, 6)

            val actual = GraphFilterEngine.bfsFromEdges(startId, depth, graph.edges)
            val expected = referenceBfs(startId, depth, graph.adj)

            assertTrue(startId in actual, "Iter $i: start=$startId missing")
            assertTrue(actual.all { it in expected }, "Iter $i: extra nodes")
            assertEquals(expected, actual, "Iter $i: n=$nodeCount e=$edgeCount start=$startId d=$depth")
        }
    }

    @Test
    fun bfsStartNodeAlwaysIncluded() {
        val rng = Random(seed = 77)
        repeat(15) { i ->
            val graph = genGraph(rng, rng.nextInt(2, 15), rng.nextInt(0, 10))
            val startId = "n0"
            val depth = rng.nextInt(1, 6)
            val result = GraphFilterEngine.bfsFromEdges(startId, depth, graph.edges)
            assertTrue(startId in result, "Iter $i: start must be in result")
        }
    }

    @Test
    fun bfsDepthZeroReturnsOnlyStart() {
        val graph = TestGraph().apply {
            addNode("a"); addNode("b"); addEdge("a", "b")
        }
        val result = GraphFilterEngine.bfsFromEdges("a", 0, graph.edges)
        assertEquals(setOf("a"), result, "depth 0 → only start node")
    }

    @Test
    fun bfsIsolatedNodeReturnsOnlyStart() {
        val graph = TestGraph().apply { addNode("lonely"); addNode("other") }
        val result = GraphFilterEngine.bfsFromEdges("lonely", 3, graph.edges)
        assertEquals(setOf("lonely"), result, "isolated node → only itself")
    }

    @Test
    fun bfsLinearChainRespectsDepth() {
        val graph = TestGraph().apply {
            (0..4).forEach { addNode("n$it") }
            (0..3).forEach { addEdge("n$it", "n${it + 1}") }
        }
        assertEquals(setOf("n0", "n1"), GraphFilterEngine.bfsFromEdges("n0", 1, graph.edges))
        assertEquals(setOf("n0", "n1", "n2"), GraphFilterEngine.bfsFromEdges("n0", 2, graph.edges))
        assertEquals(setOf("n0", "n1", "n2", "n3", "n4"), GraphFilterEngine.bfsFromEdges("n0", 4, graph.edges))
    }

    // -- Property 4: BFS Result Cap ------------------------------------------

    /** **Validates: Requirements 7.4** */
    @Test
    fun bfsResultCapAt500WhenDepthGreaterThan3() {
        val rng = Random(seed = 42)
        repeat(25) { i ->
            val nodeCount = rng.nextInt(600, 1001)
            val edgeCount = rng.nextInt(nodeCount, nodeCount * 4)
            val graph = genGraph(rng, nodeCount, edgeCount)
            val startId = "n${rng.nextInt(nodeCount)}"
            val depth = rng.nextInt(4, 6)

            val result = GraphFilterEngine.bfsFromEdges(startId, depth, graph.edges)
            assertTrue(result.size <= 500, "Iter $i: n=$nodeCount d=$depth |r|=${result.size} > 500")
            assertTrue(startId in result, "Iter $i: start missing")
        }
    }
}
