package com.assistant.graph

import com.assistant.domain.NetworkGraph
import kotlinx.serialization.Serializable

/**
 * Data models for graph layout and clustering.
 */
@Serializable
data class Position(val x: Double, val y: Double, val z: Double = 0.0)

@Serializable
data class Bounds(val width: Double, val height: Double)

@Serializable
data class GraphLayout(
    val positions: Map<String, Position>,
    val bounds: Bounds
)

@Serializable
data class Cluster(val id: Int, val nodeIds: List<String>, val color: String)

/**
 * Engine for computing force-directed graph layouts and detecting clusters.
 */
interface GraphEngine {
    fun computeLayout(graph: NetworkGraph, width: Double, height: Double): GraphLayout
    fun detectClusters(graph: NetworkGraph): List<Cluster>
}
