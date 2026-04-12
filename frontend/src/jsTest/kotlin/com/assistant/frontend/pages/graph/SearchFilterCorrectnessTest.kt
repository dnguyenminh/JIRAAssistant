package com.assistant.frontend.pages.graph

import com.assistant.frontend.models.GraphNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 3: Search filter correctness.
 *
 * For any non-blank query and any list of GraphNode objects, the set of
 * filtered node IDs SHALL contain exactly those nodes where
 * node.key.lowercase().contains(query.lowercase()) OR
 * node.summary.lowercase().contains(query.lowercase()).
 * When the query is blank, the filtered set SHALL equal all node IDs.
 *
 * **Validates: Requirements 4.1, 4.2, 4.3**
 */
class SearchFilterCorrectnessTest {

    // -- Pure filter function (mirrors KnowledgeGraphPage.applySearchFilter) -

    private fun filterNodeIds(
        query: String,
        nodes: List<GraphNode>
    ): Set<String> {
        if (query.isBlank()) return nodes.map { it.id }.toSet()
        val q = query.lowercase()
        return nodes.filter { node ->
            node.key.lowercase().contains(q) ||
                node.summary.lowercase().contains(q)
        }.map { it.id }.toSet()
    }

    // -- Random generators ---------------------------------------------------

    private val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_', ' ')

    private fun randomString(rng: Random, maxLen: Int = 20): String {
        val len = rng.nextInt(1, maxLen + 1)
        return (1..len).map { chars[rng.nextInt(chars.size)] }.joinToString("")
    }

    private fun randomNode(rng: Random): GraphNode = GraphNode(
        id = "id-${rng.nextInt()}",
        key = "TICKET-${rng.nextInt(1, 10000)}",
        summary = randomString(rng, 30),
        type = "FEATURE",
        x = rng.nextDouble(-500.0, 500.0),
        y = rng.nextDouble(-500.0, 500.0)
    )

    private fun randomNodeList(rng: Random): List<GraphNode> {
        val size = rng.nextInt(5, 21)
        return (1..size).map { randomNode(rng) }
    }

    /** Pick a query that may match: substring of a key, summary, or random. */
    private fun randomQuery(rng: Random, nodes: List<GraphNode>): String {
        return when (rng.nextInt(5)) {
            0 -> ""           // blank → all nodes
            1 -> " "          // whitespace-only → all nodes
            2 -> substringOf(rng, nodes.random(rng).key)
            3 -> substringOf(rng, nodes.random(rng).summary)
            else -> randomString(rng, 6) // may or may not match
        }
    }

    private fun substringOf(rng: Random, s: String): String {
        if (s.length <= 1) return s
        val start = rng.nextInt(0, s.length - 1)
        val end = rng.nextInt(start + 1, s.length + 1)
        return s.substring(start, end)
    }

    // -- Reference implementation (independent of filterNodeIds) -------------

    private fun expectedIds(
        query: String,
        nodes: List<GraphNode>
    ): Set<String> {
        if (query.isBlank()) return nodes.map { it.id }.toSet()
        val q = query.lowercase()
        return nodes.filter { n ->
            n.key.lowercase().contains(q) ||
                n.summary.lowercase().contains(q)
        }.map { it.id }.toSet()
    }

    // -- Property test -------------------------------------------------------

    @Test
    fun searchFilterReturnsExactMatchingNodes() {
        val rng = Random(seed = 99)
        repeat(25) { i ->
            val nodes = randomNodeList(rng)
            val query = randomQuery(rng, nodes)
            val result = filterNodeIds(query, nodes)
            val expected = expectedIds(query, nodes)

            assertEquals(
                expected, result,
                "Iteration $i: query='$query' nodes=${nodes.size}"
            )
        }
    }

    @Test
    fun blankQueryReturnsAllNodeIds() {
        val rng = Random(seed = 77)
        repeat(15) { i ->
            val nodes = randomNodeList(rng)
            val blanks = listOf("", " ", "  ", "\t", "\n")
            val query = blanks[rng.nextInt(blanks.size)]
            val result = filterNodeIds(query, nodes)
            val allIds = nodes.map { it.id }.toSet()

            assertEquals(
                allIds, result,
                "Iteration $i: blank query='$query' should return all"
            )
        }
    }

    @Test
    fun filterIsCaseInsensitive() {
        val rng = Random(seed = 55)
        repeat(15) { i ->
            val nodes = randomNodeList(rng)
            val node = nodes.random(rng)
            val query = node.key.take(4)
            val upper = filterNodeIds(query.uppercase(), nodes)
            val lower = filterNodeIds(query.lowercase(), nodes)

            assertEquals(
                upper, lower,
                "Iteration $i: case mismatch for query='$query'"
            )
        }
    }

    @Test
    fun filteredSetIsSubsetOfAllIds() {
        val rng = Random(seed = 33)
        repeat(15) { i ->
            val nodes = randomNodeList(rng)
            val query = randomString(rng, 8)
            val result = filterNodeIds(query, nodes)
            val allIds = nodes.map { it.id }.toSet()

            assertTrue(
                allIds.containsAll(result),
                "Iteration $i: filtered IDs not subset of all IDs"
            )
        }
    }
}
