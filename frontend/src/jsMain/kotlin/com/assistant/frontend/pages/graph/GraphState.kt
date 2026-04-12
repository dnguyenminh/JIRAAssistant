package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphCluster
import com.assistant.frontend.models.GraphEdge
import com.assistant.frontend.models.GraphNode
import com.assistant.frontend.models.NodeTypeInfo

/**
 * Shared mutable state for the Knowledge Graph page.
 */
internal object GraphState {

    var allNodes: List<GraphNode> = emptyList()
    var allEdges: List<GraphEdge> = emptyList()
    var allClusters: List<GraphCluster> = emptyList()
    var allNodeTypes: List<NodeTypeInfo> = emptyList()
    var filteredNodeIds: Set<String> = emptySet()
    var selectedNode: GraphNode? = null
    var highlightedNodeId: String? = null
    var searchFilteredIds: Set<String>? = null

    /** Dynamic type→color map, populated from backend response. */
    var typeColorMap: Map<String, String> = emptyMap()

    val defaultClusterColors = listOf(
        "rgba(45, 254, 207, 0.06)",
        "rgba(51, 134, 255, 0.06)",
        "rgba(190, 157, 255, 0.06)",
        "rgba(249, 212, 35, 0.06)",
        "rgba(255, 110, 132, 0.06)"
    )

    fun reset() {
        allNodes = emptyList(); allEdges = emptyList(); allClusters = emptyList()
        allNodeTypes = emptyList(); filteredNodeIds = emptySet(); selectedNode = null
        highlightedNodeId = null; searchFilteredIds = null; typeColorMap = emptyMap()
    }
}
