package com.assistant.graph

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketNode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Internal state for force-directed simulation arrays.
 */
private class LayoutState(
    val posX: DoubleArray, val posY: DoubleArray, val posZ: DoubleArray,
    val dispX: DoubleArray, val dispY: DoubleArray, val dispZ: DoubleArray
) {
    fun resetDisplacements() {
        dispX.fill(0.0); dispY.fill(0.0); dispZ.fill(0.0)
    }
}

/**
 * Fruchterman-Reingold force-directed layout engine (3D).
 *
 * Repulsion: F_rep = k² / d  (between all node pairs)
 * Attraction: F_att = d² / k  (between connected nodes)
 * k = C * sqrt(area / |V|), C = 0.5
 * Runs 75 iterations with cooling temperature.
 */
class ForceDirectedGraphEngine(
    private val iterations: Int = 75,
    private val C: Double = 0.5,
    private val seed: Long? = null
) : GraphEngine {

    companion object {
        const val DEPTH: Double = 600.0
    }

    override fun computeLayout(graph: NetworkGraph, width: Double, height: Double): GraphLayout {
        val nodes = graph.nodes
        if (nodes.isEmpty()) {
            return GraphLayout(positions = emptyMap(), bounds = Bounds(width, height))
        }
        if (nodes.size == 1) {
            val center = Position(width / 2.0, height / 2.0, DEPTH / 2.0)
            return GraphLayout(positions = mapOf(nodes[0].id to center), bounds = Bounds(width, height))
        }

        val k = C * sqrt(width * height / nodes.size.toDouble())
        val random = if (seed != null) Random(seed) else Random
        val margin = min(width, height) * 0.1
        val state = initPositions(nodes.size, width, height, margin, random)
        val idToIndex = buildIndexMap(nodes)
        val edgePairs = buildEdgePairs(graph, idToIndex)

        runSimulation(state, edgePairs, k, width, height, nodes.size)

        return buildLayout(nodes, state, width, height)
    }

    private fun initPositions(
        n: Int, width: Double, height: Double, margin: Double, random: Random
    ): LayoutState {
        return LayoutState(
            posX = DoubleArray(n) { margin + random.nextDouble() * (width - 2 * margin) },
            posY = DoubleArray(n) { margin + random.nextDouble() * (height - 2 * margin) },
            posZ = DoubleArray(n) { margin + random.nextDouble() * (DEPTH - 2 * margin) },
            dispX = DoubleArray(n), dispY = DoubleArray(n), dispZ = DoubleArray(n)
        )
    }

    private fun buildIndexMap(nodes: List<TicketNode>): HashMap<String, Int> {
        val map = HashMap<String, Int>(nodes.size)
        nodes.forEachIndexed { i, node -> map[node.id] = i }
        return map
    }

    private fun buildEdgePairs(
        graph: NetworkGraph, idToIndex: Map<String, Int>
    ): List<Pair<Int, Int>> {
        return graph.edges.mapNotNull { edge ->
            val from = idToIndex[edge.fromId]
            val to = idToIndex[edge.toId]
            if (from != null && to != null && from != to) Pair(from, to) else null
        }
    }

    private fun runSimulation(
        s: LayoutState, edges: List<Pair<Int, Int>>,
        k: Double, width: Double, height: Double, n: Int
    ) {
        var temp = min(width, height) / 2.0
        val cooling = temp / iterations
        for (iter in 0 until iterations) {
            s.resetDisplacements()
            applyRepulsion(s, k, n)
            applyAttraction(s, edges, k)
            applyDisplacements(s, temp, width, height, n)
            temp = max(temp - cooling, 0.01)
        }
    }

    private fun applyRepulsion(s: LayoutState, k: Double, n: Int) {
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = s.posX[i] - s.posX[j]
                val dy = s.posY[i] - s.posY[j]
                val dz = s.posZ[i] - s.posZ[j]
                val dist = max(sqrt(dx * dx + dy * dy + dz * dz), 0.01)
                val force = (k * k) / dist
                applyForce3D(s, i, j, dx, dy, dz, dist, force, repulsive = true)
            }
        }
    }

    private fun applyAttraction(s: LayoutState, edges: List<Pair<Int, Int>>, k: Double) {
        for ((from, to) in edges) {
            val dx = s.posX[from] - s.posX[to]
            val dy = s.posY[from] - s.posY[to]
            val dz = s.posZ[from] - s.posZ[to]
            val dist = max(sqrt(dx * dx + dy * dy + dz * dz), 0.01)
            val force = (dist * dist) / k
            applyForce3D(s, from, to, dx, dy, dz, dist, force, repulsive = false)
        }
    }

    private fun applyForce3D(
        s: LayoutState, i: Int, j: Int,
        dx: Double, dy: Double, dz: Double,
        dist: Double, force: Double, repulsive: Boolean
    ) {
        val fx = (dx / dist) * force
        val fy = (dy / dist) * force
        val fz = (dz / dist) * force
        if (repulsive) {
            s.dispX[i] += fx; s.dispY[i] += fy; s.dispZ[i] += fz
            s.dispX[j] -= fx; s.dispY[j] -= fy; s.dispZ[j] -= fz
        } else {
            s.dispX[i] -= fx; s.dispY[i] -= fy; s.dispZ[i] -= fz
            s.dispX[j] += fx; s.dispY[j] += fy; s.dispZ[j] += fz
        }
    }

    private fun applyDisplacements(
        s: LayoutState, temp: Double, width: Double, height: Double, n: Int
    ) {
        for (i in 0 until n) {
            val len = max(sqrt(s.dispX[i] * s.dispX[i] + s.dispY[i] * s.dispY[i] + s.dispZ[i] * s.dispZ[i]), 0.01)
            val capped = min(len, temp)
            s.posX[i] += (s.dispX[i] / len) * capped
            s.posY[i] += (s.dispY[i] / len) * capped
            s.posZ[i] += (s.dispZ[i] / len) * capped
            s.posX[i] = s.posX[i].coerceIn(0.0, width)
            s.posY[i] = s.posY[i].coerceIn(0.0, height)
            s.posZ[i] = s.posZ[i].coerceIn(0.0, DEPTH)
        }
    }

    private fun buildLayout(
        nodes: List<TicketNode>, s: LayoutState, width: Double, height: Double
    ): GraphLayout {
        val positions = HashMap<String, Position>(nodes.size)
        nodes.forEachIndexed { i, node ->
            positions[node.id] = Position(s.posX[i], s.posY[i], s.posZ[i])
        }
        return GraphLayout(positions = positions, bounds = Bounds(width, height))
    }

    override fun detectClusters(graph: NetworkGraph): List<Cluster> {
        return detectConnectedComponents(graph)
    }

    /**
     * Connected-components clustering using union-find.
     */
    private fun detectConnectedComponents(graph: NetworkGraph): List<Cluster> {
        val nodes = graph.nodes
        if (nodes.isEmpty()) return emptyList()

        val idToIndex = HashMap<String, Int>(nodes.size)
        nodes.forEachIndexed { i, node -> idToIndex[node.id] = i }

        // Union-Find
        val parent = IntArray(nodes.size) { it }
        val rank = IntArray(nodes.size) { 0 }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            // Path compression
            var c = x
            while (c != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a); val rb = find(b)
            if (ra == rb) return
            if (rank[ra] < rank[rb]) parent[ra] = rb
            else if (rank[ra] > rank[rb]) parent[rb] = ra
            else { parent[rb] = ra; rank[ra]++ }
        }

        for (edge in graph.edges) {
            val from = idToIndex[edge.fromId]
            val to = idToIndex[edge.toId]
            if (from != null && to != null) union(from, to)
        }

        // Group nodes by component root
        val components = HashMap<Int, MutableList<String>>()
        for (i in nodes.indices) {
            val root = find(i)
            components.getOrPut(root) { mutableListOf() }.add(nodes[i].id)
        }

        val clusterColors = listOf(
            "#00FFFF", "#4169E1", "#8A2BE2", "#FF6347",
            "#32CD32", "#FFD700", "#FF69B4", "#00CED1",
            "#FF4500", "#7B68EE"
        )

        return components.entries.mapIndexed { idx, (_, nodeIds) ->
            Cluster(
                id = idx,
                nodeIds = nodeIds,
                color = clusterColors[idx % clusterColors.size]
            )
        }
    }
}
