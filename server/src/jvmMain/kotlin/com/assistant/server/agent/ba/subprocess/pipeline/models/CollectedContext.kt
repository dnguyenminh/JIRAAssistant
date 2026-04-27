package com.assistant.server.agent.ba.subprocess.pipeline.models

import com.assistant.agent.ba.models.ToolCallLogEntry

/**
 * Aggregated data collected from all 4 MCP tool calls
 * during the data collection phase of the pipeline.
 *
 * Each field represents the outcome of one MCP tool call.
 * [toolCallLog] provides an ordered log compatible with [BATaskResult].
 */
data class CollectedContext(
    val rootTicketData: ToolCallOutcome,
    val linkedTicketsData: ToolCallOutcome,
    val kbAnalysisData: ToolCallOutcome,
    val dependenciesData: ToolCallOutcome,
    val toolCallLog: List<ToolCallLogEntry>
) {
    /** Number of MCP tool calls that completed successfully. */
    val successCount: Int get() = listOf(
        rootTicketData, linkedTicketsData,
        kbAnalysisData, dependenciesData
    ).count { it.success }
}
