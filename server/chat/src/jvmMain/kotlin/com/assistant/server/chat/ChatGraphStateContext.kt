package com.assistant.server.chat

import com.assistant.chat.GraphChatContext

/**
 * Builds context string from frontend graph state (focused node, filters, etc.)
 * Extracted from ChatServiceImpl for file size compliance.
 */
internal object ChatGraphStateContext {

    fun build(gc: GraphChatContext?): String {
        if (gc == null) return "User is NOT on the Knowledge Graph page."
        val parts = mutableListOf("User is viewing the Knowledge Graph.")
        gc.focusedNodeKey?.let { key ->
            parts.add(
                "The user is currently focused on Jira ticket \"$key\". " +
                "\"$key\" is the COMPLETE Jira issue key (project prefix + number). " +
                "Use this EXACT value \"$key\" as the issueId/issue key when calling any Jira tools. " +
                "Do NOT split, truncate, or extract only the project prefix from this key."
            )
        }
        if (gc.activeTypeFilters.isNotEmpty()) {
            parts.add("Active type filters: ${gc.activeTypeFilters.joinToString(", ")}")
        }
        gc.selectedClusterId?.let { parts.add("Selected cluster: $it") }
        if (gc.searchQuery.isNotBlank()) parts.add("Search query: ${gc.searchQuery}")
        parts.add("Visible nodes: ${gc.visibleNodeCount}, Depth: ${gc.depthValue}")
        return parts.joinToString(" ")
    }
}
