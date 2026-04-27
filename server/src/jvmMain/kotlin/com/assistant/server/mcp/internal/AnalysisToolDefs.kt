package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.ToolCategory
import kotlinx.serialization.json.*

/**
 * Analysis tool definitions (analyze_ticket, get_ticket_analysis, list_analyzed_tickets).
 * Requirements: AC 6.83–6.85, AC 6.108
 */
object AnalysisToolDefs {

    fun all(): List<InternalToolDefinition> = listOf(
        analyzeTicket(),
        getTicketAnalysis(),
        listAnalyzedTickets()
    )

    private fun analyzeTicket() = InternalToolDefinition(
        name = "analyze_ticket",
        description = "Trigger AI analysis for a specific ticket. " +
            "[Permission: ANALYZE_AI] [Role: Neural_Architect]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("ticketId", stringProp("Jira ticket ID (e.g. PROJ-123)"))
                put("forceReanalyze", boolProp("Force re-analyze even if already analyzed", false))
            },
            required = listOf("ticketId")
        ),
        requiredPermission = "ANALYZE_AI",
        requiredRole = "Neural_Architect",
        category = ToolCategory.ANALYSIS
    )

    private fun getTicketAnalysis() = InternalToolDefinition(
        name = "get_ticket_analysis",
        description = "Get stored AI analysis result for a ticket. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("ticketId", stringProp("Jira ticket ID (e.g. PROJ-123)"))
            },
            required = listOf("ticketId")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.ANALYSIS
    )

    private fun listAnalyzedTickets() = InternalToolDefinition(
        name = "list_analyzed_tickets",
        description = "List tickets that have been analyzed in a project. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
                put("limit", intProp("Max tickets to return", 20))
                put("offset", intProp("Offset for pagination", 0))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.ANALYSIS
    )
}
