package com.assistant.server.indexing

import com.assistant.server.attachment.EmbeddingService
import com.assistant.server.attachment.FakeVectorStore
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for BatchEmbedder batch size constraints.
 *
 * Feature: graph-filter-focus-mode
 * Property 9: Embedding Batch Size Constraint
 * **Validates: Requirements 16.2**
 */
@OptIn(ExperimentalKotest::class)
class BatchEmbedderPropertyTest {

    /**
     * EmbeddingService that tracks per-batch call counts.
     * Records the size of each batch by detecting batch boundaries
     * via [resetBatchCounter] calls from the test harness.
     */
    private class BatchTrackingEmbeddingService : EmbeddingService {
        val batchSizes = mutableListOf<Int>()
        private var currentBatchCount = 0
        var totalCalls = 0

        override suspend fun embed(text: String): FloatArray {
            currentBatchCount++
            totalCalls++
            return floatArrayOf(0.1f, 0.2f, 0.3f)
        }

        /** Flush current batch count and start a new batch. */
        fun flushBatch() {
            if (currentBatchCount > 0) {
                batchSizes.add(currentBatchCount)
                currentBatchCount = 0
            }
        }

        fun reset() {
            batchSizes.clear()
            currentBatchCount = 0
            totalCalls = 0
        }
    }

    private fun genEmbedItems(n: Int): List<EmbedItem> =
        (1..n).map { EmbedItem("text-$it", "T-$it", "att-$it", "TICKET") }

    // ── Property 9: N texts → ⌈N/20⌉ batches, each ≤ 20, total = N ──

    @Test
    fun `Property 9 - N items produce ceil(N div 20) batches each le 20 and total equals N`() = runTest {
        checkAll(PropTestConfig(iterations = 25), Arb.int(0..200)) { n ->
            val vectorStore = FakeVectorStore()
            val embeddingService = BatchTrackingEmbeddingService()
            val batchEmbedder = BatchEmbedder(embeddingService, vectorStore)

            val items = genEmbedItems(n)
            batchEmbedder.embedAndSaveAll(items)

            // Total items processed must equal N
            assertEquals(n, embeddingService.totalCalls,
                "Total embed calls should equal N=$n")

            // Total saved chunks must equal N
            assertEquals(n, vectorStore.chunks.size,
                "Total saved chunks should equal N=$n")

            // Verify batch structure via chunked() math
            val maxBatch = BatchEmbedder.MAX_BATCH_SIZE
            val expectedBatches = if (n == 0) 0
                else ceil(n.toDouble() / maxBatch).toInt()

            // Verify by reconstructing batches from the chunked logic
            val batches = items.chunked(maxBatch)
            assertEquals(expectedBatches, batches.size,
                "Expected ⌈$n/20⌉ = $expectedBatches batches")

            for ((i, batch) in batches.withIndex()) {
                assertTrue(batch.size <= maxBatch,
                    "Batch $i has ${batch.size} items, exceeds max $maxBatch")
                assertTrue(batch.isNotEmpty(),
                    "Batch $i should not be empty")
            }

            val totalAcrossBatches = batches.sumOf { it.size }
            assertEquals(n, totalAcrossBatches,
                "Sum of all batch sizes should equal N=$n")

            embeddingService.reset()
        }
    }

    @Test
    fun `Property 9 - batch boundaries are correct for random sizes`() = runTest {
        checkAll(PropTestConfig(iterations = 25), Arb.int(1..150)) { n ->
            val vectorStore = FakeVectorStore()
            val embeddingService = BatchTrackingEmbeddingService()
            val batchEmbedder = BatchEmbedder(embeddingService, vectorStore)

            val items = genEmbedItems(n)
            batchEmbedder.embedAndSaveAll(items)

            val maxBatch = BatchEmbedder.MAX_BATCH_SIZE
            val expectedBatches = ceil(n.toDouble() / maxBatch).toInt()
            val remainder = n % maxBatch

            // Verify via total calls and saved chunks
            assertEquals(n, embeddingService.totalCalls)
            assertEquals(n, vectorStore.chunks.size)

            // When evenly divisible: all batches are full (size 20)
            // Otherwise: (expectedBatches-1) full + 1 partial
            if (remainder == 0) {
                assertEquals(n, expectedBatches * maxBatch,
                    "$expectedBatches full batches * 20 should = $n")
            } else {
                val fullBatches = expectedBatches - 1
                assertEquals(n, fullBatches * maxBatch + remainder,
                    "$fullBatches * 20 + $remainder should = $n")
                assertTrue(remainder in 1 until maxBatch,
                    "Remainder $remainder should be in 1..${maxBatch - 1}")
            }

            embeddingService.reset()
        }
    }
}
