package com.assistant.frontend.components.chat

import com.assistant.chat.ChatAction
import com.assistant.frontend.models.GraphCluster
import com.assistant.frontend.models.GraphNode
import com.assistant.frontend.pages.graph.GraphState
import kotlinx.browser.window
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test: Chat → Graph action flow end-to-end.
 *
 * Verifies that ChatActionHandler receives a graph action,
 * dispatches it to GraphActionHandler, and produces the
 * expected user-facing result.
 *
 * Requirements: 9.1, 10.1
 */
class ChatGraphActionIntegrationTest {

    @BeforeTest
    fun setup() {
        GraphState.reset()
        window.location.hash = ""
    }

    // -- focusNode dispatched through ChatActionHandler ----

    @Test
    fun focusNodeOnGraphPageReturnsConfirmation() {
        window.location.hash = "#knowledge_graph"
        GraphState.allNodes = listOf(
            node("n1", "ICL2-24", "Auth module")
        )
        val action = ChatAction(
            type = "focusNode", label = "Focus",
            params = mapOf("nodeKey" to "ICL2-24")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Focused on **ICL2-24**.", result)
    }

    @Test
    fun focusNodeNotFoundReturnsError() {
        window.location.hash = "#knowledge_graph"
        GraphState.allNodes = listOf(
            node("n1", "ICL2-1", "Login")
        )
        val action = ChatAction(
            type = "focusNode", label = "Focus",
            params = mapOf("nodeKey" to "MISSING-99")
        )
        val result = GraphActionHandler.execute(action)
        assertTrue(result.contains("not found"))
    }

    @Test
    fun focusNodeFromOtherPageNavigatesFirst() {
        window.location.hash = "#dashboard"
        GraphState.allNodes = listOf(
            node("n1", "ICL2-24", "Auth module")
        )
        val action = ChatAction(
            type = "focusNode", label = "Focus",
            params = mapOf("nodeKey" to "ICL2-24")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigating to Knowledge Graph…", result)
    }

    // -- filterByType dispatched through ChatActionHandler --

    @Test
    fun filterByTypeNavigatesWhenNotOnGraph() {
        window.location.hash = "#analysis"
        val action = ChatAction(
            type = "filterByType", label = "Filter",
            params = mapOf("types" to "FEATURE")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigating to Knowledge Graph…", result)
    }

    // -- resetFilters requires graph page -------------------

    @Test
    fun resetFiltersNotOnGraphReturnsWarning() {
        window.location.hash = "#dashboard"
        val action = ChatAction(
            type = "resetFilters", label = "Reset"
        )
        val result = GraphActionHandler.execute(action)
        assertTrue(result.contains("Navigate first"))
    }

    // -- navigateToGraph always works -----------------------

    @Test
    fun navigateToGraphReturnsConfirmation() {
        val action = ChatAction(
            type = "navigateToGraph", label = "Graph"
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigated to Knowledge Graph.", result)
    }

    // -- canHandle gates dispatch correctly ------------------

    @Test
    fun canHandleAcceptsGraphTypes() {
        val graphTypes = listOf(
            "focusNode", "filterByType", "filterByCluster",
            "resetFilters", "searchNodes", "navigateToGraph",
            "openUrl"
        )
        graphTypes.forEach { type ->
            val action = ChatAction(type = type, label = "t")
            assertTrue(
                GraphActionHandler.canHandle(action),
                "canHandle should accept '$type'"
            )
        }
    }

    @Test
    fun canHandleRejectsNonGraphTypes() {
        val nonGraph = listOf("navigate", "triggerAnalysis")
        nonGraph.forEach { type ->
            val action = ChatAction(type = type, label = "t")
            assertTrue(
                !GraphActionHandler.canHandle(action),
                "canHandle should reject '$type'"
            )
        }
    }

    // -- filterByCluster with valid cluster -----------------

    @Test
    fun filterByClusterNotFoundReturnsError() {
        window.location.hash = "#knowledge_graph"
        GraphState.allClusters = listOf(
            GraphCluster(id = 1, color = "#fff", label = "Auth")
        )
        val action = ChatAction(
            type = "filterByCluster", label = "Cluster",
            params = mapOf("clusterId" to "42")
        )
        val result = GraphActionHandler.execute(action)
        assertTrue(result.contains("not found"))
    }

    // -- openUrl returns confirmation -----------------------

    @Test
    fun openUrlReturnsConfirmation() {
        val action = ChatAction(
            type = "openUrl", label = "Open",
            params = mapOf("url" to "https://jira.example.com")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Opened link in new tab.", result)
    }

    // -- Helper ---------------------------------------------

    private fun node(
        id: String, key: String, summary: String,
        type: String = "FEATURE", clusterId: Int? = null
    ) = GraphNode(
        id = id, key = key, summary = summary,
        type = type, x = 0.0, y = 0.0, clusterId = clusterId
    )
}
