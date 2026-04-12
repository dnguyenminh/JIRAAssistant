package com.assistant.frontend.components.chat

import com.assistant.chat.ChatAction
import com.assistant.frontend.pages.graph.GraphFilterPanel
import com.assistant.frontend.pages.graph.GraphState
import com.assistant.frontend.router.Router
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement

/**
 * Handles ChatAction types related to graph navigation and filtering.
 * Dispatches actions locally on the frontend without server round-trips.
 *
 * Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 10.1, 10.2, 19.3
 */
internal object GraphActionHandler {

    private val handledTypes = setOf(
        "focusNode", "filterByType", "filterByCluster",
        "resetFilters", "searchNodes", "navigateToGraph", "openUrl"
    )

    /** Pending action queued while navigating to the graph page. */
    private var pendingAction: ChatAction? = null

    fun canHandle(action: ChatAction): Boolean = action.type in handledTypes

    /**
     * Execute a graph-related action.
     * Returns a user-facing status message (confirmation or error).
     */
    fun execute(action: ChatAction): String = when (action.type) {
        "focusNode"       -> handleFocusNode(action)
        "filterByType"    -> handleFilterByType(action)
        "filterByCluster" -> handleFilterByCluster(action)
        "resetFilters"    -> handleResetFilters()
        "searchNodes"     -> handleSearchNodes(action)
        "navigateToGraph" -> handleNavigateToGraph(action)
        "openUrl"         -> handleOpenUrl(action)
        else              -> "Unknown graph action: ${action.type}"
    }

    /** Called by KnowledgeGraphPage after the page finishes rendering. */
    fun executePendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        window.setTimeout({ execute(action) }, 300)
    }

    // -- Action handlers -----------------------------------------------------

    private fun handleFocusNode(action: ChatAction): String {
        val nodeKey = action.params["nodeKey"] ?: return "Missing nodeKey parameter."
        if (!isOnGraphPage()) return navigateThenExecute(action)
        val node = GraphState.allNodes.find { it.key == nodeKey || it.id == nodeKey }
            ?: return "Node **$nodeKey** not found in current graph."
        GraphFilterPanel.activateFocusMode(node.id)
        return "Focused on **$nodeKey**."
    }

    private fun handleFilterByType(action: ChatAction): String {
        if (!isOnGraphPage()) return navigateThenExecute(action)
        val types = action.params["types"]?.split(",")?.map { it.trim() } ?: return "Missing types parameter."
        applyTypeCheckboxes(types.toSet())
        GraphFilterPanel.onFilterChange()
        return "Filtered by types: ${types.joinToString(", ")}."
    }

    private fun handleFilterByCluster(action: ChatAction): String {
        if (!isOnGraphPage()) return navigateThenExecute(action)
        val clusterId = action.params["clusterId"] ?: return "Missing clusterId parameter."
        val exists = GraphState.allClusters.any { it.id.toString() == clusterId }
        if (!exists) return "Cluster **$clusterId** not found in current graph."
        applyClusterDropdown(clusterId)
        GraphFilterPanel.onFilterChange()
        val label = GraphState.allClusters.find { it.id.toString() == clusterId }?.label ?: clusterId
        return "Filtered by cluster: **$label**."
    }

    private fun handleResetFilters(): String {
        if (!isOnGraphPage()) return "Not on Knowledge Graph page. Navigate first."
        GraphFilterPanel.resetAll()
        return "All filters have been reset."
    }

    private fun handleSearchNodes(action: ChatAction): String {
        if (!isOnGraphPage()) return navigateThenExecute(action)
        val query = action.params["query"] ?: return "Missing query parameter."
        applySearchInput(query)
        GraphFilterPanel.onFilterChange()
        return "Searching for **$query**."
    }

    private fun handleNavigateToGraph(action: ChatAction): String {
        Router.navigateTo("knowledge_graph")
        return "Navigated to Knowledge Graph."
    }

    private fun handleOpenUrl(action: ChatAction): String {
        val url = action.params["url"] ?: return "Missing url parameter."
        window.open(url, "_blank")
        return "Opened link in new tab."
    }

    // -- Helpers --------------------------------------------------------------

    private fun isOnGraphPage(): Boolean =
        Router.getCurrentRoute() == "knowledge_graph"

    /**
     * Navigate to the graph page first, then queue the action for execution
     * once the page has rendered.
     */
    private fun navigateThenExecute(action: ChatAction): String {
        pendingAction = action
        Router.navigateTo("knowledge_graph")
        return "Navigating to Knowledge Graph…"
    }

    private fun applyTypeCheckboxes(types: Set<String>) {
        // Dynamically find all type checkboxes in the container
        val container = document.getElementById("filter-type-container") ?: return
        val checkboxes = container.querySelectorAll("input[type=checkbox]")
        for (i in 0 until checkboxes.length) {
            val cb = checkboxes.item(i) as? HTMLInputElement ?: continue
            val cbType = cb.id.removePrefix("filter-type-").replace("-", " ")
            cb.checked = types.any { it.equals(cbType, ignoreCase = true) }
        }
    }

    private fun applyClusterDropdown(clusterId: String) {
        (document.getElementById("filter-cluster") as? HTMLSelectElement)?.value = clusterId
    }

    private fun applySearchInput(query: String) {
        (document.getElementById("graphSearchInput") as? HTMLInputElement)?.value = query
    }
}
