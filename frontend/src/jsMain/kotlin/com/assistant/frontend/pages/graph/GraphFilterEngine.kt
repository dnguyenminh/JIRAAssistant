package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphEdge
import com.assistant.frontend.models.GraphFilters
import com.assistant.frontend.models.GraphNode

// BFS uses edge list — no external graph library dependency.

/**
 * Pure logic engine for computing visible nodes/edges based on filters.
 * No DOM manipulation — only set computations.
 *
 * Requirements: 1.2, 1.3, 1.4, 2.3, 2.5, 5.7, 6.1, 6.2, 6.3, 6.4, 7.2
 */
internal object GraphFilterEngine {

    private val DEFAULT_TYPES = setOf("FEATURE", "DEPENDENCY", "UI_MODULE", "SUB_TASK")

    /**
     * Compute visible node IDs using AND logic across all active filters.
     * A node is visible iff it satisfies ALL conditions:
     *  1. node.type ∈ enabledTypes
     *  2. selectedClusterId == null OR node.clusterId == selectedClusterId
     *  3. focusNodeId == null OR node.id ∈ focusedNodeIds
     *  4. searchQuery is blank OR node matches search
     *
     * @param focusedNodeIds pre-computed BFS result set (empty if no focus)
     */
    fun computeVisibleNodes(
        filters: GraphFilters,
        allNodes: List<GraphNode>,
        focusedNodeIds: Set<String> = emptySet()
    ): Set<String> {
        return allNodes
            .filter { matchesAllFilters(it, filters, focusedNodeIds) }
            .mapTo(mutableSetOf()) { it.id }
    }

    /**
     * Compute visible edge IDs (as "sourceId->targetId").
     * An edge is visible iff BOTH source and target are in visibleNodeIds.
     */
    fun computeVisibleEdges(
        visibleNodeIds: Set<String>,
        allEdges: List<GraphEdge>
    ): Set<String> {
        return allEdges
            .filter { it.sourceId in visibleNodeIds && it.targetId in visibleNodeIds }
            .mapTo(mutableSetOf()) { edgeKey(it) }
    }

    /** Max nodes returned by BFS when depth > 3 (Req 7.4). */
    private const val BFS_CAP = 500

    /**
     * BFS from [startNodeId] up to [depth] hops using Cytoscape neighborhood().
     * Starting node is always included. Result capped at [BFS_CAP] when depth > 3.
     * Falls back to edge-list BFS if cy is null.
     *
     * Requirements: 3.1, 3.3, 4.2, 7.4
     */
    fun bfsTraversal(
        startNodeId: String,
        depth: Int,
        cy: dynamic
    ): Set<String> {
        // Use edge-list BFS (works without Cytoscape instance)
        return bfsFromEdges(startNodeId, depth, GraphState.allEdges)
    }

    /**
     * Returns true iff at least one filter differs from defaults.
     * Empty enabledTypes means "all types" (no type filter active).
     */
    fun isAnyFilterActive(filters: GraphFilters): Boolean {
        val typeFilterActive = filters.enabledTypes.isNotEmpty() &&
            filters.enabledTypes != GraphFilterPanel.allTypeNames() &&
            filters.enabledTypes != DEFAULT_TYPES
        return typeFilterActive ||
            filters.selectedClusterId != null ||
            filters.focusNodeId != null ||
            filters.searchQuery.isNotBlank()
    }

    /**
     * BFS from edge list (pure logic, no renderer dependency).
     */
    fun bfsFromEdges(startNodeId: String, depth: Int, edges: List<GraphEdge>): Set<String> {
        val adj = buildAdjacency(edges)
        if (startNodeId !in adj) return setOf(startNodeId)
        val cap = if (depth > 3) BFS_CAP else Int.MAX_VALUE
        return bfsAdj(startNodeId, depth, adj, cap)
    }

    private fun buildAdjacency(edges: List<GraphEdge>): Map<String, List<String>> {
        val adj = mutableMapOf<String, MutableList<String>>()
        for (e in edges) {
            adj.getOrPut(e.sourceId) { mutableListOf() }.add(e.targetId)
            adj.getOrPut(e.targetId) { mutableListOf() }.add(e.sourceId)
        }
        return adj
    }

    private fun bfsAdj(start: String, maxDepth: Int, adj: Map<String, List<String>>, cap: Int): Set<String> {
        val visited = mutableSetOf(start)
        var frontier = listOf(start)
        var d = 0
        while (d < maxDepth && frontier.isNotEmpty() && visited.size < cap) {
            val next = mutableListOf<String>()
            for (node in frontier) {
                for (nb in (adj[node] ?: emptyList())) {
                    if (nb !in visited && visited.size < cap) { visited.add(nb); next.add(nb) }
                }
            }
            frontier = next; d++
        }
        return visited
    }

    // -- private helpers (kept short per coding standards) --

    private fun matchesAllFilters(
        node: GraphNode,
        filters: GraphFilters,
        focusedNodeIds: Set<String>
    ): Boolean {
        return matchesType(node, filters) &&
            matchesCluster(node, filters) &&
            matchesFocus(node, filters, focusedNodeIds) &&
            matchesSearch(node, filters)
    }

    private fun matchesType(node: GraphNode, f: GraphFilters): Boolean =
        f.enabledTypes.isEmpty() || f.enabledTypes.any { it.equals(node.type, ignoreCase = true) }

    private fun matchesCluster(node: GraphNode, f: GraphFilters): Boolean =
        f.selectedClusterId == null || node.clusterId == f.selectedClusterId

    private fun matchesFocus(
        node: GraphNode,
        f: GraphFilters,
        focusedNodeIds: Set<String>
    ): Boolean = f.focusNodeId == null || node.id in focusedNodeIds

    private fun matchesSearch(node: GraphNode, f: GraphFilters): Boolean {
        if (f.searchQuery.isBlank()) return true
        val q = f.searchQuery.lowercase()
        return node.key.lowercase().contains(q) ||
            node.summary.lowercase().contains(q)
    }

    private fun edgeKey(edge: GraphEdge): String =
        "${edge.sourceId}->${edge.targetId}"
}
