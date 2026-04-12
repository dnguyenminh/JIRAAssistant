package com.assistant.server.indexing

import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.domain.NetworkGraph
import com.assistant.graph.Bounds
import com.assistant.graph.GraphLayout
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import com.assistant.server.attachment.FakeEmbeddingService
import com.assistant.server.attachment.FakeVectorStore
import com.assistant.server.attachment.models.ChunkType
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for IndexingPipeline — batch embedding, error handling, and indexing.
 * Requirements: 12.1, 12.2, 12.4, 13.1, 13.2, 13.4, 16.1, 16.2, 16.3, 16.4
 */
class IndexingPipelineTest {

    private lateinit var embeddingService: FakeEmbeddingService
    private lateinit var vectorStore: FakeVectorStore
    private lateinit var pipeline: IndexingPipeline

    @BeforeTest
    fun setup() {
        embeddingService = FakeEmbeddingService()
        vectorStore = FakeVectorStore()
        pipeline = IndexingPipeline(embeddingService, vectorStore)
    }

    // --- indexTickets ---

    @Test
    fun `indexTickets saves chunks with TICKET chunkType`() = runTest {
        val tickets = listOf(TicketNode("1", "PROJ-1", "Login page", "Open"))
        pipeline.indexTickets("PROJ", tickets)
        assertEquals(1, vectorStore.chunks.size)
        assertEquals(ChunkType.TICKET, vectorStore.chunks[0].chunkType)
        assertEquals("[PROJ-1] Login page", vectorStore.chunks[0].chunkText)
    }

    @Test
    fun `indexTickets skips already indexed tickets`() = runTest {
        vectorStore.existingAttachmentIds.add("ticket:1")
        val tickets = listOf(TicketNode("1", "PROJ-1", "Login page", "Open"))
        pipeline.indexTickets("PROJ", tickets)
        assertEquals(0, vectorStore.chunks.size)
    }

    @Test
    fun `indexTickets skips when embedding fails`() = runTest {
        embeddingService.nextEmbedding = null
        val tickets = listOf(TicketNode("1", "PROJ-1", "Login page", "Open"))
        pipeline.indexTickets("PROJ", tickets)
        assertEquals(0, vectorStore.chunks.size)
    }

    @Test
    fun `indexTickets handles multiple tickets`() = runTest {
        val tickets = listOf(
            TicketNode("1", "PROJ-1", "Login page", "Open"),
            TicketNode("2", "PROJ-2", "Dashboard", "Done")
        )
        pipeline.indexTickets("PROJ", tickets)
        assertEquals(2, vectorStore.chunks.size)
    }

    // --- indexRelationships ---

    @Test
    fun `indexRelationships saves chunks with RELATIONSHIP chunkType`() = runTest {
        val edges = listOf(TicketEdge("1", "2", "blocks"))
        val nodeMap = mapOf(
            "1" to TicketNode("1", "PROJ-1", "Login", "Open"),
            "2" to TicketNode("2", "PROJ-2", "Auth", "Open")
        )
        pipeline.indexRelationships("PROJ", edges, nodeMap)
        assertEquals(1, vectorStore.chunks.size)
        assertEquals(ChunkType.RELATIONSHIP, vectorStore.chunks[0].chunkType)
        assertEquals("PROJ-1 blocks PROJ-2: Login → Auth", vectorStore.chunks[0].chunkText)
    }

    @Test
    fun `indexRelationships skips edges with missing nodes`() = runTest {
        val edges = listOf(TicketEdge("1", "999", "blocks"))
        val nodeMap = mapOf("1" to TicketNode("1", "PROJ-1", "Login", "Open"))
        pipeline.indexRelationships("PROJ", edges, nodeMap)
        assertEquals(0, vectorStore.chunks.size)
    }

    // --- indexClusterSummaries ---

