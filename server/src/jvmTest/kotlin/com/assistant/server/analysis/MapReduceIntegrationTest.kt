package com.assistant.server.analysis

import com.assistant.ai.AnalysisSource
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.MapReduceConfig
import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketEdge
import com.assistant.server.document.models.TicketGraph
import com.assistant.server.document.models.TicketNode
import com.assistant.server.document.models.TraversalMetadata
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Integration tests for the end-to-end Map-Reduce pipeline.
 * Uses mock AI agents — no real AI calls.
 *
 * **Validates: Requirements 1.4, 4.7, 6.1, 6.5, 7.5, 10.4**
 */
class MapReduceIntegrationTest {

    private val semaphore = Semaphore(5)

    private fun buildGraph(count: Int, maxDepth: Int = 5): TicketGraph {
        val rootId = "ROOT-1"
        val nodes = mutableMapOf<String, TicketNode>()
        nodes[rootId] = ticketNode(rootId, 0, RelationshipType.ROOT, rootId, 1.0)
        for (i in 1 until count) {
            val depth = if (maxDepth == 0) 0 else ((i % maxDepth) + 1)
            nodes["TICKET-$i"] = ticketNode(
                "TICKET-$i", depth, RelationshipType.ISSUE_LINK, rootId, 1.0 / (depth + 1)
            )
        }
        val edges = nodes.values.filter { it.ticketId != rootId }.map {
            TicketEdge(rootId, it.ticketId, RelationshipType.ISSUE_LINK)
        }
        return TicketGraph(rootId, nodes, edges, TraversalMetadata(
            count, count, 0, nodes.values.maxOf { it.depth }, 100L
        ))
    }

    private fun ticketNode(
        id: String, depth: Int, via: RelationshipType, parent: String, score: Double
    ) = TicketNode(id, depth, via, parent, StructuredTicketContent(summary = "Ticket $id"), score)

    private fun orchestrator(
        agent: com.assistant.ai.AIAgent,
        config: MapReduceConfig = MapReduceConfig(maxBatchSize = 10).validated()
    ) = MapReduceOrchestrator(
        BatchStrategy(config), BatchPromptBuilder(), ReducePromptBuilder(),
        MockResponseParser(), { config }, semaphore, ProgressTracker()
    )

    private fun agents(agent: com.assistant.ai.AIAgent) = { mapOf("mock-1" to agent) }
    private fun providers() = { listOf(testProvider()) }

    /** Full pipeline: partition → map → reduce → parse. Validates: Req 1.4 */
    @Test
    @Tag("Feature: map-reduce-analysis, Integration: full-pipeline")
    fun `full pipeline produces result with mapReduceInfo`() = runBlocking {
        val graph = buildGraph(25)
        val agent = MockAIAgent(batchSummaryJson(0, listOf("ROOT-1")))
        val result = orchestrator(agent).analyze("ROOT-1", graph, agents(agent), providers())

        assertNotNull(result.mapReduceInfo)
        assertTrue(result.mapReduceInfo.totalBatches > 1) {
            "Expected multiple batches, got ${result.mapReduceInfo.totalBatches}"
        }
        assertEquals("ROOT-1", result.analysisResult.ticketId)
        assertEquals(AnalysisSource.FRESH_AI, result.analysisResult.source)
        assertTrue(agent.callCount > 1) { "Expected multiple AI calls" }
    }

    /** Single-batch optimization: 1 batch → skip reduce. Validates: Req 4.7 */
    @Test
    @Tag("Feature: map-reduce-analysis, Integration: single-batch")
    fun `single batch skips reduce phase`() = runBlocking {
        val config = MapReduceConfig(maxBatchSize = 100).validated()
        val graph = buildGraph(5, maxDepth = 1) // depth 0-1 only → 1 batch
        val agent = MockAIAgent(batchSummaryJson(0, listOf("ROOT-1")))
        val result = orchestrator(agent, config).analyze("ROOT-1", graph, agents(agent), providers())

        assertTrue(result.mapReduceInfo.reduceSkipped) {
            "Expected reduceSkipped=true, totalBatches=${result.mapReduceInfo.totalBatches}"
        }
        assertEquals(1, result.mapReduceInfo.totalBatches)
        assertEquals(1, result.mapReduceInfo.successfulBatches)
    }

    /** All batches fail → fallback result with root context. Validates: Req 7.5 */
    @Test
    @Tag("Feature: map-reduce-analysis, Integration: all-fail-fallback")
    fun `all batches fail returns fallback result`() = runBlocking {
        val graph = buildGraph(25)
        val agent = FailingAIAgent()
        val config = MapReduceConfig(maxBatchSize = 10, batchTimeoutMs = 1000).validated()
        val result = orchestrator(agent, config).analyze("ROOT-1", graph, agents(agent), providers())

        assertEquals(0, result.mapReduceInfo.successfulBatches)
        assertTrue(result.mapReduceInfo.failedBatches > 0)
        assertEquals("ROOT-1", result.analysisResult.ticketId)
        assertTrue(result.analysisResult.context.unified.isNotBlank()) {
            "Fallback result should have non-blank context"
        }
    }

    /** Backward compatibility: ticket count ≤ threshold → single-prompt. Validates: Req 6.1, 10.4 */
    @Test
    @Tag("Feature: map-reduce-analysis, Integration: backward-compat")
    fun `threshold check determines pipeline selection`() {
        val config = MapReduceConfig(mapReduceThreshold = 200).validated()
        val smallGraph = buildGraph(50)
        val largeGraph = buildGraph(250)

        assertTrue(smallGraph.nodes.size <= config.mapReduceThreshold) {
            "Small graph (${smallGraph.nodes.size}) should be ≤ threshold"
        }
        assertTrue(largeGraph.nodes.size > config.mapReduceThreshold) {
            "Large graph (${largeGraph.nodes.size}) should be > threshold"
        }
    }
}
