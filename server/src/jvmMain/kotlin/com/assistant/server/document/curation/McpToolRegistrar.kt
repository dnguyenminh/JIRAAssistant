package com.assistant.server.document.curation

/**
 * Registers MCP KB lookup tools for AI agents that support function calling.
 *
 * Requirements: 10.2, 10.3, 10.4
 */
interface McpToolRegistrar {
    /**
     * Build the MCP tool instruction block for the prompt.
     *
     * @param referenceOnlyTickets Ticket IDs available for on-demand lookup
     * @return Tool block string (empty if agent doesn't support tools)
     */
    fun buildToolBlock(referenceOnlyTickets: List<String>): String

    /**
     * Check if the given agent type supports MCP tool use.
     *
     * @param agentType Agent identifier (e.g., "GeminiCliAgent")
     * @return true if agent supports function calling
     */
    fun isToolUseSupported(agentType: String): Boolean
}