    @Test
    fun `indexClusterSummaries saves chunks with CLUSTER chunkType`() = runTest {
        val nodeMap = mapOf(
            "1" to TicketNode("1", "PROJ-1", "Login", "Open"),
            "2" to TicketNode("2", "PROJ-2", "Auth", "Open")
        )
        val clusters = listOf(Cluster(0, listOf("1", "2"), "#ff0000"))
        pipeline.indexClusterSummaries("PROJ", clusters, nodeMap)
        assertEquals(1, vectorStore.chunks.size)
        assertEquals(ChunkType.CLUSTER, vectorStore.chunks[0].chunkType)
        assertEquals("Cluster 0: contains 2 tickets — PROJ-1, PROJ-2", vectorStore.chunks[0].chunkText)
    }

    @Test
    fun `indexClusterSummaries limits to top 5 keys`() = runTest {
        val nodeMap = (1..7).associate {
            "$it" to TicketNode("$it", "PROJ-$it", "Ticket $it", "Open")
        }
        val clusters = listOf(Cluster(1, (1..7).map { "$it" }, "#00ff00"))
        pipeline.indexClusterSummaries("PROJ", clusters, nodeMap)
        val text = vectorStore.chunks[0].chunkText
        assertTrue(text.contains("PROJ-5"))
        assertFalse(text.contains("PROJ-6"))
    }

    // --- indexAnalysisResults ---

    @Test
    fun `indexAnalysisResults saves ANALYSIS chunk with correct format`() = runTest {
        val records = listOf(makeKBRecord("PROJ-1"))
        pipeline.indexAnalysisResults("PROJ", records)
        val analysisChunks = vectorStore.chunks.filter { it.chunkType == ChunkType.ANALYSIS }
        assertEquals(1, analysisChunks.size)
        assertTrue(analysisChunks[0].chunkText.startsWith("[PROJ-1] Estimate:"))
        assertTrue(analysisChunks[0].chunkText.contains("Rationale:"))
    }

    @Test
    fun `indexAnalysisResults indexes evolution entries with EVOLUTION chunkType`() = runTest {
        val entries = listOf(
            EvolutionEntry("1", "2024-01-01", "Initial estimate", "CREATED"),
            EvolutionEntry("2", "2024-01-15", "Revised scope", "UPDATED")
        )
        val records = listOf(makeKBRecord("PROJ-1", entries))
        pipeline.indexAnalysisResults("PROJ", records)
        val evoChunks = vectorStore.chunks.filter { it.chunkType == ChunkType.EVOLUTION }
        assertEquals(2, evoChunks.size)
        assertTrue(evoChunks[0].chunkText.contains("v1"))
        assertTrue(evoChunks[1].chunkText.contains("[UPDATED]"))
    }

    @Test
    fun `indexAnalysisResults with no evolution creates only ANALYSIS chunk`() = runTest {
        val records = listOf(makeKBRecord("PROJ-1", emptyList()))
        pipeline.indexAnalysisResults("PROJ", records)
        assertEquals(1, vectorStore.chunks.size)
        assertEquals(ChunkType.ANALYSIS, vectorStore.chunks[0].chunkType)
    }

    @Test
    fun `indexAnalysisResults skips when embedding fails`() = runTest {
        embeddingService.nextEmbedding = null
        pipeline.indexAnalysisResults("PROJ", listOf(makeKBRecord("PROJ-1")))
        assertEquals(0, vectorStore.chunks.size)
    }

    @Test
    fun `indexAnalysisResults handles multiple records`() = runTest {
        val records = listOf(makeKBRecord("PROJ-1"), makeKBRecord("PROJ-2"))
        pipeline.indexAnalysisResults("PROJ", records)
        val analysis = vectorStore.chunks.filter { it.chunkType == ChunkType.ANALYSIS }
        assertEquals(2, analysis.size)
    }

    // --- Format helpers (companion object) ---

    @Test
    fun `formatTicketText with null description uses summary only`() {
        val ticket = TicketNode("1", "PROJ-1", "Login page", "Open")
        assertEquals("[PROJ-1] Login page", IndexingPipeline.formatTicketText(ticket))
    }

