package com.assistant.server.indexing

import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.graph.Cluster
import com.assistant.kb.EvolutionEntry
import com.assistant.kb.KBRecord
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
 * Property-based tests for IndexingPipeline format functions.
 *
 * Feature: graph-filter-focus-mode
 * Property 6: Embedding Text Format Correctness
 * **Validates: Requirements 12.1, 12.2, 13.1, 13.2, 13.4, 14.1, 14.2, 14.4**
 */
@OptIn(ExperimentalKotest::class)
class IndexingPipelinePropertyTest {

    // ── Generators ──────────────────────────────────────────────────

    private fun arbAlphanumeric(range: IntRange = 1..20): Arb<String> =
        Arb.string(minSize = range.first, maxSize = range.last, codepoints = Codepoint.alphanumeric())

    private fun genTicketNode(): Arb<TicketNode> = arbitrary {
        TicketNode(
            id = Arb.int(1..99999).bind().toString(),
            key = "PROJ-${Arb.int(1..9999).bind()}",
            summary = arbAlphanumeric(3..40).bind(),
            status = Arb.element("Open", "In Progress", "Done").bind()
        )
    }

    private fun genTicketEdge(): Arb<TicketEdge> = arbitrary {
        TicketEdge(
            fromId = Arb.int(1..99999).bind().toString(),
            toId = Arb.int(1..99999).bind().toString(),
            relationshipType = Arb.element("blocks", "depends_on", "relates_to", "duplicates").bind()
        )
    }

    private fun genEvolutionEntry(): Arb<EvolutionEntry> = arbitrary {
        EvolutionEntry(
            version = "${Arb.int(1..20).bind()}",
            date = "2024-${Arb.int(1..12).bind().toString().padStart(2, '0')}-${Arb.int(1..28).bind().toString().padStart(2, '0')}",
            description = arbAlphanumeric(5..50).bind(),
            changeType = Arb.element("CREATED", "UPDATED", "REVISED", "DELETED").bind()
        )
    }

    private fun genKBRecord(): Arb<KBRecord> = arbitrary {
        KBRecord(
            ticketId = "PROJ-${Arb.int(1..9999).bind()}",
            requirementSummary = arbAlphanumeric(5..60).bind(),
            evolutionHistory = Arb.list(genEvolutionEntry(), 1..4).bind(),
            scrumPoints = Arb.element(0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0).bind(),
            confidenceScore = Arb.double(0.0..1.0).bind(),
            rationale = arbAlphanumeric(5..60).bind(),
            similarTicketRefs = emptyList(),
            timestamp = "2024-01-01T00:00:00Z"
        )
    }

    // ── Property 6: TICKET format ───────────────────────────────────

    @Test
    fun `Property 6 - formatTicketText with description matches spec format`() = runTest {
        checkAll(PropTestConfig(iterations = 25), genTicketNode(), arbAlphanumeric(5..50)) { ticket, desc ->
            val result = IndexingPipeline.formatTicketText(ticket, desc)
            assertEquals("[${ticket.key}] ${ticket.summary}. $desc", result)
        }
    }

    @Test
    fun `Property 6 - formatTicketText without description uses summary only`() = runTest {
        checkAll(PropTestConfig(iterations = 25), genTicketNode()) { ticket ->
            val result = IndexingPipeline.formatTicketText(ticket)
            assertEquals("[${ticket.key}] ${ticket.summary}", result)
        }
    }

    @Test
    fun `Property 6 - formatTicketText with blank description uses summary only`() = runTest {
        val blanks = Arb.element("", " ", "  ", "\t", "\n")
        checkAll(PropTestConfig(iterations = 25), genTicketNode(), blanks) { ticket, blank ->
            val result = IndexingPipeline.formatTicketText(ticket, blank)
            assertEquals("[${ticket.key}] ${ticket.summary}", result)
        }
    }

    // ── Property 6: RELATIONSHIP format ─────────────────────────────

    @Test
    fun `Property 6 - formatRelationshipText matches spec format`() = runTest {
        checkAll(PropTestConfig(iterations = 25), genTicketNode(), genTicketEdge(), genTicketNode()) { source, edge, target ->
            val result = IndexingPipeline.formatRelationshipText(source, edge, target)
            val expected = "${source.key} ${edge.relationshipType} ${target.key}: ${source.summary} → ${target.summary}"
            assertEquals(expected, result)
        }
    }

    // ── Property 6: ANALYSIS format ─────────────────────────────────

    @Test
    fun `Property 6 - formatAnalysisText matches spec format`() = runTest {
        checkAll(PropTestConfig(iterations = 25), genKBRecord()) { record ->
            val result = IndexingPipeline.formatAnalysisText(record)
            val expected = "[${record.ticketId}] Estimate: ${record.scrumPoints}pts" +
                " (confidence: ${record.confidenceScore})." +
                " ${record.requirementSummary}. Rationale: ${record.rationale}"
            assertEquals(expected, result)
            assertTrue(result.startsWith("[${record.ticketId}]"))
            assertTrue(result.contains("Estimate:"))
            assertTrue(result.contains("confidence:"))
            assertTrue(result.contains("Rationale:"))
        }
    }

    // ── Property 6: EVOLUTION format ────────────────────────────────

    @Test
    fun `Property 6 - formatEvolutionText matches spec format`() = runTest {
        val arbTicketId = Arb.of("PROJ-1", "PROJ-42", "ICL2-100", "TEST-9999")
        checkAll(PropTestConfig(iterations = 25), arbTicketId, genEvolutionEntry()) { ticketId, entry ->
            val result = IndexingPipeline.formatEvolutionText(ticketId, entry)
            val expected = "[$ticketId] v${entry.version} (${entry.date}): ${entry.description} [${entry.changeType}]"
            assertEquals(expected, result)
            assertTrue(result.startsWith("[$ticketId]"))
            assertTrue(result.contains("[${entry.changeType}]"))
        }
    }

    // ── Property 6: CLUSTER format ──────────────────────────────────

    @Test
    fun `Property 6 - formatClusterText matches spec format`() = runTest {
        checkAll(PropTestConfig(iterations = 25), Arb.int(0..50), Arb.int(1..10)) { clusterId, nodeCount ->
            val nodes = (1..nodeCount).map {
                TicketNode("$it", "PROJ-$it", "Summary $it", "Open")
            }
            val nodeMap = nodes.associateBy { it.id }
            val cluster = Cluster(clusterId, nodes.map { it.id }, "#ff0000")

            val result = IndexingPipeline.formatClusterText(cluster, nodeMap)
            val top5Keys = nodes.take(5).joinToString(", ") { it.key }
            val expected = "Cluster $clusterId: contains $nodeCount tickets — $top5Keys"
            assertEquals(expected, result)
        }
    }

    @Test
    fun `Property 6 - formatClusterText limits to top 5 keys`() = runTest {
        checkAll(PropTestConfig(iterations = 50), Arb.int(6..20)) { nodeCount ->
            val nodes = (1..nodeCount).map {
                TicketNode("$it", "K-$it", "S$it", "Open")
            }
            val nodeMap = nodes.associateBy { it.id }
            val cluster = Cluster(0, nodes.map { it.id }, "#000")

            val result = IndexingPipeline.formatClusterText(cluster, nodeMap)
            val keysInResult = result.substringAfter("— ")
            val keyCount = keysInResult.split(", ").size
            assertEquals(5, keyCount, "Should contain exactly 5 keys, got: $keysInResult")
        }
    }
}
