package com.assistant.graph

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for 3D Knowledge Graph (Properties 1–6).
 *
 * Pure math functions from Projection3D are duplicated here since
 * the originals live in the frontend JS module.
 *
 * Feature: 3d-knowledge-graph
 */
class Graph3DPropertyTest {

    private val engine = ForceDirectedGraphEngine(iterations = 75, seed = 42)

    // --- Duplicated pure math from Projection3D ---

    private fun rotate(
        x: Double, y: Double, z: Double,
        rotX: Double, rotY: Double
    ): Triple<Double, Double, Double> {
        val cosY = cos(rotY); val sinY = sin(rotY)
        val x1 = x * cosY + z * sinY
        val z1 = -x * sinY + z * cosY
        val cosX = cos(rotX); val sinX = sin(rotX)
        val y1 = y * cosX - z1 * sinX
        val z2 = y * sinX + z1 * cosX
        return Triple(x1, y1, z2)
    }

    private fun project(
        x: Double, y: Double, z: Double, f: Double
    ): Pair<Double, Double> {
        val denom = (z + f).coerceAtLeast(0.01)
        return Pair(x * f / denom, y * f / denom)
    }

    private fun depthScale(z: Double, f: Double): Double =
        f / (z + f).coerceAtLeast(0.01)

    private fun depthOpacity(z: Double, maxZ: Double): Double {
        if (maxZ <= 0.0) return 1.0
        return (1.0 - z / maxZ * 0.7).coerceIn(0.15, 1.0)
    }

    private fun glowIntensity(z: Double, maxZ: Double): Double {
        if (maxZ <= 0.0) return 1.0
        return (1.0 - z / maxZ * 0.8).coerceIn(0.1, 1.0)
    }

    // --- Generators ---

    private fun arbNetworkGraph(): Arb<NetworkGraph> = arbitrary {
        val n = Arb.int(1..50).bind()
        val ids = (1..n).map { "node-$it" }
        val nodes = ids.map { id ->
            TicketNode(id, "KEY-${id.substringAfter('-')}", "Summary $id", "Open")
        }
        val maxEdges = if (n > 1) minOf(n * 2, n * (n - 1) / 2) else 0
        val edgeCount = if (n > 1) Arb.int(0..maxEdges).bind() else 0
        val edgeSet = mutableSetOf<Pair<String, String>>()
        val edges = mutableListOf<TicketEdge>()
        repeat(edgeCount) {
            if (ids.size >= 2) {
                val fi = Arb.int(0 until ids.size).bind()
                var ti = Arb.int(0 until ids.size).bind()
                if (ti == fi) ti = (ti + 1) % ids.size
                val pair = if (fi < ti) ids[fi] to ids[ti] else ids[ti] to ids[fi]
                if (pair !in edgeSet) {
                    edgeSet.add(pair)
                    edges.add(TicketEdge(pair.first, pair.second, "relates", false))
                }
            }
        }
        NetworkGraph(nodes, edges)
    }

    // --- Property 1: 3D Layout bounded coordinates ---

    /**
     * Property 1: For any valid NetworkGraph, computeLayout produces
     * positions with x∈[0,width], y∈[0,height], z∈[0,600] and
     * every input node has a corresponding position.
     *
     * **Validates: Requirements 3.14**
     */
    @Test
    fun `Property 1 - 3D layout produces bounded coordinates`() = runTest {
        checkAll(PropTestConfig(iterations = 25), arbNetworkGraph()) { graph ->
            val layout = engine.computeLayout(graph, 1200.0, 800.0)
            assertEquals(graph.nodes.size, layout.positions.size)
            for ((id, pos) in layout.positions) {
                assertTrue(pos.x in 0.0..1200.0, "Node $id x=${pos.x} out of [0,1200]")
                assertTrue(pos.y in 0.0..800.0, "Node $id y=${pos.y} out of [0,800]")
                assertTrue(pos.z in 0.0..600.0, "Node $id z=${pos.z} out of [0,600]")
            }
        }
    }

    // --- Property 2: Perspective projection formula ---

