package com.assistant.server.analysis

import com.assistant.server.analysis.models.MapReduceConfig
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 4: Batch Partition — Conservation and Invariants.
 * Property 5: Batch Depth Grouping — Tickets cùng depth ưu tiên cùng batch.
 *
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.5, 2.6**
 */
@OptIn(ExperimentalKotest::class)
class BatchPartitionPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    /**
     * **Property 4: Conservation and Invariants**
     * **Validates: Requirements 2.1, 2.3, 2.5, 2.6**
     *
     * For any TicketGraph with N nodes and maxBatchSize M (5..100):
     * (1) total tickets across batches = N
     * (2) no duplicate ticket IDs across batches
     * (3) no empty batches
     * (4) each batch ≤ M tickets
     * (5) root ticket in batch 0
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 4: Conservation")
    fun `partition conserves all tickets with no duplicates`() {
        runBlocking {
            checkAll(cfg, Arb.arbTicketGraph(), Arb.int(5..100)) { graph, maxBatch ->
                val config = MapReduceConfig(maxBatchSize = maxBatch).validated()
                val strategy = BatchStrategy(config)
                val batches = strategy.partition(graph)
                val allIds = batches.flatMap { it.ticketIds }
                val uniqueIds = allIds.toSet()

                // (1) total tickets = N
                assertEquals(graph.nodes.size, allIds.size) {
                    "Expected ${graph.nodes.size} tickets, got ${allIds.size}"
                }
                // (2) no duplicates
                assertEquals(allIds.size, uniqueIds.size) {
                    "Duplicate IDs found: ${allIds.groupingBy { it }.eachCount().filter { it.value > 1 }}"
                }
                // (3) no empty batches
                assertTrue(batches.all { it.tickets.isNotEmpty() }) {
                    "Empty batch found at indices: ${batches.filter { it.tickets.isEmpty() }.map { it.batchIndex }}"
                }
                // (4) each batch ≤ maxBatchSize
                assertTrue(batches.all { it.tickets.size <= config.maxBatchSize }) {
                    "Oversized batch: ${batches.filter { it.tickets.size > config.maxBatchSize }.map { "batch ${it.batchIndex}: ${it.tickets.size}" }}"
                }
                // (5) root ticket in batch 0
                if (batches.isNotEmpty()) {
                    assertTrue(graph.rootTicketId in batches[0].ticketIds) {
                        "Root ticket ${graph.rootTicketId} not in batch 0. Batch 0 IDs: ${batches[0].ticketIds}"
                    }
                }
            }
        }
    }

    /**
     * **Property 5: Batch Depth Grouping**
     * **Validates: Requirements 2.2**
     *
     * For any batch, `max(depthLevels) - min(depthLevels) ≤ 1`
     * or batch contains overflow from adjacent depth level.
     *
     * Batch 0 is excluded because it intentionally mixes root (depth 0)
     * with highest-relevance depth-1 tickets by design (Req 2.3).
     */
    @Test
    @Tag("Feature: map-reduce-analysis, Property 5: Depth Grouping")
    fun `batches group tickets by adjacent depth levels`() {
        runBlocking {
            checkAll(cfg, Arb.arbTicketGraph(), Arb.int(5..100)) { graph, maxBatch ->
                val config = MapReduceConfig(maxBatchSize = maxBatch).validated()
                val strategy = BatchStrategy(config)
                val batches = strategy.partition(graph)

                // Skip batch 0 — it mixes root + depth-1 by design
                for (batch in batches.drop(1)) {
                    if (batch.depthLevels.isEmpty()) continue
                    val minDepth = batch.depthLevels.min()
                    val maxDepth = batch.depthLevels.max()
                    val depthSpan = maxDepth - minDepth
                    assertTrue(depthSpan <= 1) {
                        "Batch ${batch.batchIndex} spans depths $minDepth..$maxDepth (span=$depthSpan > 1). " +
                            "Tickets: ${batch.tickets.map { "${it.ticketId}@d${it.depth}" }}"
                    }
                }
            }
        }
    }
}
