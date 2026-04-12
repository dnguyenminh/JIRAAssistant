package com.assistant.server.indexing

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketNode
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.graph.Bounds
import com.assistant.graph.GraphLayout
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import com.assistant.server.attachment.FakeEmbeddingService
import com.assistant.server.attachment.VectorStore
import com.assistant.server.attachment.models.AttachmentChunk
import com.assistant.server.attachment.models.ChunkType
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for IndexingPipeline idempotency and reindex.
 *
 * Feature: graph-filter-focus-mode
 * Property 7: Indexing Idempotency và Reindex Correctness
 * **Validates: Requirements 12.4, 12.5, 16.5**
 */
@OptIn(ExperimentalKotest::class)
class IndexingIdempotencyPropertyTest {

    // ── Generators ──────────────────────────────────────────────────

    private fun genTicketNode(): Arb<TicketNode> = arbitrary {
        val id = Arb.int(1..9999).bind().toString()
        TicketNode(
            id = id,
            key = "PROJ-${Arb.int(1..9999).bind()}",
            summary = Arb.string(3..30, Codepoint.alphanumeric()).bind(),
            status = Arb.element("Open", "In Progress", "Done").bind()
        )
    }

    private fun genTicketList(): Arb<List<TicketNode>> =
        Arb.list(genTicketNode(), 1..10).map { tickets ->
            tickets.distinctBy { it.id }
        }

    private fun genKBRecord(ticketId: String): KBRecord = KBRecord(
        ticketId = ticketId,
        requirementSummary = "Summary for $ticketId",
        evolutionHistory = listOf(
            EvolutionEntry("1", "2024-01-01", "Initial", "CREATED")
        ),
        scrumPoints = 5.0,
        confidenceScore = 0.8,
        rationale = "Rationale for $ticketId",
        similarTicketRefs = emptyList(),
        timestamp = "2024-01-01T00:00:00Z"
    )

    // ── Property 7 Part 1: Idempotency ─────────────────────────────

    @Test
    fun `Property 7a - indexing same tickets twice does not create duplicates`() =
        runTest {
            checkAll(PropTestConfig(iterations = 25), genTicketList()) { tickets ->
                val store = TrackingVectorStore()
                val pipeline = IndexingPipeline(
                    FakeEmbeddingService(), store
                )

                pipeline.indexTickets("PROJ", tickets)
                val countAfterFirst = store.chunks.size

                pipeline.indexTickets("PROJ", tickets)
                val countAfterSecond = store.chunks.size

                assertEquals(
                    countAfterFirst, countAfterSecond,
                    "Indexing ${tickets.size} tickets twice should not " +
                        "create duplicates: first=$countAfterFirst, " +
                        "second=$countAfterSecond"
                )
            }
        }

    // ── Property 7 Part 2: Reindex Correctness ─────────────────────

    @Test
    fun `Property 7b - reindex replaces old data with new data only`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 25),
                genTicketList(), genTicketList()
            ) { oldTickets, newTickets ->
                val store = TrackingVectorStore()
                val engine = StubGraphEngine()
                val pipeline = IndexingPipeline(
                    FakeEmbeddingService(), store, engine
                )

                // Index old data
                val oldGraph = NetworkGraph(oldTickets, emptyList())
                pipeline.reindexSuspend("PROJ", oldGraph, emptyList())
                val oldKeys = oldTickets.map { it.key }.toSet()

                // Reindex with new data
                val newGraph = NetworkGraph(newTickets, emptyList())
                pipeline.reindexSuspend("PROJ", newGraph, emptyList())

                val newKeys = newTickets.map { it.key }.toSet()
                val remaining = store.chunks.map { it.ticketId }.toSet()

                // All remaining chunks should belong to new data
                for (ticketId in remaining) {
                    assertTrue(
                        ticketId in newKeys,
                        "Chunk ticketId=$ticketId should be in " +
                            "new set $newKeys, not stale"
                    )
                }

                // Every new ticket should be indexed
                val ticketChunks = store.chunks
                    .filter { it.chunkType == ChunkType.TICKET }
                    .map { it.ticketId }.toSet()
                assertEquals(
                    newKeys, ticketChunks,
                    "All new tickets should be indexed"
                )
            }
        }
}

/**
 * VectorStore that auto-tracks saved attachmentIds for
 * realistic existsByAttachmentId behavior.
 * Uses composition since FakeVectorStore is final.
 */
private class TrackingVectorStore : VectorStore {
    val chunks = mutableListOf<AttachmentChunk>()
    private val savedAttachmentIds = mutableSetOf<String>()

    override suspend fun saveChunk(chunk: AttachmentChunk): Boolean {
        chunks.add(chunk)
        if (chunk.chunkType == ChunkType.TICKET) {
            savedAttachmentIds.add("ticket:${chunk.attachmentId}")
        }
        return true
    }

    override suspend fun existsByAttachmentId(attachmentId: String) =
        attachmentId in savedAttachmentIds

    override suspend fun search(
        queryEmbedding: FloatArray, topK: Int
    ) = emptyList<AttachmentChunk>()

    override suspend fun search(
        queryEmbedding: FloatArray, topK: Int, chunkType: String?
    ) = emptyList<AttachmentChunk>()

    override suspend fun deleteByTicketId(ticketId: String): Boolean {
        chunks.removeAll { it.ticketId == ticketId }
        return true
    }

    override suspend fun deleteByProjectKey(
        projectKey: String, chunkType: String?
    ): Boolean {
        chunks.removeAll { chunk ->
            chunk.ticketId.startsWith(projectKey) &&
                (chunkType == null || chunk.chunkType == chunkType)
        }
        savedAttachmentIds.clear()
        return true
    }

    override suspend fun findByTicketId(ticketId: String) =
        chunks.filter { it.ticketId == ticketId }
}

/** Stub GraphEngine returning empty clusters. */
private class StubGraphEngine : GraphEngine {
    override fun computeLayout(
        graph: NetworkGraph, width: Double, height: Double
    ) = GraphLayout(emptyMap(), Bounds(width, height))

    override fun detectClusters(graph: NetworkGraph) =
        emptyList<Cluster>()
}
