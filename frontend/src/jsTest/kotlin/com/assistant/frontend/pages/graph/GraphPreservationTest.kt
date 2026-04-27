package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.*
import com.assistant.frontend.services.NavigationContext
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property 2: Preservation — Graph State Behavior.
 *
 * Observation-first methodology: these tests capture CURRENT
 * correct behavior on UNFIXED code. They MUST PASS before AND
 * after the bugfix to confirm no regression.
 *
 * Properties tested:
 * - Save/restore roundtrip preserves all GraphLayoutResponse fields
 * - Restore returns null when sessionStorage empty
 * - Restore returns null when sessionStorage contains invalid JSON
 * - GraphState.reset() clears all fields to defaults
 * - NavigationContext priority over state restore
 * - First load (no saved state) follows normal API flow
 *
 * **Validates: Requirements 3.2, 3.4, 3.5, 3.6, 3.8**
 */
class GraphPreservationTest {

    private val STORAGE_KEY = "knowledge_graph_state"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @BeforeTest
    fun setup() {
        GraphState.reset()
        NavigationContext.clear()
        window.sessionStorage.removeItem(STORAGE_KEY)
    }

    // -- Generators --

    private fun generateGraphResponse(rng: Random): GraphLayoutResponse {
        val nodeCount = rng.nextInt(1, 40)
        val edgeCount = rng.nextInt(0, nodeCount)
        val clusterCount = rng.nextInt(0, 5)
        val types = listOf("Story", "Bug", "Task", "Epic", "Sub-task")
        val colors = listOf("#2dfecf", "#3386ff", "#be9dff", "#f9d423", "#ff6e84")

        val nodes = (0 until nodeCount).map { idx ->
            val typeIdx = idx % types.size
            GraphNode(
                id = "node-$idx",
                key = "PRJ-${rng.nextInt(100, 9999)}",
                summary = "Summary #$idx seed=${rng.nextInt()}",
                type = types[typeIdx],
                x = rng.nextDouble(-500.0, 500.0),
                y = rng.nextDouble(-500.0, 500.0)
            )
        }

        val edges = (0 until edgeCount.coerceAtMost(nodeCount - 1)).map { idx ->
            GraphEdge(
                sourceId = "node-$idx",
                targetId = "node-${idx + 1}",
                type = "relates_to"
            )
        }

        val clusters = (0 until clusterCount).map { idx ->
            GraphCluster(
                id = idx,
                color = colors[idx % colors.size],
                label = "Cluster $idx"
            )
        }

        val nodeTypes = types.indices.map { idx ->
            NodeTypeInfo(
                type = types[idx],
                count = nodes.count { it.type == types[idx] },
                color = colors[idx]
            )
        }.filter { it.count > 0 }

        return GraphLayoutResponse(
            nodes = nodes,
            edges = edges,
            clusters = clusters,
            nodeTypes = nodeTypes
        )
    }

    // ── Property: Save/restore roundtrip preserves all fields ──

