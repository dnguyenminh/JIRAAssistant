package com.assistant.server.mcp.internal.handlers

import com.assistant.ai.AIOrchestrator
import com.assistant.kb.KBRepository
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.json.*

/**
 * Analysis tool handlers — analyze_ticket, get_ticket_analysis, list_analyzed_tickets.
 * Requirements: AC 6.83–6.85, AC 6.110
 */
class AnalysisHandlers(
    private val aiOrchestrator: AIOrchestrator,
    private val kbRepository: KBRepository
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleAnalyzeTicket(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val ticketId = args.str("ticketId") ?: return missingField("ticketId")
        val force = args.boolOrNull("forceReanalyze") ?: false
        return try {
            val result = aiOrchestrator.analyzeTicket(ticketId, force)
            textResponse(json.encodeToString(result))
        } catch (e: Exception) {
            errorResponse("Analysis failed for $ticketId: ${e.message}")
        }
    }

    suspend fun handleGetTicketAnalysis(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val ticketId = args.str("ticketId") ?: return missingField("ticketId")
        val record = kbRepository.findByTicketId(ticketId)
            ?: return errorResponse("No analysis found for ticket: $ticketId")
        return textResponse(json.encodeToString(record))
    }

    suspend fun handleListAnalyzedTickets(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val graph = kbRepository.getGraphData(projectKey)
        val ticketIds = graph?.nodes?.map { it.key } ?: emptyList()
        val result = buildJsonObject {
            put("projectKey", projectKey)
            put("ticketIds", JsonArray(ticketIds.map { JsonPrimitive(it) }))
            put("count", ticketIds.size)
        }
        return textResponse(result.toString())
    }
}
