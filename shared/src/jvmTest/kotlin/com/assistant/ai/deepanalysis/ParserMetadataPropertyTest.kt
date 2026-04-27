package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.*
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
 * Property-based tests for ResponseToResultMapper draw.io metadata validation.
 *
 * Feature: drawio-template-diagrams, Property 9: Parser produces valid metadata
 */
class ParserMetadataPropertyTest {

    // ── Generators ──────────────────────────────────────────────────────

    private val validNodeTypes = listOf(
        "webapp", "database", "external_api", "server", "mobile",
        "cloud", "user", "service", "queue", "cache"
    )

    private val validTemplates = listOf(
        "flow", "deployment", "component", "dependency", "bpmn"
    )

    private fun arbAlpha(range: IntRange = 1..20): Arb<String> =
        Arb.string(range.first, range.last, Codepoint.alphanumeric())

    /** Random AIDrawioNode with id from a small pool to force duplicates */
    private fun arbAIDrawioNode(idPool: List<String>): Arb<AIDrawioNode> = arbitrary {
        AIDrawioNode(
            id = Arb.element(idPool).bind(),
            label = arbAlpha(1..30).bind(),
            type = Arb.element(validNodeTypes).bind()
        )
    }

    /**
     * Random AIDrawioConnection — 50% valid refs, 50% invalid refs
     * to ensure the mapper filters out bad connections.
     */
    private fun arbAIDrawioConnection(
        validIds: List<String>
    ): Arb<AIDrawioConnection> = arbitrary {
        val allCandidates = validIds + listOf("ghost_1", "ghost_2", "nonexistent")
        AIDrawioConnection(
            from = Arb.element(allCandidates).bind(),
            to = Arb.element(allCandidates).bind(),
            label = arbAlpha(0..20).bind()
        )
    }

    /**
     * Generator: AIDiagram with drawio format containing:
     * - Duplicate node IDs (small id pool → high collision rate)
     * - Connections referencing non-existent node IDs
     */
    private fun arbDrawioDiagramWithDupsAndBadConns(): Arb<AIDiagram> = arbitrary {
        // Small ID pool (3-5 IDs) with 5-15 nodes → guaranteed duplicates
        val poolSize = Arb.int(3..5).bind()
        val idPool = (1..poolSize).map { "node_$it" }
        val nodeCount = Arb.int(5..15).bind()
        val nodes = Arb.list(arbAIDrawioNode(idPool), nodeCount..nodeCount).bind()
        val connCount = Arb.int(1..20).bind()
        val connections = Arb.list(
            arbAIDrawioConnection(idPool), connCount..connCount
        ).bind()

        AIDiagram(
            type = Arb.element("deployment", "bpmn", "flow").bind(),
            title = arbAlpha(1..30).bind(),
            format = "drawio",
            drawioMetadata = AIDrawioMetadata(
                template = Arb.element(validTemplates).bind(),
                nodes = nodes,
                connections = connections
            )
        )
    }

    // ── Property 9: Parser produces valid metadata ──────────────────────

    /**
     * **Validates: Requirements 7.3, 7.4**
     *
     * For any AI response JSON with drawioMetadata containing duplicate
     * node IDs and connections referencing non-existent node IDs,
     * after parsing:
     * (a) all node IDs SHALL be unique
     * (b) all connections SHALL only reference node IDs that exist
     *
     * Feature: drawio-template-diagrams, Property 9: Parser produces valid metadata
     */
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 9 - Parser produces valid metadata dedup and connection filter`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            arbDrawioDiagramWithDupsAndBadConns()
        ) { aiDiagram ->
            val root = AIResponseRoot(diagrams = listOf(aiDiagram))
            val result = ResponseToResultMapper.map("TEST-1", root)
            val diagrams = result.diagrams

            assertEquals(1, diagrams.size, "Should produce exactly one diagram")
            val diagram = diagrams.first()
            assertEquals("drawio", diagram.format)

            val metadata = diagram.drawioMetadata
            requireNotNull(metadata) { "drawioMetadata should not be null" }

            // (a) All node IDs are unique
            val nodeIds = metadata.nodes.map { it.id }
            assertEquals(
                nodeIds.toSet().size, nodeIds.size,
                "Node IDs must be unique after dedup, got: $nodeIds"
            )

            // (b) All connections reference existing node IDs
            val nodeIdSet = nodeIds.toSet()
            metadata.connections.forEach { conn ->
                assertTrue(
                    conn.from in nodeIdSet,
                    "Connection from='${conn.from}' references non-existent node. Valid: $nodeIdSet"
                )
                assertTrue(
                    conn.to in nodeIdSet,
                    "Connection to='${conn.to}' references non-existent node. Valid: $nodeIdSet"
                )
            }
        }
    }
}