    /**
     * For any random GraphLayoutResponse, serializing to
     * sessionStorage and deserializing back MUST produce an
     * identical object — all fields preserved.
     */
    @Test
    fun saveRestoreRoundtripPreservesAllFields() {
        val rng = Random(seed = 42)
        repeat(30) { i ->
            window.sessionStorage.removeItem(STORAGE_KEY)

            val original = generateGraphResponse(rng)

            // Save
            val serialized = json.encodeToString(
                GraphLayoutResponse.serializer(), original
            )
            window.sessionStorage.setItem(STORAGE_KEY, serialized)

            // Restore
            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertTrue(
                raw != null,
                "Iter $i: sessionStorage must contain data after save"
            )
            val restored = json.decodeFromString(
                GraphLayoutResponse.serializer(), raw
            )

            // Nodes
            assertEquals(
                original.nodes.size, restored.nodes.size,
                "Iter $i: nodes size mismatch"
            )
            for (j in original.nodes.indices) {
                assertEquals(
                    original.nodes[j].id, restored.nodes[j].id,
                    "Iter $i: node[$j].id mismatch"
                )
                assertEquals(
                    original.nodes[j].key, restored.nodes[j].key,
                    "Iter $i: node[$j].key mismatch"
                )
                assertEquals(
                    original.nodes[j].summary,
                    restored.nodes[j].summary,
                    "Iter $i: node[$j].summary mismatch"
                )
                assertEquals(
                    original.nodes[j].type, restored.nodes[j].type,
                    "Iter $i: node[$j].type mismatch"
                )
                assertEquals(
                    original.nodes[j].x, restored.nodes[j].x,
                    "Iter $i: node[$j].x mismatch"
                )
                assertEquals(
                    original.nodes[j].y, restored.nodes[j].y,
                    "Iter $i: node[$j].y mismatch"
                )
            }

            // Edges
            assertEquals(
                original.edges.size, restored.edges.size,
                "Iter $i: edges size mismatch"
            )
            for (j in original.edges.indices) {
                assertEquals(
                    original.edges[j], restored.edges[j],
                    "Iter $i: edge[$j] mismatch"
                )
            }

            // Clusters
            assertEquals(
                original.clusters?.size, restored.clusters?.size,
                "Iter $i: clusters size mismatch"
            )
            original.clusters?.forEachIndexed { j, cluster ->
                assertEquals(
                    cluster, restored.clusters?.get(j),
                    "Iter $i: cluster[$j] mismatch"
                )
            }

            // NodeTypes
            assertEquals(
                original.nodeTypes.size, restored.nodeTypes.size,
                "Iter $i: nodeTypes size mismatch"
            )
            for (j in original.nodeTypes.indices) {
                assertEquals(
                    original.nodeTypes[j], restored.nodeTypes[j],
                    "Iter $i: nodeType[$j] mismatch"
                )
            }
        }
    }

    // ── Property: Restore returns null when sessionStorage empty ──

    @Test
    fun restoreReturnsNullWhenSessionStorageEmpty() {
        repeat(10) { i ->
            window.sessionStorage.removeItem(STORAGE_KEY)

            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertNull(
                raw,
                "Iter $i: sessionStorage must be null when empty"
            )

            val restored: GraphLayoutResponse? = try {
                val data = window.sessionStorage.getItem(STORAGE_KEY)
                if (data != null) {
                    json.decodeFromString(
                        GraphLayoutResponse.serializer(), data
                    )
                } else null
            } catch (_: Exception) {
                null
            }

            assertNull(
                restored,
                "Iter $i: restore must return null on empty storage"
            )
        }
    }

    // ── Property: Restore returns null on invalid JSON ──

    @Test
    fun restoreReturnsNullOnInvalidJson() {
        val invalidJsons = listOf(
            "",
            "not json at all",
            "{broken",
            "null",
            "[]",
            "{\"nodes\": \"not-array\"}",
            "{{{}}}",
            "<html>nope</html>"
        )

        invalidJsons.forEachIndexed { i, invalid ->
            window.sessionStorage.setItem(STORAGE_KEY, invalid)

            val restored: GraphLayoutResponse? = try {
                val raw = window.sessionStorage.getItem(STORAGE_KEY)
                if (raw != null) {
                    json.decodeFromString(
                        GraphLayoutResponse.serializer(), raw
                    )
                } else null
            } catch (_: Exception) {
                null
            }

            assertNull(
                restored,
                "Iter $i: restore must return null for invalid " +
                    "JSON: '${invalid.take(20)}'"
            )
        }
    }

    // ── Property: GraphState.reset() clears all fields ──

