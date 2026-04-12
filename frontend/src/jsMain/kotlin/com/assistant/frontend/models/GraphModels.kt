package com.assistant.frontend.models

import kotlinx.serialization.Serializable

@Serializable
data class GraphLayoutResponse(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val clusters: List<GraphCluster>? = null,
    val nodeTypes: List<NodeTypeInfo> = emptyList()
)

@Serializable
data class NodeTypeInfo(
    val type: String,
    val count: Int,
    val color: String
)

@Serializable
data class GraphNode(
    val id: String,
    val key: String,
    val summary: String,
    val description: String? = null,
    val type: String,
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val clusterId: Int? = null,
    val jiraUrl: String? = null
)

@Serializable
data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val type: String
)

@Serializable
data class GraphCluster(
    val id: Int,
    val color: String,
    val label: String? = null
)
