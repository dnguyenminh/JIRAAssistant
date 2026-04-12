package com.assistant.server.routes

import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.graph.filterNodes
import com.assistant.kb.KBRepository
import com.assistant.rbac.Permission
import com.assistant.server.middleware.withPermission
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

private fun buildClusterMap(clusters: List<Cluster>): Map<String, Int> {
    val map = mutableMapOf<String, Int>()
    for (cluster in clusters) {
        for (nodeId in cluster.nodeIds) {
            map[nodeId] = cluster.id
        }
    }
    return map
}

/** Build a human-readable label from the top 3 ticket keys in the cluster. */
private fun buildClusterLabel(
    cluster: Cluster,
    nodeMap: Map<String, com.assistant.domain.TicketNode>
): String {
    val topKeys = cluster.nodeIds
        .take(3)
        .mapNotNull { nodeMap[it]?.key }
    return if (topKeys.isEmpty()) "Cluster ${cluster.id}"
    else topKeys.joinToString(", ") + if (cluster.nodeIds.size > 3) " …" else ""
}

/** Assign a color to a node type based on a rotating palette. */
private val TYPE_COLORS = listOf("#2dfecf", "#3386ff", "#be9dff", "#ff9d5c", "#f9d423", "#ff6e84", "#6ee7b7", "#93c5fd")

private fun buildNodeTypeInfos(nodes: List<GraphNodeDto>): List<NodeTypeInfoDto> {
    return nodes.groupBy { it.type }
        .entries
        .sortedByDescending { it.value.size }
        .mapIndexed { i, (type, group) ->
            NodeTypeInfoDto(type, group.size, TYPE_COLORS[i % TYPE_COLORS.size])
        }
}

@Serializable
private data class NodeTypeInfoDto(
    val type: String,
    val count: Int,
    val color: String
)

@Serializable
private data class GraphResponse(
    val nodes: List<GraphNodeDto>,
    val edges: List<GraphEdgeDto>,
    val clusters: List<GraphClusterDto>? = null,
    val nodeTypes: List<NodeTypeInfoDto> = emptyList()
)

@Serializable
private data class GraphNodeDto(
    val id: String,
    val key: String,
    val summary: String,
    val description: String? = null,
    val type: String,
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val clusterId: Int? = null
)

@Serializable
private data class GraphEdgeDto(
    val sourceId: String,
    val targetId: String,
    val type: String
)

@Serializable
private data class GraphClusterDto(
    val id: Int,
    val color: String,
    val label: String? = null
)

/**
 * GET /api/graph/{projectKey} — returns graph layout, clusters, and optionally filtered nodes.
 * Requires Reader+ role (VIEW_GRAPH permission).
 * Optional query param: ?search=... for node filtering.
 */
fun Routing.graphRoutes() {
    val kbRepository by inject<KBRepository>()
    val graphEngine by inject<GraphEngine>()

    route("/api/graph") {
        withPermission(Permission.VIEW_GRAPH) {
            get("/{projectKey}") {
                val projectKey = call.parameters["projectKey"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("projectKey is required"))

                val graph = kbRepository.getGraphData(projectKey)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Graph data not found for project: $projectKey"))

                val searchQuery = call.request.queryParameters["search"]
                val filteredGraph = if (!searchQuery.isNullOrBlank()) {
                    val filteredNodes = filterNodes(graph.nodes, searchQuery)
                    val filteredNodeIds = filteredNodes.map { it.id }.toSet()
                    graph.copy(
                        nodes = filteredNodes,
                        edges = graph.edges.filter { it.fromId in filteredNodeIds && it.toId in filteredNodeIds }
                    )
                } else {
                    graph
                }

                val layout = graphEngine.computeLayout(filteredGraph, width = 1200.0, height = 800.0)
                val clusters = graphEngine.detectClusters(filteredGraph)

                val clusterMap = buildClusterMap(clusters)
                val nodes = filteredGraph.nodes.map { node ->
                    val pos = layout.positions[node.id]
                    GraphNodeDto(
                        id = node.id,
                        key = node.key,
                        summary = node.summary,
                        description = node.description,
                        type = node.featureName ?: "Story",
                        x = pos?.x ?: 0.0,
                        y = pos?.y ?: 0.0,
                        z = pos?.z ?: 0.0,
                        clusterId = clusterMap[node.id]
                    )
                }
                val edges = filteredGraph.edges.map { edge ->
                    GraphEdgeDto(
                        sourceId = edge.fromId,
                        targetId = edge.toId,
                        type = edge.relationshipType
                    )
                }
                val nodeMap = filteredGraph.nodes.associateBy { it.id }
                val clusterDtos = clusters.map { c ->
                    GraphClusterDto(
                        id = c.id, color = c.color,
                        label = buildClusterLabel(c, nodeMap)
                    )
                }

                call.respond(HttpStatusCode.OK, GraphResponse(
                    nodes = nodes,
                    edges = edges,
                    clusters = clusterDtos,
                    nodeTypes = buildNodeTypeInfos(nodes)
                ))
            }
        }
    }
}
