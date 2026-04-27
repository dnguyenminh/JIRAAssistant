package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GraphLayoutResponse
import com.assistant.frontend.components.chat.GraphActionHandler
import com.assistant.frontend.pages.graph.*
import com.assistant.frontend.services.NavigationContext
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Knowledge Graph page — Ticket Relationship Network (MH3).
 * API: GET /api/graph/{projectKey} → GraphLayoutResponse
 */
object KnowledgeGraphPage {

    internal val scope = MainScope()
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun render(container: Element) {
        container.innerHTML = ""
        cleanup()
        scope.launch {
            val html = ApiClient.loadTemplate("knowledge-graph")
            val page = document.createElement("div") as HTMLElement
            page.style.apply { width = "100%"; height = "100%"; display = "flex"; flexDirection = "column" }
            page.innerHTML = html
            container.appendChild(page)
            GraphFilterPanel.init()
            GraphSearchCombobox.init()
            bindEvents()
            immediateRestoreFromSession()
            loadGraphData()
            GraphScanStatus.loadScanStatus()
        }
    }

    fun cleanup() {
        CytoscapeRenderer.destroy()
        GraphScanStatus.cancelJobs()
    }

    private fun bindEvents() {
        val searchInput = document.getElementById("graphSearchInput") as? HTMLInputElement
        searchInput?.addEventListener("input", {
            applySearchFilter()
        })
        document.getElementById("detailPanelClose")?.addEventListener("click", {
            GraphDetailPanel.close()
        })
        document.getElementById("graph-retry-btn")?.addEventListener("click", {
            loadGraphData()
        })
        document.getElementById("graph-project-btn")?.addEventListener("click", {
            com.assistant.frontend.router.Router.navigateTo("project_select")
        })
    }

    /**
     * Phase 1: Immediate display from sessionStorage.
     * Renders graph BEFORE API loads. Skips if navigation context exists.
     * Requirements: 2.2, 2.6, 3.6
     */
    private fun immediateRestoreFromSession() {
        if (NavigationContext.peek()?.screen == "knowledge_graph") return
        val data = GraphStateManager.restore() ?: return
        populateGraphStateFromResponse(data)
        CytoscapeRenderer.renderGraph()
        GraphFilterPanel.populateNodeTypes(data.nodeTypes)
        GraphFilterPanel.populateClusters(GraphState.allClusters)
        updateNodeCount()
    }

    /** Populate GraphState fields from a GraphLayoutResponse. */
    private fun populateGraphStateFromResponse(data: GraphLayoutResponse) {
        GraphState.allNodes = data.nodes
        GraphState.allEdges = data.edges
        GraphState.allClusters = data.clusters ?: emptyList()
        GraphState.allNodeTypes = data.nodeTypes
        GraphState.typeColorMap = data.nodeTypes.associate { it.type to it.color }
        GraphState.filteredNodeIds = GraphState.allNodes.map { it.id }.toSet()
    }

    internal fun loadGraphData() {
        val projectKey = ApiClient.getProjectKey() ?: run {
            CytoscapeRenderer.renderEmptyState(); return
        }
        hideGraphError()
        scope.launch {
            try {
                val response = ApiClient.get("/api/graph/$projectKey")
                if (ApiClient.handleUnauthorized(response)) {
                    CytoscapeRenderer.renderEmptyState(); return@launch
                }
                if (response.status.value == 404) {
                    CytoscapeRenderer.renderEmptyState(); return@launch
                }
                if (response.status.value != 200) {
                    showGraphError("Server error (${response.status.value}). Try again later.")
                    return@launch
                }
                val body = response.bodyAsText()
                val graphData = json.decodeFromString<GraphLayoutResponse>(body)
                populateGraphStateFromResponse(graphData)
                GraphStateManager.save(graphData)
                CytoscapeRenderer.renderGraph()
                GraphFilterPanel.populateNodeTypes(graphData.nodeTypes)
                GraphFilterPanel.populateClusters(GraphState.allClusters)
                updateNodeCount()
                GraphActionHandler.executePendingAction()
                applyNavigationContext()
            } catch (e: Exception) {
                console.log("[KnowledgeGraphPage] Failed to load graph: ${e.message}")
                showGraphError("Failed to load graph data: ${e.message}")
            }
        }
    }

    private fun applySearchFilter() {
        GraphFilterPanel.onFilterChange()
    }

    internal fun updateNodeCount() {
        val visible = GraphState.filteredNodeIds.size
        val total = GraphState.allNodes.size
        GraphFilterPanel.updateNodeCount(visible, total)
    }

    private fun showGraphError(message: String) {
        val errorEl = document.getElementById("graph-error") as? HTMLElement
        val msgEl = document.getElementById("graph-error-msg") as? HTMLElement
        errorEl?.style?.display = ""
        msgEl?.textContent = message
    }

    private fun hideGraphError() {
        (document.getElementById("graph-error") as? HTMLElement)
            ?.style?.display = "none"
    }

    /**
     * If navigated here via ChatAction with nodeKey context,
     * auto-focus on the matching node.
     */
    private fun applyNavigationContext() {
        val params = NavigationContext.consume("knowledge_graph")
            ?: return
        val nodeKey = params["ticketKey"] ?: params["nodeKey"] ?: return
        val node = GraphState.allNodes.find {
            it.key == nodeKey || it.id == nodeKey
        } ?: return
        GraphFilterPanel.activateFocusMode(node.id)
    }
}
