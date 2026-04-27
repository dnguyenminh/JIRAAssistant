package com.assistant.graph

import com.assistant.scan.ScanStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for fade-in rendering logic and loading state detection.
 * CytoscapeStyles lives in frontend (Kotlin/JS) — we test the shared
 * logic patterns that drive fade-in and loading message behavior.
 * Requirements: 2.3, 3.3
 */
class FadeInRenderingTest {

    // -- Req 2.3: Fade-in CSS class applied to new elements ----------------

    /**
     * Req 2.3: First poll with data produces diff where ALL elements are new,
     * triggering fade-in for every node and edge.
     */
    @Test
    fun `first poll produces diff with all elements as new`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)

        val diff = sm.onPollSuccess(
            setOf("NODE-1", "NODE-2", "NODE-3"),
            setOf("NODE-1-NODE-2", "NODE-2-NODE-3")
        )

        assertEquals(
            setOf("NODE-1", "NODE-2", "NODE-3"),
            diff.newNodeIds,
            "All nodes should be new on first poll (fade-in targets)"
        )
        assertEquals(
            setOf("NODE-1-NODE-2", "NODE-2-NODE-3"),
            diff.newEdgeKeys,
            "All edges should be new on first poll (fade-in targets)"
        )
    }

    /**
     * Req 2.3: Subsequent poll with additional elements produces diff
     * containing ONLY the new elements — fade-in applies only to them.
     */
    @Test
    fun `incremental poll produces diff with only new elements`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onPollSuccess(setOf("A", "B"), setOf("A-B"))

        val diff = sm.onPollSuccess(
            setOf("A", "B", "C", "D"),
            setOf("A-B", "C-D", "A-C")
        )

        assertEquals(setOf("C", "D"), diff.newNodeIds)
        assertEquals(setOf("C-D", "A-C"), diff.newEdgeKeys)
    }

    /**
     * Req 2.3: When poll returns same data, diff is empty —
     * no fade-in animation triggered.
     */
    @Test
    fun `unchanged poll produces empty diff — no fade-in`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onPollSuccess(setOf("X", "Y"), setOf("X-Y"))

        val diff = sm.onPollSuccess(setOf("X", "Y"), setOf("X-Y"))

        assertTrue(diff.newNodeIds.isEmpty(), "No new nodes → no fade-in")
        assertTrue(diff.newEdgeKeys.isEmpty(), "No new edges → no fade-in")
    }

    /**
     * Req 2.3: GraphDiffLogic.computeDiff correctly isolates new elements
     * that should receive the fade-in CSS class.
     */
    @Test
    fun `GraphDiffLogic computes correct fade-in targets`() {
        val diff = GraphDiffLogic.computeDiff(
            oldNodeIds = setOf("A", "B"),
            oldEdgeKeys = setOf("A-B"),
            newNodeIds = setOf("A", "B", "C"),
            newEdgeKeys = setOf("A-B", "B-C")
        )

        assertEquals(setOf("C"), diff.newNodeIds)
        assertEquals(setOf("B-C"), diff.newEdgeKeys)
    }

    // -- Req 3.3: Loading message when graph empty during scan -------------

    /**
     * Req 3.3: Graph empty during active scan — loading message condition.
     * nodeCount == 0 && pollingActive == true → show loading message.
     */
    @Test
    fun `empty graph during scan triggers loading state`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)

        val showLoading = sm.nodeCount == 0 && sm.pollingActive

        assertTrue(showLoading, "Should show loading when graph empty during scan")
    }

    /**
     * Req 3.3: After first successful poll with data, loading message
     * should no longer be shown (nodeCount > 0).
     */
    @Test
    fun `loading state clears after first data arrives`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        assertTrue(sm.nodeCount == 0 && sm.pollingActive, "Loading before data")

        sm.onPollSuccess(setOf("TICKET-1"), setOf())

        val showLoading = sm.nodeCount == 0 && sm.pollingActive
        assertFalse(showLoading, "Loading should clear after data arrives")
        assertEquals(1, sm.nodeCount)
    }

    /**
     * Req 3.3: When polling is not active (scan not started),
     * loading message should NOT be shown even if graph is empty.
     */
    @Test
    fun `no loading message when polling inactive`() {
        val sm = GraphPollingStateMachine()

        val showLoading = sm.nodeCount == 0 && sm.pollingActive

        assertFalse(showLoading, "No loading when polling inactive")
    }

    /**
     * Req 3.3: After scan completes, loading message should NOT show
     * even if graph happens to be empty (edge case: scan with 0 results).
     */
    @Test
    fun `no loading message after scan completes with empty graph`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onScanStatus(ScanStatus.COMPLETED)

        val showLoading = sm.nodeCount == 0 && sm.pollingActive

        assertFalse(showLoading, "No loading after scan completes")
        assertFalse(sm.pollingActive)
    }

    /**
     * Req 3.3: API errors during scan keep loading state active
     * (polling continues, nodeCount stays 0 until successful poll).
     */
    @Test
    fun `loading state persists through API errors`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onPollError()
        sm.onPollError()

        val showLoading = sm.nodeCount == 0 && sm.pollingActive

        assertTrue(showLoading, "Loading persists through errors")
        assertTrue(sm.pollingActive, "Polling still active")
        assertEquals(2, sm.consecutiveErrors)
    }
}
