package com.assistant.server.document.traversal

import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketNode
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Property 20: Relevance Scoring — Monotonicity.
 *
 * - Lower depth has score >= higher depth (ceteris paribus)
 * - ISSUE_LINK (blocking) scores > TEXT_REFERENCE (same depth, same factors)
 *
 * Tests [RelevanceScorer] directly since it's a separate object.
 *
 * **Validates: Requirements 1.9**
 */
@OptIn(ExperimentalKotest::class)
class RelevanceScorerPropertyTest {

    private val cfg = PropTestConfig(iterations = 100)

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 20: Relevance Scoring Monotonicity")
    fun `lower depth yields higher or equal score ceteris paribus`() {
        runBlocking {
            checkAll(
                cfg,
                Arb.int(0..8),
                Arb.int(1..9)
            ) { depthA, offset ->
                val depthB = depthA + offset
                val content = buildTicketContent(
                    status = "Open", updatedDate = "2025-01-01T00:00:00Z"
                )
                val nodeA = TicketNode(
                    "A-1", depthA, RelationshipType.ISSUE_LINK, "ROOT-1",
                    content
                )
                val nodeB = TicketNode(
                    "B-1", depthB, RelationshipType.ISSUE_LINK, "ROOT-1",
                    content
                )
                val scoreA = RelevanceScorer.compute(nodeA)
                val scoreB = RelevanceScorer.compute(nodeB)
                assertTrue(scoreA >= scoreB) {
                    "depth $depthA score=$scoreA < depth $depthB score=$scoreB"
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 20: Relevance Scoring Monotonicity")
    fun `ISSUE_LINK scores higher than TEXT_REFERENCE at same depth`() {
        runBlocking {
            checkAll(cfg, Arb.int(0..10)) { depth ->
                val content = buildTicketContent(
                    status = "Open", updatedDate = "2025-01-01T00:00:00Z"
                )
                val linkNode = TicketNode(
                    "L-1", depth, RelationshipType.ISSUE_LINK, "ROOT-1",
                    content
                )
                val textNode = TicketNode(
                    "T-1", depth, RelationshipType.TEXT_REFERENCE, "ROOT-1",
                    content
                )
                val linkScore = RelevanceScorer.compute(linkNode)
                val textScore = RelevanceScorer.compute(textNode)
                assertTrue(linkScore > textScore) {
                    "ISSUE_LINK score=$linkScore <= TEXT_REF score=$textScore " +
                        "at depth=$depth"
                }
            }
        }
    }

    @Test
    @Tag("Feature: deep-ticket-data-collection, Property 20: Relevance Scoring Monotonicity")
    fun `relationship weight ordering is consistent`() {
        val weights = RelationshipType.entries.map { type ->
            type to RelevanceScorer.relationshipWeight(type)
        }
        val root = weights.first { it.first == RelationshipType.ROOT }.second
        val parent = weights.first { it.first == RelationshipType.PARENT }.second
        val link = weights.first { it.first == RelationshipType.ISSUE_LINK }.second
        val sub = weights.first { it.first == RelationshipType.SUB_TASK }.second
        val text = weights.first { it.first == RelationshipType.TEXT_REFERENCE }.second

        assertTrue(root >= parent) { "ROOT < PARENT" }
        assertTrue(parent >= link) { "PARENT < ISSUE_LINK" }
        assertTrue(link >= sub) { "ISSUE_LINK < SUB_TASK" }
        assertTrue(sub >= text) { "SUB_TASK < TEXT_REFERENCE" }
    }
}
