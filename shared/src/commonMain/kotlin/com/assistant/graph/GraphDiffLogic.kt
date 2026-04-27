package com.assistant.graph

/**
 * Pure diff logic for graph incremental updates.
 * Computes which nodes and edges are new given old and new graph states.
 * Extracted as pure functions for testability (no DOM/browser dependencies).
 */
object GraphDiffLogic {

    data class DiffResult(
        val newNodeIds: Set<String>,
        val newEdgeKeys: Set<String>
    )

    /**
     * Compute diff between old and new graph states.
     * @param oldNodeIds set of existing node IDs
     * @param oldEdgeKeys set of existing edge keys ("sourceId-targetId")
     * @param newNodeIds set of node IDs in the new graph response
     * @param newEdgeKeys set of edge keys in the new graph response
     * @return DiffResult with exactly the new elements
     */
    fun computeDiff(
        oldNodeIds: Set<String>,
        oldEdgeKeys: Set<String>,
        newNodeIds: Set<String>,
        newEdgeKeys: Set<String>
    ): DiffResult = DiffResult(
        newNodeIds = newNodeIds - oldNodeIds,
        newEdgeKeys = newEdgeKeys - oldEdgeKeys
    )

    /** Build edge key from source and target IDs (matches frontend edgeKey). */
    fun edgeKey(sourceId: String, targetId: String): String =
        "$sourceId-$targetId"
}
