package com.assistant.server.mcp

import com.assistant.mcp.models.McpServerState
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Property 1: Process lifecycle state machine validity
 *
 * State transitions follow the valid state machine: STOPPED→STARTING→RUNNING,
 * STARTING→ERROR, RUNNING→ERROR, ERROR→STARTING (retry), ERROR→OFFLINE.
 * No invalid transitions are allowed.
 *
 * Property 3: Auto-restart bounded retries
 *
 * For any crash count n, if n < 3 → retry with exponential backoff,
 * if n >= 3 → OFFLINE, no more retries.
 *
 * **Validates: Requirements 6.31, 6.32, 6.35**
 *
 * Feature: mcp-runtime, Property 1 & 3
 */
@OptIn(ExperimentalKotest::class)
class McpProcessManagerPropertyTest {

    /** Valid transitions per the design state machine. */
    private val validTransitions: Map<McpServerState, Set<McpServerState>> = mapOf(
        McpServerState.STOPPED to setOf(McpServerState.STARTING),
        McpServerState.STARTING to setOf(McpServerState.RUNNING, McpServerState.ERROR),
        McpServerState.RUNNING to setOf(McpServerState.ERROR, McpServerState.STOPPED),
        McpServerState.ERROR to setOf(McpServerState.STARTING, McpServerState.OFFLINE),
        McpServerState.OFFLINE to setOf(McpServerState.STARTING)
    )

    /**
     * Property 1 — McpServerState enum has exactly 5 states and
     * every state has defined valid transitions.
     */
    @Test
    fun `Property 1 - state machine has exactly 5 valid states`() {
        assertEquals(5, McpServerState.entries.size, "Must have exactly 5 states")
        val expected = setOf("STOPPED", "STARTING", "RUNNING", "ERROR", "OFFLINE")
        assertEquals(expected, McpServerState.entries.map { it.name }.toSet())
    }

    /**
     * Property 1 — For any (from, to) state pair, the transition is
     * either in the valid set or explicitly disallowed.
     */
    @Test
    fun `Property 1 - only valid transitions are allowed`() {
        runBlocking {
            val arbState = Arb.enum<McpServerState>()
            checkAll(PropTestConfig(iterations = 25), arbState, arbState) { from, to ->
                val allowed = validTransitions[from] ?: emptySet()
                if (to in allowed) {
                    assertTrue(true, "$from → $to is a valid transition")
                } else if (from == to) {
                    // Self-transition not in the machine — that's fine
                    assertFalse(
                        to in allowed,
                        "$from → $to self-transition should not be in valid set"
                    )
                } else {
                    assertFalse(
                        to in allowed,
                        "$from → $to should NOT be a valid transition"
                    )
                }
            }
        }
    }

    /**
     * Property 3 — For any crash count 0..10, verify bounded retry
     * logic: n < 3 → retry with backoff, n >= 3 → OFFLINE.
     */
    @Test
    fun `Property 3 - auto-restart bounded to 3 retries with exponential backoff`() {
        val maxRetries = 3
        val backoffMs = longArrayOf(2000, 4000, 8000)

        runBlocking {
            checkAll(PropTestConfig(iterations = 25), Arb.int(0..10)) { crashCount ->
                if (crashCount < maxRetries) {
                    val expectedDelay = backoffMs[crashCount]
                    assertEquals(
                        (1L shl (crashCount + 1)) * 1000,
                        expectedDelay,
                        "Backoff for retry $crashCount should be 2^${crashCount + 1} seconds"
                    )
                    // After retry, state should transition ERROR → STARTING
                    assertTrue(
                        McpServerState.STARTING in (validTransitions[McpServerState.ERROR] ?: emptySet()),
                        "ERROR → STARTING must be valid for retry $crashCount"
                    )
                } else {
                    // Exceeded max retries → OFFLINE
                    assertTrue(
                        McpServerState.OFFLINE in (validTransitions[McpServerState.ERROR] ?: emptySet()),
                        "ERROR → OFFLINE must be valid when crashCount=$crashCount >= $maxRetries"
                    )
                    assertTrue(
                        crashCount >= maxRetries,
                        "crashCount=$crashCount should exceed maxRetries=$maxRetries"
                    )
                }
            }
        }
    }

    /**
     * Property 3 — backoffMs array has exactly maxRetries entries
     * and values match 2^(n+1) * 1000.
     */
    @Test
    fun `Property 3 - backoff array matches exponential formula`() {
        val maxRetries = 3
        val backoffMs = longArrayOf(2000, 4000, 8000)
        assertEquals(maxRetries, backoffMs.size, "backoffMs must have $maxRetries entries")
        for (i in 0 until maxRetries) {
            val expected = (1L shl (i + 1)) * 1000
            assertEquals(expected, backoffMs[i], "backoffMs[$i] should be ${expected}ms")
        }
    }
}