    @Test
    fun `formatTicketText with blank description uses summary only`() {
        val ticket = TicketNode("1", "PROJ-1", "Login page", "Open")
        assertEquals("[PROJ-1] Login page", IndexingPipeline.formatTicketText(ticket, "  "))
    }

    @Test
    fun `formatTicketText with description includes it`() {
        val ticket = TicketNode("1", "PROJ-1", "Login page", "Open")
        assertEquals("[PROJ-1] Login page. User login flow", IndexingPipeline.formatTicketText(ticket, "User login flow"))
    }

    @Test
    fun `formatRelationshipText produces correct format`() {
        val source = TicketNode("1", "PROJ-1", "Login", "Open")
        val target = TicketNode("2", "PROJ-2", "Auth", "Open")
        val edge = TicketEdge("1", "2", "depends_on")
        assertEquals("PROJ-1 depends_on PROJ-2: Login → Auth", IndexingPipeline.formatRelationshipText(source, edge, target))
    }

    @Test
    fun `formatClusterText produces correct format`() {
        val nodeMap = mapOf(
            "a" to TicketNode("a", "PROJ-1", "Login", "Open"),
            "b" to TicketNode("b", "PROJ-2", "Auth", "Open")
        )
        val cluster = Cluster(3, listOf("a", "b"), "#aabbcc")
        assertEquals("Cluster 3: contains 2 tickets — PROJ-1, PROJ-2", IndexingPipeline.formatClusterText(cluster, nodeMap))
    }

    @Test
    fun `formatAnalysisText produces correct format`() {
        val record = makeKBRecord("PROJ-1")
        assertEquals(
            "[PROJ-1] Estimate: 5.0pts (confidence: 0.85). Implement login. Rationale: Complex auth flow",
            IndexingPipeline.formatAnalysisText(record)
        )
    }

    @Test
    fun `formatEvolutionText produces correct format`() {
        val entry = EvolutionEntry("3", "2024-03-01", "Scope reduced", "REVISED")
        assertEquals("[PROJ-1] v3 (2024-03-01): Scope reduced [REVISED]", IndexingPipeline.formatEvolutionText("PROJ-1", entry))
    }

    // --- reindex (Req 12.5, 16.5) ---

    @Test
    fun `reindexSuspend deletes old chunks before indexing new data`() = runTest {
        // Pre-populate with old data
        pipeline.indexTickets("PROJ", listOf(TicketNode("1", "PROJ-1", "Old ticket", "Open")))
        assertEquals(1, vectorStore.chunks.size)

        // Reindex with new data
        val graph = com.assistant.domain.NetworkGraph(
            nodes = listOf(TicketNode("2", "PROJ-2", "New ticket", "Open")),
            edges = emptyList()
        )
        pipeline.reindexSuspend("PROJ", graph, emptyList())

        // Old chunk deleted, new chunk present
        val ticketChunks = vectorStore.chunks.filter { it.chunkType == ChunkType.TICKET }
        assertEquals(1, ticketChunks.size)
        assertTrue(ticketChunks[0].chunkText.contains("PROJ-2"))
    }

    @Test
    fun `reindexSuspend indexes all four types`() = runTest {
        val nodes = listOf(
            TicketNode("1", "PROJ-1", "Login", "Open"),
            TicketNode("2", "PROJ-2", "Auth", "Open")
        )
        val edges = listOf(TicketEdge("1", "2", "blocks"))
        val graph = com.assistant.domain.NetworkGraph(nodes, edges)
        val records = listOf(makeKBRecord("PROJ-1", emptyList()))

        val graphEngine = FakeGraphEngineForReindex(
            listOf(Cluster(0, listOf("1", "2"), "#ff0000"))
        )
        val pipelineWithEngine = IndexingPipeline(embeddingService, vectorStore, graphEngine)
        pipelineWithEngine.reindexSuspend("PROJ", graph, records)

        val types = vectorStore.chunks.map { it.chunkType }.toSet()
        assertTrue(ChunkType.TICKET in types)
        assertTrue(ChunkType.RELATIONSHIP in types)
        assertTrue(ChunkType.CLUSTER in types)
        assertTrue(ChunkType.ANALYSIS in types)
    }

