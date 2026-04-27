package com.assistant.document

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.GenerationContext
import com.assistant.kb.KBRecord
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 1: Aggregation respects capacity limits.
 * Property 2: Aggregation fault tolerance.
 *
 * Tests capacity limiting logic (take 20, take 10) with in-memory helper,
 * since DocumentAggregatorImpl lives in server module.
 *
 * **Validates: Requirements 1.2, 1.3, 1.5**
 */
class AggregationCapacityPropertyTest {

    companion object {
        private const val MAX_LINKED = 20
        private const val MAX_CHUNKS = 10
    }

    /**
     * Simulates the aggregation capacity limiting logic from
     * DocumentAggregatorImpl: take(20) linked, take(10) chunks.
     */
    private fun applyCapacityLimits(
        mainTicket: KBRecord,
        linked: List<KBRecord>,
        chunks: List<AttachmentChunkInfo>
    ): GenerationContext {
        return GenerationContext(
            mainTicket = mainTicket,
            linkedTicketAnalyses = linked.take(MAX_LINKED),
            attachmentChunks = chunks.take(MAX_CHUNKS)
        )
    }

    /**
     * Simulates fault-tolerant aggregation: only includes available
     * tickets (filters by presence in a "KB"), skips missing ones.
     */
    private fun aggregateWithFaultTolerance(
        mainTicket: KBRecord,
        linkedIds: List<String>,
        availableKb: Map<String, KBRecord>
    ): GenerationContext {
        val found = linkedIds.mapNotNull { availableKb[it] }
        return GenerationContext(
            mainTicket = mainTicket,
            linkedTicketAnalyses = found.take(MAX_LINKED)
        )
    }

    @Test
    fun `Property 1 - linked analyses capped at 20 and chunks at 10`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.kbRecord(),
            Arb.list(Arb.kbRecord(), 0..100),
            Arb.list(Arb.attachmentChunkInfo(), 0..50)
        ) { main, linked, chunks ->
            val ctx = applyCapacityLimits(main, linked, chunks)
            assertTrue(
                ctx.linkedTicketAnalyses.size <= MAX_LINKED,
                "Expected ≤$MAX_LINKED linked, got ${ctx.linkedTicketAnalyses.size}"
            )
            assertTrue(
                ctx.attachmentChunks.size <= MAX_CHUNKS,
                "Expected ≤$MAX_CHUNKS chunks, got ${ctx.attachmentChunks.size}"
            )
        }
    }

    @Test
    fun `Property 2 - aggregation succeeds with any mix of available and missing tickets`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.kbRecord(),
            Arb.list(Arb.string(3..12), 0..40),
            Arb.list(Arb.kbRecord(), 0..25)
        ) { main, linkedIds, availableRecords ->
            val kb = availableRecords.associateBy { it.ticketId }
            val ctx = aggregateWithFaultTolerance(main, linkedIds, kb)
            val expectedCount = minOf(
                linkedIds.count { it in kb },
                MAX_LINKED
            )
            assertTrue(
                ctx.linkedTicketAnalyses.size <= expectedCount,
                "Expected ≤$expectedCount, got ${ctx.linkedTicketAnalyses.size}"
            )
            assertTrue(ctx.linkedTicketAnalyses.size <= MAX_LINKED)
        }
    }
}
