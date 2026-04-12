package com.assistant.frontend.components.chat

import com.assistant.chat.ChatAction
import com.assistant.frontend.models.GraphCluster
import com.assistant.frontend.models.GraphNode
import com.assistant.frontend.pages.graph.GraphState
import kotlinx.browser.window
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for GraphActionHandler.
 *
 * Tests canHandle() dispatch, execute() return messages, and edge cases.
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6
 */
class GraphActionHandlerTest {

    @BeforeTest
    fun setup() {
        GraphState.reset()
        // Reset hash so isOnGraphPage() returns false
        window.location.hash = ""
    }

    // -- canHandle: all 7 action types --------------------------------------

    @Test
    fun canHandleReturnsTrueForAllSevenActionTypes() {
        val types = listOf(
            "focusNode", "filterByType", "filterByCluster",
            "resetFilters", "searchNodes", "navigateToGraph", "openUrl"
        )
        for (type in types) {
            val action = ChatAction(type = type, label = "test")
            assertTrue(
                GraphActionHandler.canHandle(action),
                "canHandle should return true for '$type'"
            )
        }
    }

    @Test
    fun canHandleReturnsFalseForUnknownTypes() {
        val unknowns = listOf(
            "navigate", "unknown", "deleteNode",
            "FOCUSNODE", "FilterByType", "", "focus_node"
        )
        for (type in unknowns) {
            val action = ChatAction(type = type, label = "test")
            assertFalse(
                GraphActionHandler.canHandle(action),
                "canHandle should return false for '$type'"
            )
        }
    }

    // -- execute: focusNode --------------------------------------------------

    @Test
    fun focusNodeMissingParamReturnsMissingMessage() {
        val action = ChatAction(
            type = "focusNode", label = "Focus",
            params = emptyMap()
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Missing nodeKey parameter.", result)
    }

    @Test
    fun focusNodeNotFoundWhenOnGraphPage() {
        window.location.hash = "#knowledge_graph"
        GraphState.allNodes = listOf(
            makeNode("n-1", "TK-1", "Node one"),
            makeNode("n-2", "TK-2", "Node two")
        )
        val action = ChatAction(
            type = "focusNode", label = "Focus",
            params = mapOf("nodeKey" to "TK-999")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Node **TK-999** not found in current graph.", result)
    }

    @Test
    fun focusNodeNavigatesWhenNotOnGraphPage() {
        window.location.hash = "#dashboard"
        GraphState.allNodes = listOf(makeNode("n-1", "TK-1", "Node one"))
        val action = ChatAction(
            type = "focusNode", label = "Focus",
            params = mapOf("nodeKey" to "TK-1")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigating to Knowledge Graph…", result)
    }

    // -- execute: filterByType -----------------------------------------------

    @Test
    fun filterByTypeMissingParamWhenOnGraphPage() {
        window.location.hash = "#knowledge_graph"
        val action = ChatAction(
            type = "filterByType", label = "Filter",
            params = emptyMap()
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Missing types parameter.", result)
    }

    @Test
    fun filterByTypeNavigatesWhenNotOnGraphPage() {
        window.location.hash = "#dashboard"
        val action = ChatAction(
            type = "filterByType", label = "Filter",
            params = mapOf("types" to "FEATURE,DEPENDENCY")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigating to Knowledge Graph…", result)
    }

    // -- execute: filterByCluster --------------------------------------------

    @Test
    fun filterByClusterMissingParamWhenOnGraphPage() {
        window.location.hash = "#knowledge_graph"
        val action = ChatAction(
            type = "filterByCluster", label = "Cluster",
            params = emptyMap()
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Missing clusterId parameter.", result)
    }

    @Test
    fun filterByClusterNotFoundWhenOnGraphPage() {
        window.location.hash = "#knowledge_graph"
        GraphState.allClusters = listOf(
            GraphCluster(id = 1, color = "#fff", label = "Auth")
        )
        val action = ChatAction(
            type = "filterByCluster", label = "Cluster",
            params = mapOf("clusterId" to "99")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals(
            "Cluster **99** not found in current graph.", result
        )
    }

    @Test
    fun filterByClusterNavigatesWhenNotOnGraphPage() {
        window.location.hash = "#dashboard"
        val action = ChatAction(
            type = "filterByCluster", label = "Cluster",
            params = mapOf("clusterId" to "1")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigating to Knowledge Graph…", result)
    }

    // -- execute: resetFilters -----------------------------------------------

    @Test
    fun resetFiltersNotOnGraphPageReturnsMessage() {
        window.location.hash = "#dashboard"
        val action = ChatAction(
            type = "resetFilters", label = "Reset"
        )
        val result = GraphActionHandler.execute(action)
        assertEquals(
            "Not on Knowledge Graph page. Navigate first.",
            result
        )
    }

    // -- execute: searchNodes ------------------------------------------------

    @Test
    fun searchNodesMissingQueryWhenOnGraphPage() {
        window.location.hash = "#knowledge_graph"
        val action = ChatAction(
            type = "searchNodes", label = "Search",
            params = emptyMap()
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Missing query parameter.", result)
    }

    @Test
    fun searchNodesNavigatesWhenNotOnGraphPage() {
        window.location.hash = "#dashboard"
        val action = ChatAction(
            type = "searchNodes", label = "Search",
            params = mapOf("query" to "auth")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigating to Knowledge Graph…", result)
    }

    // -- execute: navigateToGraph --------------------------------------------

    @Test
    fun navigateToGraphReturnsNavigatedMessage() {
        val action = ChatAction(
            type = "navigateToGraph", label = "Go to Graph"
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Navigated to Knowledge Graph.", result)
    }

    // -- execute: openUrl ----------------------------------------------------

    @Test
    fun openUrlMissingParamReturnsMissingMessage() {
        val action = ChatAction(
            type = "openUrl", label = "Open",
            params = emptyMap()
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Missing url parameter.", result)
    }

    @Test
    fun openUrlWithUrlReturnsOpenedMessage() {
        val action = ChatAction(
            type = "openUrl", label = "Open",
            params = mapOf("url" to "https://example.com")
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Opened link in new tab.", result)
    }

    // -- execute: unknown type -----------------------------------------------

    @Test
    fun unknownActionTypeReturnsUnknownMessage() {
        val action = ChatAction(
            type = "deleteNode", label = "Delete"
        )
        val result = GraphActionHandler.execute(action)
        assertEquals("Unknown graph action: deleteNode", result)
    }

    // -- Helper --------------------------------------------------------------

    private fun makeNode(
        id: String, key: String, summary: String,
        type: String = "FEATURE", clusterId: Int? = null
    ) = GraphNode(
        id = id, key = key, summary = summary,
        type = type, x = 0.0, y = 0.0, clusterId = clusterId
    )
}