    @Test
    fun `reindexSuspend without graphEngine skips clusters`() = runTest {
        val graph = com.assistant.domain.NetworkGraph(
            nodes = listOf(TicketNode("1", "PROJ-1", "Login", "Open")),
            edges = emptyList()
        )
        pipeline.reindexSuspend("PROJ", graph, emptyList())

        val clusterChunks = vectorStore.chunks.filter { it.chunkType == ChunkType.CLUSTER }
        assertEquals(0, clusterChunks.size)
    }

    @Test
    fun `reindexSuspend deletes all chunk types`() = runTest {
        // Pre-populate with ticket and analysis chunks (ticketId starts with "PROJ")
        pipeline.indexTickets("PROJ", listOf(TicketNode("1", "PROJ-1", "T1", "Open")))
        pipeline.indexAnalysisResults("PROJ", listOf(makeKBRecord("PROJ-1")))
        val beforeCount = vectorStore.chunks.size
        assertTrue(beforeCount >= 2)

        // Reindex with different data
        val graph = com.assistant.domain.NetworkGraph(
            nodes = listOf(TicketNode("3", "PROJ-3", "New", "Open")),
            edges = emptyList()
        )
        pipeline.reindexSuspend("PROJ", graph, emptyList())

        // Only the new ticket chunk should remain
        val ticketChunks = vectorStore.chunks.filter { it.chunkType == ChunkType.TICKET }
        assertEquals(1, ticketChunks.size)
        assertTrue(ticketChunks[0].chunkText.contains("PROJ-3"))
        // No analysis or evolution chunks from old data
        val analysisChunks = vectorStore.chunks.filter { it.chunkType == ChunkType.ANALYSIS }
        assertEquals(0, analysisChunks.size)
    }

    // --- Batch embedding and error handling (Req 16.1–16.4) ---

    @Test
    fun `null embeddingService skips indexing without throwing`() = runTest {
        val nullPipeline = IndexingPipeline(null, vectorStore)
        nullPipeline.indexTickets("PROJ", listOf(TicketNode("1", "PROJ-1", "Login", "Open")))
        assertEquals(0, vectorStore.chunks.size)
    }

    @Test
    fun `embed exception is caught and item retried once`() = runTest {
        embeddingService.failOnCallNumber = 1 // first call throws, retry succeeds
        val tickets = listOf(TicketNode("1", "PROJ-1", "Login", "Open"))
        pipeline.indexTickets("PROJ", tickets)
        // First call fails, retry succeeds → 1 chunk saved
        assertEquals(1, vectorStore.chunks.size)
    }

    @Test
    fun `batches items in groups of 20`() = runTest {
        val tickets = (1..25).map { TicketNode("$it", "P-$it", "T$it", "Open") }
        pipeline.indexTickets("PROJ", tickets)
        // All 25 should be embedded (2 batches: 20 + 5)
        assertEquals(25, vectorStore.chunks.size)
        assertEquals(25, embeddingService.embedCallCount)
    }

    // --- Helper ---

    private fun makeKBRecord(
        ticketId: String,
        evolution: List<EvolutionEntry> = listOf(
            EvolutionEntry("1", "2024-01-01", "Initial", "CREATED")
        )
    ) = KBRecord(
        ticketId = ticketId,
        requirementSummary = "Implement login",
        evolutionHistory = evolution,
        scrumPoints = 5.0,
        confidenceScore = 0.85,
        rationale = "Complex auth flow",
        similarTicketRefs = emptyList(),
        timestamp = "2024-01-01T00:00:00Z"
    )
}

/** Fake GraphEngine that returns pre-configured clusters. */
private class FakeGraphEngineForReindex(
    private val clusters: List<Cluster> = emptyList()
) : GraphEngine {
    override fun computeLayout(graph: NetworkGraph, width: Double, height: Double) =
        GraphLayout(emptyMap(), Bounds(width, height))
    override fun detectClusters(graph: NetworkGraph) = clusters
}
