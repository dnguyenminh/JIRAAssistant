package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphEdge
import com.assistant.frontend.models.GraphFilters
import com.assistant.frontend.models.GraphNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** Property tests for GraphFilterEngine. */
class GraphFilterEngineTest {

    private val allTypes = listOf("FEATURE", "DEPENDENCY", "UI_MODULE", "SUB_TASK")
    private val chars = ('a'..'z') + ('0'..'9') + listOf('-', '_')

    private fun genString(rng: Random, max: Int = 15): String {
        val len = rng.nextInt(1, max + 1)
        return (1..len).map { chars[rng.nextInt(chars.size)] }.joinToString("")
    }

    private fun genGraphNode(rng: Random): GraphNode = GraphNode(
        id = "n-${rng.nextInt(0, 100000)}", key = "TK-${rng.nextInt(1, 9999)}",
        summary = genString(rng, 25), type = allTypes[rng.nextInt(allTypes.size)],
        x = 0.0, y = 0.0, clusterId = if (rng.nextBoolean()) rng.nextInt(1, 6) else null
    )

    private fun genNodeList(rng: Random): List<GraphNode> =
        (1..rng.nextInt(5, 30)).map { genGraphNode(rng) }

    private fun genGraphFilters(rng: Random, nodes: List<GraphNode>): GraphFilters {
        val types = allTypes.filter { rng.nextBoolean() }.toSet()
            .ifEmpty { setOf(allTypes[rng.nextInt(allTypes.size)]) }
        val clusterId = if (rng.nextInt(3) == 0) rng.nextInt(1, 6) else null
        val focusId = if (rng.nextInt(3) == 0) nodes.random(rng).id else null
        val query = genSearchQuery(rng, nodes)
        return GraphFilters(types, clusterId, focusId, 1, query)
    }

    private fun genSearchQuery(rng: Random, nodes: List<GraphNode>): String {
        return when (rng.nextInt(4)) {
            0 -> ""
            1 -> substringOf(rng, nodes.random(rng).key)
            2 -> substringOf(rng, nodes.random(rng).summary)
            else -> genString(rng, 5)
        }
    }

    private fun substringOf(rng: Random, s: String): String {
        if (s.length <= 1) return s
        val start = rng.nextInt(0, s.length - 1)
        val end = rng.nextInt(start + 1, s.length + 1)
        return s.substring(start, end)
    }

    /** Build focusedNodeIds: random subset when focus active. */
    private fun genFocusedIds(rng: Random, nodes: List<GraphNode>, filters: GraphFilters): Set<String> {
        if (filters.focusNodeId == null) return emptySet()
        // Simulate BFS result: always includes focusNodeId + random neighbors
        val ids = mutableSetOf(filters.focusNodeId)
        val count = rng.nextInt(0, nodes.size / 2 + 1)
        repeat(count) { ids.add(nodes.random(rng).id) }
        return ids
    }

    // -- Reference oracle ----------------------------------------------------

    private fun expectedVisible(
        nodes: List<GraphNode>, filters: GraphFilters, focusedIds: Set<String>
    ): Set<String> = nodes.filter { n ->
        matchType(n, filters) && matchCluster(n, filters) &&
            matchFocus(n, filters, focusedIds) && matchSearch(n, filters)
    }.mapTo(mutableSetOf()) { it.id }

    private fun matchType(n: GraphNode, f: GraphFilters) = n.type in f.enabledTypes

    private fun matchCluster(n: GraphNode, f: GraphFilters) =
        f.selectedClusterId == null || n.clusterId == f.selectedClusterId

    private fun matchFocus(n: GraphNode, f: GraphFilters, ids: Set<String>) =
        f.focusNodeId == null || n.id in ids

    private fun matchSearch(n: GraphNode, f: GraphFilters): Boolean {
        if (f.searchQuery.isBlank()) return true
        val q = f.searchQuery.lowercase()
        return n.key.lowercase().contains(q) ||
            n.summary.lowercase().contains(q)
    }

    // -- Property 1: Combined AND Filter Node Visibility --------------------

