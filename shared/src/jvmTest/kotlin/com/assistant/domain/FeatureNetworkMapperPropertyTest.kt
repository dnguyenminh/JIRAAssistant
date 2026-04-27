package com.assistant.domain

import com.assistant.ai.AIResult
import com.assistant.ai.FakeAIAgent
import com.assistant.domain.FeatureNetworkMapperTest.Companion.issueLink
import com.assistant.domain.FeatureNetworkMapperTest.Companion.jiraIssue
import com.assistant.domain.FeatureNetworkMapperTest.Companion.linkedIssue
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
 * Property-based tests for FeatureNetworkMapper — external node handling.
 *
 * **Validates: Requirements 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4**
 */
class FeatureNetworkMapperPropertyTest {

    private val mapper = FeatureNetworkMapper(FakeAIAgent(response = AIResult.Success("{}")))

    // --- Generators ---

    private val arbLinkType: Arb<String> =
        Arb.element("Blocks", "Relates", "Duplicates", "Cloners")

    private val arbStatus: Arb<String> =
        Arb.element("To Do", "In Progress", "Done", "Closed")

    private fun arbExternalLinks(extIds: List<String>): Arb<List<Pair<String, String>>> =
        arbitrary {
            val count = Arb.int(0..minOf(3, extIds.size)).bind()
            extIds.shuffled().take(count).map { extId ->
                extId to arbLinkType.bind()
            }
        }

    // --- Property 1: Bug Condition — External links have edges and nodes ---

    /**
     * Property 1: Bug Condition — External Linked Tickets Have Node And Edge
     *
     * For random issues with external links, verify every external link
     * has a corresponding edge and an external node in the graph.
     *
     * **Validates: Requirements 2.2, 2.3, 2.4**
     */
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 1 - external links produce edges and external nodes`() = runTest {
        val arbScenario = arbitrary {
            val n = Arb.int(1..5).bind()
            val intIds = (1..n).map { "int-$it" }
            val extCount = Arb.int(1..4).bind()
            val extIds = (1..extCount).map { "ext-$it" }

            val issues = intIds.map { id ->
                val links = arbExternalLinks(extIds).bind()
                val issuelinks = links.map { (extId, type) ->
                    val status = arbStatus.bind()
                    issueLink(type, outward = linkedIssue(extId, "EXT-$extId", "Summary $extId", status))
                }
                jiraIssue(id = id, key = "PROJ-$id", summary = "Issue $id", issuelinks = issuelinks)
            }
            issues
        }

        checkAll(PropTestConfig(iterations = 25), arbScenario) { issues ->
            val graph = mapper.map(issues)
            val idSet = issues.map { it.id }.toHashSet()

            for (issue in issues) {
                for (link in issue.fields.issuelinks ?: emptyList()) {
                    val target = link.outwardIssue ?: link.inwardIssue ?: continue
                    if (target.id in idSet) continue
                    // External link — must have edge and node
                    val hasEdge = graph.edges.any {
                        (it.fromId == issue.id && it.toId == target.id) ||
                            (it.fromId == target.id && it.toId == issue.id)
                    }
                    assertTrue(hasEdge, "Edge missing: ${issue.id} <-> ${target.id}")
                    val extNode = graph.nodes.find { it.id == target.id }
                    assertTrue(extNode != null, "External node missing: ${target.id}")
                    assertTrue(extNode.isExternal, "Node ${target.id} must be external")
                    assertEquals(target.key, extNode.key)
                }
            }
        }
    }

    // --- Property 2: Preservation — Internal-only links produce same graph ---

    /**
     * Property 2: Preservation — Internal Edges And Nodes Unchanged
     *
     * For random issues where ALL links point to other issues in the list,
     * verify no external nodes exist and all internal edges are correct.
     *
     * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
     */
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Property 2 - internal only links produce no external nodes`() = runTest {
        val arbInternalScenario = arbitrary {
            val n = Arb.int(2..6).bind()
            val ids = (1..n).map { "int-$it" }

            ids.map { id ->
                val linkCount = Arb.int(0..minOf(3, n - 1)).bind()
                val targets = ids.filter { it != id }.shuffled().take(linkCount)
                val issuelinks = targets.map { targetId ->
                    val type = arbLinkType.bind()
                    issueLink(type, outward = linkedIssue(targetId, "PROJ-$targetId", "Summary $targetId"))
                }
                jiraIssue(id = id, key = "PROJ-$id", summary = "Issue $id", issuelinks = issuelinks)
            }
        }

        checkAll(PropTestConfig(iterations = 25), arbInternalScenario) { issues ->
            val graph = mapper.map(issues)
            val idSet = issues.map { it.id }.toSet()

            // No external nodes
            val extNodes = graph.nodes.filter { it.isExternal }
            assertTrue(extNodes.isEmpty(), "Expected 0 external nodes, got ${extNodes.size}")

            // All nodes are from input issues
            assertTrue(
                graph.nodes.map { it.id }.toSet().containsAll(idSet),
                "Graph must contain all internal nodes"
            )

            // All link edges exist for internal links
            for (issue in issues) {
                for (link in issue.fields.issuelinks ?: emptyList()) {
                    val target = link.outwardIssue ?: link.inwardIssue ?: continue
                    if (target.id !in idSet) continue
                    val hasEdge = graph.edges.any {
                        (it.fromId == issue.id && it.toId == target.id) ||
                            (it.fromId == target.id && it.toId == issue.id)
                    }
                    assertTrue(hasEdge, "Internal edge missing: ${issue.id} <-> ${target.id}")
                }
            }
        }
    }
}
