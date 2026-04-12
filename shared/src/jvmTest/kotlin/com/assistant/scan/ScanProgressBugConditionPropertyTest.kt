package com.assistant.scan

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Bug Condition Exploration — Scan progress race condition & truncation.
 * EXPECTED: Tests 1 & 2 FAIL on unfixed code (confirms bugs exist).
 * Test 3 is non-deterministic (race condition).
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
 */
@OptIn(ExperimentalKotest::class)
class ScanProgressBugConditionPropertyTest {

    /**
     * Test 1 — Truncation in progressPercent.
     * Generate ScanState where processedCount/totalTickets >= 99.5% but < 100%.
     * Assert progressPercent == 100.
     * FAILS on unfixed code: .toInt() truncates 99.5+ to 99.
     * **Validates: Requirements 1.3**
     */
    @Test
    fun `progressPercent rounds up near 100 percent`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 25), arbTruncationState()) { state ->
            val ratio = state.processedCount.toDouble() / state.totalTickets * 100
            assert(ratio >= 99.5 && ratio < 100.0) {
                "Generator invariant: ratio=$ratio should be in [99.5, 100)"
            }
            assertEquals(
                100, state.progressPercent,
                "progressPercent should be 100 for ratio $ratio " +
                    "(${state.processedCount}/${state.totalTickets}) but got ${state.progressPercent}"
            )
        }
    }

    /**
     * Test 2 — COMPLETED force 100%.
     * Generate ScanState with status=COMPLETED and processedCount < totalTickets.
     * Assert progressPercent == 100.
     * FAILS on unfixed code: no special handling for COMPLETED status.
     * **Validates: Requirements 1.4**
     */
    @Test
    fun `progressPercent is 100 when status COMPLETED`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 25), arbCompletedState()) { state ->
            assertEquals(ScanStatus.COMPLETED, state.status)
            assert(state.processedCount < state.totalTickets) {
                "Generator invariant: processedCount < totalTickets"
            }
            assertEquals(
                100, state.progressPercent,
                "COMPLETED scan should show 100% but got ${state.progressPercent} " +
                    "(${state.processedCount}/${state.totalTickets})"
            )
        }
    }

    /**
     * Test 3 — Race condition (non-deterministic).
     * Run full scan with batch processing, verify processedCount == totalTickets.
     * May or may not fail depending on coroutine scheduling.
     * **Validates: Requirements 1.1, 1.2**
     */
    @Test
    fun `processedCount equals totalTickets after scan completes`(): Unit = runBlocking {
        checkAll(PropTestConfig(iterations = 10), arbProjectKey(), Arb.int(2..8)) { projectKey, ticketCount ->
            val scanStateRepo = CompleteScanBugConditionPropertyTest.InMemoryScanStateRepo()
            val scanLogRepo = CompleteScanBugConditionPropertyTest.InMemoryScanLogRepo()
            val kbRepo = CompleteScanBugConditionPropertyTest.TrackingKBRepository()
            val ticketKeys = (1..ticketCount).map { "$projectKey-$it" }

            val engine = BatchScanEngine(
                aiOrchestrator = CompleteScanBugConditionPropertyTest.StubAIOrchestratorForScan(),
                kbRepository = kbRepo,
                jiraClientProvider = { CompleteScanBugConditionPropertyTest.StubJiraClientForScan(ticketKeys) },
                featureNetworkMapper = com.assistant.domain.FeatureNetworkMapper(
                    CompleteScanBugConditionPropertyTest.StubAIAgentForScan()
                ),
                scanStateRepository = scanStateRepo,
                scanLogRepository = scanLogRepo,
                scope = CoroutineScope(Dispatchers.Default)
            )
            engine.startScan(projectKey)
            awaitScanComplete(scanStateRepo, projectKey)

            val state = scanStateRepo.findByProjectKey(projectKey)
                ?: fail("Scan state should exist for $projectKey")
            assertEquals(ScanStatus.COMPLETED, state.status)
            assertEquals(
                ticketCount, state.processedCount,
                "processedCount should equal totalTickets ($ticketCount) " +
                    "but got ${state.processedCount} for $projectKey"
            )
        }
    }

    // --- Generators ---

    /** Generate ScanState where processedCount/totalTickets ratio is in [99.5%, 100%). */
    private fun arbTruncationState(): Arb<ScanState> =
        Arb.int(200..2000).flatMap { total ->
            // Find the minimum processedCount where ratio >= 99.5%
            val minProcessed = kotlin.math.ceil(total * 0.995).toInt().coerceAtMost(total - 1)
            Arb.int(minProcessed until total).map { processed ->
                makeScanState(ScanStatus.SCANNING, total, processed)
            }
        }

    /** Generate ScanState with COMPLETED status and processedCount < totalTickets. */
    private fun arbCompletedState(): Arb<ScanState> =
        Arb.int(10..2000).flatMap { total ->
            Arb.int(1 until total).map { processed ->
                makeScanState(ScanStatus.COMPLETED, total, processed)
            }
        }

    private fun arbProjectKey(): Arb<String> =
        Arb.string(2, 10, Codepoint.alphanumeric()).map { it.uppercase() }

    private fun makeScanState(status: ScanStatus, total: Int, processed: Int) = ScanState(
        projectKey = "TEST",
        status = status,
        totalTickets = total,
        processedCount = processed,
        currentTicketId = null,
        ticketIds = (1..total).map { "TEST-$it" },
        startedAt = "2024-01-01T00:00:00Z",
        updatedAt = "2024-01-01T00:00:00Z"
    )

    private suspend fun awaitScanComplete(
        repo: CompleteScanBugConditionPropertyTest.InMemoryScanStateRepo,
        projectKey: String
    ) {
        repeat(200) {
            if (repo.findByProjectKey(projectKey)?.status == ScanStatus.COMPLETED) return
            delay(50)
        }
        fail("Scan did not complete within timeout for $projectKey")
    }
}
