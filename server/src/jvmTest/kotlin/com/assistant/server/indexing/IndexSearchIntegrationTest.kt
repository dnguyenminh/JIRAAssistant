package com.assistant.server.indexing

import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.graph.Bounds
import com.assistant.graph.Cluster
import com.assistant.graph.GraphEngine
import com.assistant.graph.GraphLayout
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
import com.assistant.server.attachment.FakeEmbeddingService
import com.assistant.server.attachment.FakeVectorStore
import com.assistant.server.attachment.models.ChunkType
import com.assistant.server.chat.ChatServiceImpl
import com.assistant.server.chat.CapturingAIAgent
import com.assistant.server.chat.StubKBRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test: IndexingPipeline → VectorStore search →
 * buildKnowledgeContext returns grouped output.
 *
 * Indexes tickets + relationships + analysis into an in-memory
 * VectorStore, then verifies ChatServiceImpl.buildKnowledgeContext
 * returns correctly grouped sections.
 *
 * Requirements: 15.1, 15.2, 15.3
 */
class IndexSearchIntegrationTest {

    private lateinit var embedding: FakeEmbeddingService
    private lateinit var store: FakeVectorStore
    private lateinit var pipeline: IndexingPipeline
    private lateinit var chatService: ChatServiceImpl

    @BeforeEach
    fun setup() {
        embedding = FakeEmbeddingService()
        store = FakeVectorStore()
        pipeline = IndexingPipeline(embedding, store)
        chatService = ChatServiceImpl(
            aiAgentProvider = { CapturingAIAgent() },
            kbRepository = StubKBRepository(),
            graphEngine = IndexSearchStubGraphEngine(),
            embeddingService = embedding,
            vectorStore = store
        )
    }

    @Test
    fun `indexed tickets appear in RELEVANT TICKETS section`() = runTest {
        val tickets = listOf(
            TicketNode("1", "PROJ-1", "Login page", "Open"),
            TicketNode("2", "PROJ-2", "Dashboard", "Open")
        )
        pipeline.indexTickets("PROJ", tickets)

        val ctx = chatService.buildKnowledgeContext("PROJ", "login")
        assertTrue(ctx.contains("--- RELEVANT TICKETS ---"))
        assertTrue(ctx.contains("PROJ-1"))
    }

    @Test
    fun `indexed relationships appear in RELATIONSHIPS section`() =
        runTest {
            val nodes = listOf(
                TicketNode("1", "PROJ-1", "Login", "Open"),
                TicketNode("2", "PROJ-2", "Auth", "Open")
            )
            val edges = listOf(TicketEdge("1", "2", "blocks"))
            val nodeMap = nodes.associateBy { it.id }

            pipeline.indexRelationships("PROJ", edges, nodeMap)

            val ctx = chatService.buildKnowledgeContext("PROJ", "blocks")
            assertTrue(ctx.contains("--- RELATIONSHIPS ---"))
            assertTrue(ctx.contains("PROJ-1 blocks PROJ-2"))
        }

    @Test
    fun `indexed analysis appears in ANALYSIS section`() = runTest {
        val records = listOf(makeKBRecord("PROJ-1"))
        pipeline.indexAnalysisResults("PROJ", records)

        val ctx = chatService.buildKnowledgeContext("PROJ", "estimate")
        assertTrue(ctx.contains("--- ANALYSIS ---"))
        assertTrue(ctx.contains("PROJ-1"))
    }

    @Test
    fun `mixed types produce multiple grouped sections`() = runTest {
        indexAllTypes()

        val ctx = chatService.buildKnowledgeContext("PROJ", "login")
        assertTrue(ctx.contains("--- RELEVANT TICKETS ---"))
        assertTrue(ctx.contains("--- RELATIONSHIPS ---"))
        assertTrue(ctx.contains("--- ANALYSIS ---"))
    }

    @Test
    fun `empty store returns no attachment data fallback`() = runTest {
        val ctx = chatService.buildKnowledgeContext("PROJ", "anything")
        assertEquals("No attachment data.", ctx)
    }

    @Test
    fun `reindex replaces old data with new`() = runTest {
        pipeline.indexTickets(
            "PROJ",
            listOf(TicketNode("1", "PROJ-1", "Old ticket", "Open"))
        )
        assertTrue(store.chunks.any { it.chunkText.contains("Old") })

        val newGraph = NetworkGraph(
            nodes = listOf(
                TicketNode("2", "PROJ-2", "New ticket", "Open")
            ),
            edges = emptyList()
        )
        pipeline.reindexSuspend("PROJ", newGraph, emptyList())

        val ticketTexts = store.chunks
            .filter { it.chunkType == ChunkType.TICKET }
            .map { it.chunkText }
        assertTrue(ticketTexts.all { it.contains("PROJ-2") })
        assertFalse(ticketTexts.any { it.contains("Old") })
    }

    @Test
    fun `buildKnowledgeContext returns empty when embedding null`() =
        runTest {
            embedding.nextEmbedding = null
            pipeline.indexTickets(
                "PROJ",
                listOf(TicketNode("1", "P-1", "T", "Open"))
            )
            val ctx = chatService.buildKnowledgeContext("PROJ", "q")
            assertEquals("", ctx)
        }

    @Test
    fun `top 10 limit respected across mixed types`() = runTest {
        // Index 12 tickets — search should return at most 10
        val tickets = (1..12).map {
            TicketNode("$it", "P-$it", "Ticket $it", "Open")
        }
        pipeline.indexTickets("PROJ", tickets)

        val ctx = chatService.buildKnowledgeContext("PROJ", "ticket")
        val chunkLines = ctx.split("\n").filter { it.startsWith("[") }
        assertTrue(chunkLines.size <= 10)
    }

    // -- Helpers --

    private suspend fun indexAllTypes() {
        val nodes = listOf(
            TicketNode("1", "PROJ-1", "Login", "Open"),
            TicketNode("2", "PROJ-2", "Auth", "Open")
        )
        val edges = listOf(TicketEdge("1", "2", "blocks"))
        val nodeMap = nodes.associateBy { it.id }

        pipeline.indexTickets("PROJ", nodes)
        pipeline.indexRelationships("PROJ", edges, nodeMap)
        pipeline.indexAnalysisResults("PROJ", listOf(makeKBRecord("PROJ-1")))
    }

    private fun makeKBRecord(ticketId: String) = KBRecord(
        ticketId = ticketId,
        requirementSummary = "Implement login",
        evolutionHistory = listOf(
            EvolutionEntry("1", "2024-01-01", "Initial", "CREATED")
        ),
        scrumPoints = 5.0,
        confidenceScore = 0.85,
        rationale = "Complex auth flow",
        similarTicketRefs = emptyList(),
        timestamp = "2024-01-01T00:00:00Z"
    )
}

/** Stub GraphEngine for index-search integration tests. */
private class IndexSearchStubGraphEngine : GraphEngine {
    override fun computeLayout(
        graph: NetworkGraph, width: Double, height: Double
    ) = GraphLayout(emptyMap(), Bounds(width, height))

    override fun detectClusters(graph: NetworkGraph) =
        emptyList<Cluster>()
}
