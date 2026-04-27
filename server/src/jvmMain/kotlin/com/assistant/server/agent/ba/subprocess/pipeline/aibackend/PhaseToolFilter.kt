package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor

/**
 * Filters ToolDescriptor lists for each pipeline phase.
 * Uses case-insensitive pattern matching on tool names.
 * Deterministic: sorts by name first so input order is irrelevant.
 *
 * Reuses pattern matching approach from AgenticToolDetector.
 */
class PhaseToolFilter {

    /**
     * Returns true if any tool name contains "kb_search", "kb_ingest",
     * or "kb_write" (case-insensitive). These 3 patterns indicate
     * KB MCP server availability for multi-phase mode.
     */
    fun hasKbTools(tools: List<ToolDescriptor>): Boolean {
        val kbPatterns = listOf("kb_search", "kb_ingest", "kb_write")
        return tools.any { tool ->
            val name = tool.name.lowercase()
            kbPatterns.any { pattern -> name.contains(pattern) }
        }
    }

    /** Phase 1: Jira + KB + doc convert tools. */
    fun filterForPhase1(tools: List<ToolDescriptor>): List<ToolDescriptor> =
        filterForPhase(tools, PHASE1_PATTERNS)

    /** Phase 2: KB read-only tools. */
    fun filterForPhase2(tools: List<ToolDescriptor>): List<ToolDescriptor> =
        filterForPhase(tools, PHASE2_PATTERNS)

    /** Phase 3: KB read + diagram tools. */
    fun filterForPhase3(tools: List<ToolDescriptor>): List<ToolDescriptor> =
        filterForPhase(tools, PHASE3_PATTERNS)

    /**
     * Shared filter: sort by name (deterministic), exclude EXCLUDED_PATTERNS,
     * include only tools matching at least one pattern.
     */
    private fun filterForPhase(
        tools: List<ToolDescriptor>,
        patterns: List<String>
    ): List<ToolDescriptor> {
        return tools
            .sortedBy { it.name.lowercase() }
            .filter { tool ->
                val name = tool.name.lowercase()
                val excluded = EXCLUDED_PATTERNS.any { name.contains(it) }
                val included = patterns.any { name.contains(it) }
                !excluded && included
            }
    }

    companion object {
        // Phase 1: Jira + KB + doc convert
        internal val PHASE1_PATTERNS = listOf(
            "get_issue", "search", "jira",
            "analyze_ticket", "get_ticket_analysis",
            "kb_search", "kb_read", "kb_context",
            "kb_ingest", "kb_write",
            "convert_to_markdown", "markitdown"
        )
        // Phase 2: KB read-only
        internal val PHASE2_PATTERNS = listOf(
            "kb_search", "kb_search_smart",
            "kb_read", "kb_context"
        )
        // Phase 3: KB read + diagram
        internal val PHASE3_PATTERNS = listOf(
            "kb_search", "kb_read", "kb_context",
            "drawio", "draw.io", "diagram"
        )
        // Always excluded regardless of phase
        internal val EXCLUDED_PATTERNS = listOf(
            "playwright", "browser"
        )
    }
}
