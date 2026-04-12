package com.assistant.frontend.components.chat

import com.assistant.chat.GraphChatContext
import com.assistant.frontend.pages.graph.GraphFilterPanel
import com.assistant.frontend.pages.graph.GraphState
import kotlinx.browser.window

/**
 * Builds [GraphChatContext] from current graph filter state.
 * Keeps AIChatSidebar thin by extracting graph-specific context logic.
 *
 * Requirements: 8.1, 8.2, 8.3
 */
internal object ChatGraphContextBuilder {

    private var cached: GraphChatContext? = null

    /** Latest cached graph context (null when not on knowledge_graph page). */
    fun current(): GraphChatContext? =
        if (currentScreen() == "knowledge_graph") cached else null

    /** Refresh cached context from GraphFilterPanel + GraphState. */
    fun refresh() {
        cached = if (currentScreen() == "knowledge_graph") buildFromState() else null
    }

    private fun buildFromState(): GraphChatContext {
        val filters = GraphFilterPanel.getFilters()
        return GraphChatContext(
            focusedNodeKey = lookupNodeKey(filters.focusNodeId),
            activeTypeFilters = filters.enabledTypes.toList(),
            selectedClusterId = filters.selectedClusterId,
            depthValue = filters.focusDepth,
            visibleNodeCount = GraphState.filteredNodeIds.size,
            searchQuery = filters.searchQuery
        )
    }

    private fun lookupNodeKey(nodeId: String?): String? {
        if (nodeId == null) return null
        return GraphState.allNodes.find { it.id == nodeId }?.key
    }

    private fun currentScreen(): String =
        window.location.hash.removePrefix("#").ifBlank { "dashboard" }
}