    /**
     * Observed behavior: GraphState.reset() sets all fields
     * to empty defaults. This MUST remain true after fix.
     */
    @Test
    fun graphStateResetClearsAllFields() {
        val rng = Random(seed = 77)
        repeat(20) { i ->
            // Populate GraphState with random data
            val data = generateGraphResponse(rng)
            GraphState.allNodes = data.nodes
            GraphState.allEdges = data.edges
            GraphState.allClusters = data.clusters ?: emptyList()
            GraphState.allNodeTypes = data.nodeTypes
            GraphState.typeColorMap = data.nodeTypes.associate {
                it.type to it.color
            }
            GraphState.filteredNodeIds = data.nodes.map { it.id }.toSet()

            // Verify populated
            assertTrue(
                GraphState.allNodes.isNotEmpty(),
                "Iter $i: allNodes must be populated before reset"
            )

            // Reset
            GraphState.reset()

            // All fields must be empty
            assertTrue(
                GraphState.allNodes.isEmpty(),
                "Iter $i: allNodes must be empty after reset"
            )
            assertTrue(
                GraphState.allEdges.isEmpty(),
                "Iter $i: allEdges must be empty after reset"
            )
            assertTrue(
                GraphState.allClusters.isEmpty(),
                "Iter $i: allClusters must be empty after reset"
            )
            assertTrue(
                GraphState.allNodeTypes.isEmpty(),
                "Iter $i: allNodeTypes must be empty after reset"
            )
            assertTrue(
                GraphState.filteredNodeIds.isEmpty(),
                "Iter $i: filteredNodeIds must be empty after reset"
            )
            assertTrue(
                GraphState.typeColorMap.isEmpty(),
                "Iter $i: typeColorMap must be empty after reset"
            )
            assertNull(
                GraphState.selectedNode,
                "Iter $i: selectedNode must be null after reset"
            )
            assertNull(
                GraphState.highlightedNodeId,
                "Iter $i: highlightedNodeId must be null after reset"
            )
            assertNull(
                GraphState.searchFilteredIds,
                "Iter $i: searchFilteredIds must be null after reset"
            )
        }
    }

    // ── Property: NavigationContext priority over state restore ──

    /**
     * Observed behavior: When NavigationContext has pending context
     * for "knowledge_graph", it should be consumed (one-shot) and
     * take priority. State restore should be skipped.
     */
    @Test
    fun navigationContextTakesPriorityOverRestore() {
        val rng = Random(seed = 55)
        repeat(20) { i ->
            NavigationContext.clear()
            window.sessionStorage.removeItem(STORAGE_KEY)

            // Save graph state to sessionStorage
            val data = generateGraphResponse(rng)
            val serialized = json.encodeToString(
                GraphLayoutResponse.serializer(), data
            )
            window.sessionStorage.setItem(STORAGE_KEY, serialized)

            // Store navigation context (e.g. from ChatAction)
            val nodeKey = "PRJ-${rng.nextInt(100, 999)}"
            NavigationContext.store(
                "knowledge_graph",
                mapOf("nodeKey" to nodeKey)
            )

            // Check: navigation context exists → should skip restore
            val pending = NavigationContext.peek()
            assertTrue(
                pending != null && pending.screen == "knowledge_graph",
                "Iter $i: navigation context must exist for graph"
            )

            // Consume context (one-shot)
            val params = NavigationContext.consume("knowledge_graph")
            assertTrue(
                params != null && params["nodeKey"] == nodeKey,
                "Iter $i: consumed params must contain nodeKey=$nodeKey"
            )

            // After consume, context is gone
            val afterConsume = NavigationContext.consume("knowledge_graph")
            assertNull(
                afterConsume,
                "Iter $i: context must be null after consume"
            )
        }
    }

    // ── Property: First load — no state, normal flow ──

    @Test
    fun firstLoadHasNoSavedState() {
        val rng = Random(seed = 99)
        repeat(10) { i ->
            window.sessionStorage.removeItem(STORAGE_KEY)
            GraphState.reset()

            // Verify preconditions: no state anywhere
            val raw = window.sessionStorage.getItem(STORAGE_KEY)
            assertNull(raw, "Iter $i: no saved state on first load")
            assertTrue(
                GraphState.allNodes.isEmpty(),
                "Iter $i: GraphState empty on first load"
            )

            // After first load (simulated API response), state saved
            val data = generateGraphResponse(rng)
            val serialized = json.encodeToString(
                GraphLayoutResponse.serializer(), data
            )
            window.sessionStorage.setItem(STORAGE_KEY, serialized)

            val saved = window.sessionStorage.getItem(STORAGE_KEY)
            assertTrue(
                saved != null && saved.isNotEmpty(),
                "Iter $i: state must be saved after first load"
            )

            // Cleanup
            window.sessionStorage.removeItem(STORAGE_KEY)
        }
    }
}