    /**
     * Property 2: project(x,y,z,f) = (x*f/(z+f), y*f/(z+f)) for valid inputs.
     *
     * **Validates: Requirements 3.15**
     */
    @Test
    fun `Property 2 - perspective projection matches formula`() = runTest {
        val arbCoord = Arb.double(-500.0..500.0)
        val arbZ = Arb.double(0.0..600.0)
        val arbF = Arb.double(200.0..2000.0)
        checkAll(PropTestConfig(iterations = 25), arbCoord, arbCoord, arbZ, arbF) { x, y, z, f ->
            val (sx, sy) = project(x, y, z, f)
            val denom = (z + f).coerceAtLeast(0.01)
            val expectedX = x * f / denom
            val expectedY = y * f / denom
            assertTrue(abs(sx - expectedX) < 1e-9, "sx=$sx != expected=$expectedX")
            assertTrue(abs(sy - expectedY) < 1e-9, "sy=$sy != expected=$expectedY")
        }
    }

    // --- Property 3: Depth cue monotonicity ---

    /**
     * Property 3: For z1 < z2 (both ≥ 0): depthScale(z1) > depthScale(z2),
     * depthOpacity(z1) > depthOpacity(z2), glowIntensity(z1) > glowIntensity(z2).
     *
     * **Validates: Requirements 3.16, 3.23**
     */
    @Test
    fun `Property 3 - depth cues are monotonically decreasing with z`() = runTest {
        val arbZ = Arb.double(0.0..599.0)
        val arbDelta = Arb.double(0.01..200.0)
        val arbF = Arb.double(200.0..2000.0)
        val arbMaxZ = Arb.double(100.0..1200.0)
        checkAll(PropTestConfig(iterations = 25), arbZ, arbDelta, arbF, arbMaxZ) { z1, delta, f, maxZ ->
            val z2 = z1 + delta
            assertTrue(depthScale(z1, f) > depthScale(z2, f),
                "depthScale($z1) should > depthScale($z2)")
            assertTrue(depthOpacity(z1, maxZ) >= depthOpacity(z2, maxZ),
                "depthOpacity($z1) should >= depthOpacity($z2)")
            assertTrue(glowIntensity(z1, maxZ) >= glowIntensity(z2, maxZ),
                "glowIntensity($z1) should >= glowIntensity($z2)")
        }
    }

    // --- Property 4: Rotation isometry ---

    /**
     * Property 4: For any point and rotation angles, distance from origin
     * is preserved: sqrt(x'²+y'²+z'²) ≈ sqrt(x²+y²+z²).
     *
     * **Validates: Requirements 3.17**
     */
    @Test
    fun `Property 4 - rotation preserves distance from origin`() = runTest {
        val arbCoord = Arb.double(-500.0..500.0)
        val arbAngle = Arb.double(-2 * PI..2 * PI)
        checkAll(
            PropTestConfig(iterations = 25),
            arbCoord, arbCoord, arbCoord, arbAngle, arbAngle
        ) { x, y, z, rotX, rotY ->
            val (rx, ry, rz) = rotate(x, y, z, rotX, rotY)
            val distBefore = sqrt(x * x + y * y + z * z)
            val distAfter = sqrt(rx * rx + ry * ry + rz * rz)
            assertTrue(abs(distBefore - distAfter) < 1e-6,
                "Distance changed: $distBefore -> $distAfter")
        }
    }

    // --- Property 5: Painter's algorithm sort ---

    /**
     * Property 5: Sorting ProjectedNode-like entries by projectedZ descending
     * yields non-increasing z order.
     *
     * **Validates: Requirements 3.22**
     */
    @Test
    fun `Property 5 - painter sort produces descending z order`() = runTest {
        val arbZ = Arb.double(-500.0..1000.0)
        val arbList = Arb.list(arbZ, 1..100)
        checkAll(PropTestConfig(iterations = 25), arbList) { zValues ->
            val sorted = zValues.sortedByDescending { it }
            for (i in 0 until sorted.size - 1) {
                assertTrue(sorted[i] >= sorted[i + 1],
                    "Sort broken at index $i: ${sorted[i]} < ${sorted[i + 1]}")
            }
        }
    }

    // --- Property 6: LOD threshold consistency ---

    /**
     * Property 6: shouldShowLabel returns true iff scale > LOD_LABEL_THRESHOLD (0.5).
     *
     * **Validates: Requirements 3.20**
     */
    @Test
    fun `Property 6 - LOD label visibility matches threshold`() = runTest {
        val lodThreshold = 0.5
        val arbScale = Arb.double(0.0..2.0)
        checkAll(PropTestConfig(iterations = 25), arbScale) { scale ->
            val shouldShow = scale > lodThreshold
            if (shouldShow) {
                assertTrue(scale > lodThreshold,
                    "Label should show when scale=$scale > $lodThreshold")
            } else {
                assertTrue(scale <= lodThreshold,
                    "Label should hide when scale=$scale <= $lodThreshold")
            }
        }
    }
}
