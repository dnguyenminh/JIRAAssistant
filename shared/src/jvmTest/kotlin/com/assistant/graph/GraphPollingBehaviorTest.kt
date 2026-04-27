package com.assistant.graph

import com.assistant.scan.ScanStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for graph polling behavior state machine.
 * Tests the pure polling logic extracted from frontend GraphScanStatus.
 * Requirements: 2.1, 2.4, 2.5, 3.4
 */
class GraphPollingBehaviorTest {

    /**
     * Req 2.1: Graph polling starts when scan status is SCANNING.
     */
    @Test
    fun `polling starts when scan status is SCANNING`() {
        val sm = GraphPollingStateMachine()
        assertFalse(sm.pollingActive, "Polling should be inactive initially")

        val started = sm.onScanStatus(ScanStatus.SCANNING)

        assertTrue(started, "onScanStatus(SCANNING) should return true")
        assertTrue(sm.pollingActive, "Polling should be active after SCANNING")
    }

    @Test
    fun `polling does not start for non-SCANNING statuses`() {
        for (status in listOf(ScanStatus.IDLE, ScanStatus.COMPLETED, ScanStatus.CANCELLED)) {
            val sm = GraphPollingStateMachine()
            val started = sm.onScanStatus(status)
            assertFalse(started, "Polling should not start for $status")
            assertFalse(sm.pollingActive, "Polling should remain inactive for $status")
        }
    }

    @Test
    fun `duplicate SCANNING does not restart polling`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        val secondStart = sm.onScanStatus(ScanStatus.SCANNING)
        assertFalse(secondStart, "Second SCANNING should not re-trigger start")
        assertTrue(sm.pollingActive, "Polling should still be active")
    }

    /**
     * Req 2.4: Node count updated after successful poll.
     */
    @Test
    fun `node count updated after successful poll`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        assertEquals(0, sm.nodeCount, "Initial node count should be 0")

        sm.onPollSuccess(setOf("A", "B", "C"), setOf("A-B"))
        assertEquals(3, sm.nodeCount, "Node count should be 3 after first poll")

        sm.onPollSuccess(setOf("A", "B", "C", "D", "E"), setOf("A-B", "C-D"))
        assertEquals(5, sm.nodeCount, "Node count should be 5 after second poll")
    }

    @Test
    fun `diff correctly identifies new elements across polls`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)

        val diff1 = sm.onPollSuccess(setOf("A", "B"), setOf("A-B"))
        assertEquals(setOf("A", "B"), diff1.newNodeIds, "First poll: all nodes are new")
        assertEquals(setOf("A-B"), diff1.newEdgeKeys, "First poll: all edges are new")

        val diff2 = sm.onPollSuccess(setOf("A", "B", "C"), setOf("A-B", "B-C"))
        assertEquals(setOf("C"), diff2.newNodeIds, "Second poll: only C is new")
        assertEquals(setOf("B-C"), diff2.newEdgeKeys, "Second poll: only B-C is new")
    }

    @Test
    fun `no new elements when poll returns same data`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onPollSuccess(setOf("A", "B"), setOf("A-B"))

        val diff = sm.onPollSuccess(setOf("A", "B"), setOf("A-B"))
        assertTrue(diff.newNodeIds.isEmpty(), "No new nodes when data unchanged")
        assertTrue(diff.newEdgeKeys.isEmpty(), "No new edges when data unchanged")
    }

    /**
     * Req 2.5: Polling stops and final load on COMPLETED.
     */
    @Test
    fun `polling stops on COMPLETED with final load triggered`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        assertTrue(sm.pollingActive)

        sm.onScanStatus(ScanStatus.COMPLETED)

        assertFalse(sm.pollingActive, "Polling should stop on COMPLETED")
        assertTrue(sm.finalLoadTriggered, "Final load should be triggered on COMPLETED")
    }

    @Test
    fun `polling stops on CANCELLED`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onScanStatus(ScanStatus.CANCELLED)

        assertFalse(sm.pollingActive, "Polling should stop on CANCELLED")
        assertTrue(sm.finalLoadTriggered, "Final load should be triggered on CANCELLED")
    }

    @Test
    fun `node count preserved after polling stops`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onPollSuccess(setOf("A", "B", "C"), setOf("A-B"))
        sm.onScanStatus(ScanStatus.COMPLETED)

        assertEquals(3, sm.nodeCount, "Node count should be preserved after stop")
    }

    /**
     * Req 3.4: API error during poll → retry on next interval.
     */
    @Test
    fun `API error increments error count but keeps polling active`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)

        sm.onPollError()

        assertTrue(sm.pollingActive, "Polling should remain active after error")
        assertEquals(1, sm.consecutiveErrors, "Error count should be 1")
    }

    @Test
    fun `successful poll resets error count`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onPollError()
        sm.onPollError()
        assertEquals(2, sm.consecutiveErrors)

        sm.onPollSuccess(setOf("A"), emptySet())

        assertEquals(0, sm.consecutiveErrors, "Error count should reset on success")
    }

    @Test
    fun `multiple errors do not stop polling`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)

        repeat(5) { sm.onPollError() }

        assertTrue(sm.pollingActive, "Polling should remain active after 5 errors")
        assertEquals(5, sm.consecutiveErrors)
    }

    @Test
    fun `poll success after errors updates node count normally`() {
        val sm = GraphPollingStateMachine()
        sm.onScanStatus(ScanStatus.SCANNING)
        sm.onPollError()
        sm.onPollError()

        sm.onPollSuccess(setOf("X", "Y"), setOf("X-Y"))

        assertEquals(2, sm.nodeCount, "Node count should update after recovery")
        assertEquals(0, sm.consecutiveErrors, "Errors should be reset")
    }
}
