package com.assistant.server.kb

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.assistant.db.JiraDatabase
import com.assistant.domain.NetworkGraph
import com.assistant.domain.TicketEdge
import com.assistant.domain.TicketNode
import com.assistant.kb.KBRepository
import com.assistant.kb.KBRepositoryImpl
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.kotest.common.ExperimentalKotest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Property 7: Graph Data Persistence Round-Trip
 *
 * For any valid NetworkGraph and projectKey, calling saveGraphData(projectKey, graph)
 * then getGraphData(projectKey) SHALL return a NetworkGraph with the same number of
 * nodes and edges, with each node and edge having equal values to the original.
 *
 * **Validates: Requirements 9.8**
 *
 * Feature: jira-assistant-app, Property 7: Graph Data Persistence Round-Trip
 */
@OptIn(ExperimentalKotest::class)
class GraphDataPersistencePropertyTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: JiraDatabase
    private lateinit var repository: KBRepository

    @BeforeEach
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        JiraDatabase.Schema.create(driver)
        database = JiraDatabase(driver)
        repository = KBRepositoryImpl(database)
    }

    // --- Generators ---

    private fun arbSafeString(min: Int = 1, max: Int = 20): Arb<String> =
        Arb.string(minSize = min, maxSize = max, codepoints = Codepoint.alphanumeric())

    private val arbTicketNode: Arb<TicketNode> = Arb.bind(
        arbSafeString(3, 10),
        arbSafeString(3, 10),
        arbSafeString(5, 30),
        Arb.element("Open", "In Progress", "Done", "Closed"),
        Arb.string(minSize = 3, maxSize = 15, codepoints = Codepoint.alphanumeric()).orNull()
    ) { id, key, summary, status, featureName ->
        TicketNode(id, key, summary, status, featureName)
    }

    private val arbTicketEdge: Arb<TicketEdge> = Arb.bind(
        arbSafeString(3, 10),
        arbSafeString(3, 10),
        Arb.element("blocks", "relates_to", "depends_on", "semantic"),
        Arb.boolean()
    ) { fromId, toId, relType, isSemantic ->
        TicketEdge(fromId, toId, relType, isSemantic)
    }

    private val arbNetworkGraph: Arb<NetworkGraph> = Arb.bind(
        Arb.list(arbTicketNode, 0..10),
        Arb.list(arbTicketEdge, 0..10)
    ) { nodes, edges ->
        NetworkGraph(nodes, edges)
    }

    @Test
    fun saveAndGetGraphDataRoundTrip() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbSafeString(2, 10), arbNetworkGraph) { projectKey, graph ->
                driver.execute(null, "DELETE FROM graph_data", 0)

                val saved = repository.saveGraphData(projectKey, graph)
                assertTrue(saved, "saveGraphData() should return true")

                val found = repository.getGraphData(projectKey)
                assertNotNull(found, "getGraphData should return non-null after save")
                assertEquals(graph.nodes.size, found!!.nodes.size, "Node count must match")
                assertEquals(graph.edges.size, found.edges.size, "Edge count must match")
                assertEquals(graph.nodes, found.nodes, "Nodes must be equal")
                assertEquals(graph.edges, found.edges, "Edges must be equal")
            }
        }
    }

    @Test
    fun getGraphDataReturnsNullForMissing() {
        runBlocking {
            val result = repository.getGraphData("NONEXISTENT-PROJECT")
            assertNull(result, "getGraphData should return null for non-existent project")
        }
    }

    @Test
    fun overwriteGraphDataReplacesExisting() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 25), arbSafeString(2, 10), arbNetworkGraph, arbNetworkGraph) { projectKey, graph1, graph2 ->
                driver.execute(null, "DELETE FROM graph_data", 0)

                repository.saveGraphData(projectKey, graph1)
                repository.saveGraphData(projectKey, graph2)

                val found = repository.getGraphData(projectKey)
                assertNotNull(found)
                assertEquals(graph2.nodes.size, found!!.nodes.size)
                assertEquals(graph2.edges.size, found.edges.size)
                assertEquals(graph2.nodes, found.nodes)
                assertEquals(graph2.edges, found.edges)
            }
        }
    }
}
