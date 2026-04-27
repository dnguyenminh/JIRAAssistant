package com.assistant.server.document.curation

/**
 * Default implementation of [McpToolRegistrar].
 *
 * Registers kb_search and kb_read tools for AI agents
 * that support function calling (GeminiCliAgent).
 *
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6
 */
class DefaultMcpToolRegistrar : McpToolRegistrar {

    override fun buildToolBlock(referenceOnlyTickets: List<String>): String {
        if (referenceOnlyTickets.isEmpty()) return ""
        val ticketList = referenceOnlyTickets
            .take(CurationConfig.MAX_MCP_LOOKUPS)
            .joinToString(", ")
        return buildString {
            appendLine("=== AVAILABLE TOOLS ===")
            appendLine()
            appendLine("You can request additional ticket details using:")
            appendLine()
            appendLine("Tool: kb_search")
            appendLine("  Parameters: ticketId (string)")
            appendLine("  Returns: Full KB record for the ticket")
            appendLine()
            appendLine("Tool: kb_read")
            appendLine("  Parameters: ticketId (string), fieldName (string)")
            appendLine("  Valid fieldNames: businessSummary, asIsState,")
            appendLine("    toBeState, extractedRequirements, technicalDetails")
            appendLine()
            appendLine("Available tickets for lookup: $ticketList")
            appendLine()
            appendLine("IMPORTANT: Max ${CurationConfig.MAX_MCP_LOOKUPS} lookups.")
            appendLine("Only request if the summary is insufficient.")
        }
    }

    override fun isToolUseSupported(agentType: String): Boolean {
        return agentType in TOOL_CAPABLE_AGENTS
    }

    companion object {
        private val TOOL_CAPABLE_AGENTS = setOf(
            "GeminiCliAgent", "gemini", "gemini-cli"
        )
    }
}
