package com.assistant.domain

import com.assistant.ai.AIResult
import com.assistant.ai.FakeAIAgent
import com.assistant.jira.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for FeatureNetworkMapper — external node creation.
 * Validates: Requirements 2.2, 2.3, 2.4
 */
class FeatureNetworkMapperTest {

    private val fakeAgent = FakeAIAgent(response = AIResult.Success("{}"))
    private val mapper = FeatureNetworkMapper(fakeAgent)

    // --- Task 3.1: Single external link ---

    @Test
    fun `map with external linked ticket creates edge and external node with correct metadata`() = runTest {
        val externalKey = "OTHER-5"
        val externalId = "505"
        val externalSummary = "External ticket summary"
        val externalStatus = "In Progress"

        val issue = jiraIssue(
            id = "101", key = "PROJ-1", summary = "Internal ticket",
            issuelinks = listOf(
                issueLink(
                    typeName = "Blocks",
                    outward = linkedIssue(externalId, externalKey, externalSummary, externalStatus)
                )
            )
        )

        val graph = mapper.map(listOf(issue))

        // Edge exists between internal and external ticket
        val edge = graph.edges.find {
            (it.fromId == "101" && it.toId == externalId) ||
                (it.fromId == externalId && it.toId == "101")
        }
        assertNotNull(edge, "Edge between PROJ-1 and $externalKey must exist")
        assertTrue(edge.relationshipType.startsWith("link:"), "Edge type should be link:*")

        // External node exists with correct metadata
        val extNode = graph.nodes.find { it.id == externalId }
        assertNotNull(extNode, "External node for $externalKey must exist")
        assertEquals(externalKey, extNode.key)
        assertEquals(externalSummary, extNode.summary)
        assertEquals(externalStatus, extNode.status)
        assertTrue(extNode.isExternal, "External node must have isExternal=true")

        // Internal node also present
        val intNode = graph.nodes.find { it.id == "101" }
        assertNotNull(intNode, "Internal node PROJ-1 must exist")
        assertEquals(false, intNode.isExternal, "Internal node must have isExternal=false")
    }

    // --- Task 3.2: Multiple external links ---

    @Test
    fun `map with multiple external links creates all edges and external nodes`() = runTest {
        val issue = jiraIssue(
            id = "101", key = "PROJ-1", summary = "Internal ticket",
            issuelinks = listOf(
                issueLink("Blocks", outward = linkedIssue("501", "EXT-1", "Ext one", "Done")),
                issueLink("Relates", outward = linkedIssue("502", "EXT-2", "Ext two", "Open")),
                issueLink("Duplicates", inward = linkedIssue("503", "EXT-3", "Ext three"))
            )
        )
        val graph = mapper.map(listOf(issue))

        assertEquals(4, graph.nodes.size, "1 internal + 3 external nodes")
        assertEquals(3, graph.edges.size, "3 link edges")
        val extNodes = graph.nodes.filter { it.isExternal }
        assertEquals(3, extNodes.size)
        assertEquals(setOf("501", "502", "503"), extNodes.map { it.id }.toSet())
    }

    // --- Task 3.3: Mix internal/external links ---

    @Test
    fun `map with mix of internal and external links creates correct edges`() = runTest {
        val issueA = jiraIssue(
            id = "101", key = "PROJ-1", summary = "First ticket",
            issuelinks = listOf(
                issueLink("Relates", outward = linkedIssue("102", "PROJ-2", "Second")),
                issueLink("Blocks", outward = linkedIssue("501", "EXT-1", "External", "Done"))
            )
        )
        val issueB = jiraIssue(id = "102", key = "PROJ-2", summary = "Second ticket")
        val graph = mapper.map(listOf(issueA, issueB))

        val internalEdge = graph.edges.find { it.fromId == "101" && it.toId == "102" }
        assertNotNull(internalEdge, "Internal edge must exist")
        val externalEdge = graph.edges.find {
            (it.fromId == "101" && it.toId == "501") || (it.fromId == "501" && it.toId == "101")
        }
        assertNotNull(externalEdge, "External edge must exist")
        val extNode = graph.nodes.find { it.id == "501" }
        assertNotNull(extNode, "External node must exist")
        assertTrue(extNode.isExternal)
        val intNodes = graph.nodes.filter { !it.isExternal }
        assertEquals(2, intNodes.size, "2 internal nodes unchanged")
    }

    // --- Task 3.4: Dedup — 2 issues link to same external ticket ---

    @Test
    fun `map dedup two issues linking to same external creates one node two edges`() = runTest {
        val issueA = jiraIssue(
            id = "101", key = "PROJ-1", summary = "First ticket",
            issuelinks = listOf(
                issueLink("Blocks", outward = linkedIssue("501", "EXT-1", "Shared ext", "Done"))
            )
        )
        val issueB = jiraIssue(
            id = "102", key = "PROJ-2", summary = "Second ticket",
            issuelinks = listOf(
                issueLink("Relates", outward = linkedIssue("501", "EXT-1", "Shared ext", "Done"))
            )
        )
        val graph = mapper.map(listOf(issueA, issueB))

        val extNodes = graph.nodes.filter { it.isExternal }
        assertEquals(1, extNodes.size, "Only 1 external node for EXT-1")
        assertEquals("501", extNodes.first().id)
        val extEdges = graph.edges.filter { it.fromId == "101" && it.toId == "501" || it.fromId == "102" && it.toId == "501" }
        assertEquals(2, extEdges.size, "2 edges from different issues to same external")
    }

    // --- Test data helpers ---

    companion object {
        fun jiraIssue(
            id: String, key: String, summary: String,
            status: String = "To Do",
            issuelinks: List<JiraIssueLink>? = null,
            parent: JiraParent? = null,
            subtasks: List<JiraSubtask>? = null
        ) = JiraIssue(
            id = id, key = key,
            fields = JiraIssueFields(
                summary = summary,
                status = JiraStatus(name = status),
                issuelinks = issuelinks,
                parent = parent,
                subtasks = subtasks
            )
        )

        fun issueLink(
            typeName: String = "Relates",
            outward: JiraLinkedIssue? = null,
            inward: JiraLinkedIssue? = null
        ) = JiraIssueLink(
            type = JiraIssueLinkType(name = typeName),
            outwardIssue = outward,
            inwardIssue = inward
        )

        fun linkedIssue(
            id: String, key: String,
            summary: String = "", status: String = "To Do"
        ) = JiraLinkedIssue(
            id = id, key = key,
            fields = JiraLinkedIssueFields(
                summary = summary,
                status = JiraStatus(name = status)
            )
        )
    }
}