    /** **Validates: Requirements 1.2, 1.3, 1.6, 2.3, 2.4, 6.1, 6.2, 6.3, 6.5** */
    @Test
    fun combinedAndFilterNodeVisibility() {
        val rng = Random(seed = 42)
        repeat(25) { i ->
            val nodes = genNodeList(rng)
            val filters = genGraphFilters(rng, nodes)
            val focusedIds = genFocusedIds(rng, nodes, filters)
            val actual = GraphFilterEngine.computeVisibleNodes(filters, nodes, focusedIds)
            val expected = expectedVisible(nodes, filters, focusedIds)
            assertEquals(expected, actual, "Iter $i: filters=$filters")
        }
    }

    @Test
    fun allFiltersDefaultReturnsAllNodes() {
        val rng = Random(seed = 7)
        repeat(15) { i ->
            val nodes = genNodeList(rng)
            val result = GraphFilterEngine.computeVisibleNodes(GraphFilters(), nodes, emptySet())
            assertEquals(nodes.mapTo(mutableSetOf()) { it.id }, result, "Iter $i")
        }
    }

    @Test
    fun visibleSetIsSubsetOfAllNodeIds() {
        val rng = Random(seed = 13)
        repeat(15) { i ->
            val nodes = genNodeList(rng)
            val filters = genGraphFilters(rng, nodes)
            val focusedIds = genFocusedIds(rng, nodes, filters)
            val result = GraphFilterEngine.computeVisibleNodes(filters, nodes, focusedIds)
            assertEquals(true, nodes.mapTo(mutableSetOf()) { it.id }.containsAll(result), "Iter $i")
        }
    }

    // -- Edge generator ------------------------------------------------------

    private val edgeTypes = listOf("BLOCKS", "RELATES_TO", "DEPENDS_ON")

    private fun genEdges(rng: Random, nodeIds: List<String>): List<GraphEdge> {
        if (nodeIds.size < 2) return emptyList()
        val count = rng.nextInt(1, nodeIds.size * 2)
        return (1..count).map {
            GraphEdge(
                sourceId = nodeIds[rng.nextInt(nodeIds.size)],
                targetId = nodeIds[rng.nextInt(nodeIds.size)],
                type = edgeTypes[rng.nextInt(edgeTypes.size)]
            )
        }
    }

    /** **Validates: Requirements 1.4, 2.5, 3.4** */
    @Test
    fun edgeVisibilityDerivedFromNodeVisibility() {
        val rng = Random(seed = 99)
        repeat(25) { i ->
            val nodes = genNodeList(rng)
            val nodeIds = nodes.map { it.id }
            val edges = genEdges(rng, nodeIds)
            val visibleNodeIds = nodeIds.filter { rng.nextBoolean() }.toSet()
            val visibleEdges = GraphFilterEngine.computeVisibleEdges(visibleNodeIds, edges)
            for (edge in edges) {
                val key = "${edge.sourceId}->${edge.targetId}"
                val bothVisible = edge.sourceId in visibleNodeIds && edge.targetId in visibleNodeIds
                assertEquals(bothVisible, key in visibleEdges, "Iter $i edge $key")
            }
        }
    }

    // -- Property 5: isAnyFilterActive Correctness ---------------------------

    private val defaultTypes = setOf("FEATURE", "DEPENDENCY", "UI_MODULE", "SUB_TASK")

    private fun genRandomFilters(rng: Random): GraphFilters {
        val types = allTypes.filter { rng.nextBoolean() }.toSet()
            .ifEmpty { setOf(allTypes[rng.nextInt(allTypes.size)]) }
        val cluster = if (rng.nextBoolean()) rng.nextInt(1, 10) else null
        val focus = if (rng.nextBoolean()) "n-${rng.nextInt(100)}" else null
        val query = if (rng.nextBoolean()) genString(rng, 8) else ""
        return GraphFilters(types, cluster, focus, 1, query)
    }

    /** **Validates: Requirements 5.7** */
    @Test
    fun isAnyFilterActiveCorrectness() {
        val rng = Random(seed = 55)
        repeat(25) { i ->
            val filters = genRandomFilters(rng)
            val typeFilterActive = filters.enabledTypes.isNotEmpty() &&
                filters.enabledTypes != defaultTypes
            val expected = typeFilterActive ||
                filters.selectedClusterId != null ||
                filters.focusNodeId != null ||
                filters.searchQuery.isNotBlank()
            assertEquals(
                expected, GraphFilterEngine.isAnyFilterActive(filters),
                "Iter $i: filters=$filters"
            )
        }
        // Default filters (empty enabledTypes) = no filter active
        assertEquals(false, GraphFilterEngine.isAnyFilterActive(GraphFilters()))
    }
}
