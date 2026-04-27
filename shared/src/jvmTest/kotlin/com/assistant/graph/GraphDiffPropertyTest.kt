package com.assistant.graph

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property tests for graph diff correctness.
 * Tag: Feature: incremental-graph-rendering, Property 4: Graph diff correctness
 * **Validates: Requirements 2.2**
 */
@OptIn(ExperimentalKotest::class)
class GraphDiffPropertyTest {

    private fun arbNodeId(): Arb<String> =
        Arb.string(3, 10, Codepoint.alphanumeric()).map { "N-$it" }

    private fun arbEdgePair(): Arb<Pair<String, String>> =
        Arb.pair(arbNodeId(), arbNodeId()).filter { (s, t) -> s != t }

    /**
     * Property 4: For any oldGraph ⊆ newGraph, diff produces exactly
     * the node IDs and edge keys in newGraph but not in oldGraph.
     * **Validates: Requirements 2.2**
     */
    @Test
    fun diffProducesExactlyNewNodeIdsAndEdgeKeys() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.set(arbNodeId(), 0..20),
            Arb.set(arbNodeId(), 0..15),
            Arb.set(arbEdgePair(), 0..15),
            Arb.set(arbEdgePair(), 0..10)
        ) { oldNodes, extraNodes, oldEdgePairs, extraEdgePairs ->
            val newNodeIds = oldNodes + extraNodes
            val oldEdgeKeys = toEdgeKeys(oldEdgePairs)
            val newEdgeKeys = oldEdgeKeys + toEdgeKeys(extraEdgePairs)

            val diff = GraphDiffLogic.computeDiff(
                oldNodes, oldEdgeKeys, newNodeIds, newEdgeKeys
            )

            assertEquals(
                newNodeIds - oldNodes, diff.newNodeIds,
                "Diff node IDs should be exactly newGraph - oldGraph"
            )
            assertEquals(
                newEdgeKeys - oldEdgeKeys, diff.newEdgeKeys,
                "Diff edge keys should be exactly newGraph - oldGraph"
            )
        }
    }

    /**
     * Property 4 (empty old): When old graph is empty,
     * diff returns all elements from new graph.
     * **Validates: Requirements 2.2**
     */
    @Test
    fun diffFromEmptyOldGraphReturnsAllNewElements() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.set(arbNodeId(), 1..20),
            Arb.set(arbEdgePair(), 1..15)
        ) { newNodes, newEdgePairs ->
            val newEdgeKeys = toEdgeKeys(newEdgePairs)

            val diff = GraphDiffLogic.computeDiff(
                emptySet(), emptySet(), newNodes, newEdgeKeys
            )

            assertEquals(newNodes, diff.newNodeIds)
            assertEquals(newEdgeKeys, diff.newEdgeKeys)
        }
    }

    /**
     * Property 4 (identical): When old == new, diff is empty.
     * **Validates: Requirements 2.2**
     */
    @Test
    fun diffOfIdenticalGraphsProducesEmptyResult() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.set(arbNodeId(), 0..20),
            Arb.set(arbEdgePair(), 0..15)
        ) { nodes, edgePairs ->
            val edgeKeys = toEdgeKeys(edgePairs)

            val diff = GraphDiffLogic.computeDiff(
                nodes, edgeKeys, nodes, edgeKeys
            )

            assertTrue(diff.newNodeIds.isEmpty())
            assertTrue(diff.newEdgeKeys.isEmpty())
        }
    }

    private fun toEdgeKeys(pairs: Set<Pair<String, String>>): Set<String> =
        pairs.map { (s, t) -> GraphDiffLogic.edgeKey(s, t) }.toSet()
}
