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
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Preservation Property Tests — Scan progress baseline behavior.
 * These tests PASS on unfixed code and MUST continue passing after fix.
 * **Validates: Requirements 3.1, 3.2, 3.4, 3.5, 3.6**
 */
@OptIn(ExperimentalKotest::class)
class ScanProgressPreservationPropertyTest {

    /**
     * Test 1 — SCANNING/PAUSED progress for low ratios.
     * For processedCount/totalTickets < 99.5%, progressPercent equals
     * ((processedCount.toDouble() / totalTickets) * 100).roundToInt().
     * After fix, roundToInt is used for mathematical rounding accuracy.
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun `progressPercent correct for SCANNING and PAUSED below threshold`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 25),
            arbLowRatioState()
        ) { state ->
            val expected = ((state.processedCount.toDouble() / state.totalTickets) * 100).roundToInt()
            assertEquals(
                expected, state.progressPercent,
                "SCANNING/PAUSED progressPercent should be $expected " +
                    "for ${state.processedCount}/${state.totalTickets} (status=${state.status})"
            )
        }
    }

    /**
     * Test 2 — Zero tickets always yields progressPercent = 0.
     * **Validates: Requirements 3.4**
     */
    @Test
    fun `progressPercent is 0 when totalTickets is 0`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 25),
            Arb.element(ScanStatus.SCANNING, ScanStatus.PAUSED, ScanStatus.IDLE, ScanStatus.CANCELLED)
        ) { status ->
            val state = makeScanState(status, totalTickets = 0, processedCount = 0)
            assertEquals(0, state.progressPercent, "progressPercent should be 0 when totalTickets=0")
        }
    }

    /**
     * Test 3 — Batch completeness: processBatchParallel processes all tickets.
     * **Validates: Requirements 3.5, 3.6**
     */
    @Test
    fun `processBatchParallel processes all tickets in batch`(): Unit = runBlocking {
        checkAll(
            PropTestConfig(iterations = 20),
            arbProjectKey(),
            Arb.int(1..6)
        ) { projectKey, ticketCount ->
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

            val finalState = scanStateRepo.findByProjectKey(projectKey)
                ?: fail("Scan state should exist for $projectKey")
            assertEquals(ScanStatus.COMPLETED, finalState.status)
            assertEquals(
                ticketCount, finalState.totalTickets,
                "totalTickets should match input for $projectKey"
            )
        }
    }

    // --- Generators ---

    /**
     * Generate ScanState with SCANNING or PAUSED status where
     * processedCount/totalTickets < 99.5% (safe zone where toInt == roundToInt).
     */
    private fun arbLowRatioState(): Arb<ScanState> =
        Arb.bind(
            Arb.int(10..2000),
            Arb.element(ScanStatus.SCANNING, ScanStatus.PAUSED)
        ) { total, status ->
            // maxProcessed so that ratio < 99.5% → processed < total * 0.995
            val maxProcessed = ((total * 0.995).toInt() - 1).coerceAtLeast(0)
            maxProcessed to (total to status)
        }.flatMap { (maxProcessed, pair) ->
            val (total, status) = pair
            Arb.int(0..maxProcessed).map { processed ->
                makeScanState(status, total, processed)
            }
        }

    private fun arbProjectKey(): Arb<String> =
        Arb.string(2, 10, Codepoint.alphanumeric()).map { it.uppercase() }

    private fun makeScanState(status: ScanStatus, totalTickets: Int, processedCount: Int) = ScanState(
        projectKey = "TEST",
        status = status,
        totalTickets = totalTickets,
        processedCount = processedCount,
        currentTicketId = null,
        ticketIds = (1..totalTickets).map { "TEST-$it" },
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
