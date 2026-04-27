package com.assistant.graph

import com.assistant.scan.ScanStatus

/**
 * Pure state machine for graph polling behavior.
 * Extracted from frontend GraphScanStatus for testability (no DOM/browser deps).
 * Tracks polling state, node counts, and handles scan status transitions.
 */
class GraphPollingStateMachine {

    var pollingActive: Boolean = false
        private set
    var nodeCount: Int = 0
        private set
    var currentNodeIds: Set<String> = emptySet()
        private set
    var currentEdgeKeys: Set<String> = emptySet()
        private set
    var finalLoadTriggered: Boolean = false
        private set
    var consecutiveErrors: Int = 0
        private set

    /** Start polling if scan status is SCANNING. Returns true if polling started. */
    fun onScanStatus(status: ScanStatus): Boolean {
        if (status == ScanStatus.SCANNING && !pollingActive) {
            pollingActive = true
            return true
        }
        if (isTerminal(status) && pollingActive) {
            pollingActive = false
            finalLoadTriggered = true
        }
        return false
    }

    /** Process a successful poll response. Returns diff result. */
    fun onPollSuccess(
        newNodeIds: Set<String>,
        newEdgeKeys: Set<String>
    ): GraphDiffLogic.DiffResult {
        consecutiveErrors = 0
        val diff = GraphDiffLogic.computeDiff(
            currentNodeIds, currentEdgeKeys, newNodeIds, newEdgeKeys
        )
        currentNodeIds = newNodeIds
        currentEdgeKeys = newEdgeKeys
        nodeCount = newNodeIds.size
        return diff
    }

    /** Handle API error during poll. Polling continues. */
    fun onPollError() {
        consecutiveErrors++
        // Polling continues — transient errors auto-recover
    }

    /** Check if scan status is terminal (polling should stop). */
    fun isTerminal(status: ScanStatus): Boolean =
        status == ScanStatus.COMPLETED ||
            status == ScanStatus.CANCELLED ||
            status == ScanStatus.IDLE
}
