package com.assistant.server.mcp.internal.handlers

import com.assistant.kb.KBRepository
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.scan.BatchScanEngine
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.json.*

/**
 * Dashboard tool handlers — get_dashboard_metrics, list_projects, get_project_analysis_summary.
 * Requirements: AC 6.101–6.103
 */
class DashboardHandlers(
    private val kbRepository: KBRepository,
    private val batchScanEngine: BatchScanEngine
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleGetDashboardMetrics(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val graph = kbRepository.getGraphData(projectKey)
        val scanState = batchScanEngine.getStatus(projectKey)
        val result = buildJsonObject {
            put("projectKey", projectKey)
            put("totalTickets", graph?.nodes?.size ?: 0)
            put("totalEdges", graph?.edges?.size ?: 0)
            put("scanStatus", scanState.status.name)
            put("scanProgress", scanState.progressPercent)
        }
        return textResponse(result.toString())
    }

    suspend fun handleListProjects(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val activeScans = batchScanEngine.getActiveScans()
        val projectKeys = activeScans.map { it.projectKey }.distinct()
        val result = buildJsonObject {
            put("projects", JsonArray(projectKeys.map { JsonPrimitive(it) }))
            put("count", projectKeys.size)
        }
        return textResponse(result.toString())
    }

    suspend fun handleGetProjectAnalysisSummary(
        args: JsonObject,
        ctx: UserContext
    ): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val graph = kbRepository.getGraphData(projectKey)
        val scanState = batchScanEngine.getStatus(projectKey)
        val result = buildJsonObject {
            put("projectKey", projectKey)
            put("analyzedTickets", graph?.nodes?.size ?: 0)
            put("relationships", graph?.edges?.size ?: 0)
            put("scanStatus", scanState.status.name)
            put("processedCount", scanState.processedCount)
            put("totalTickets", scanState.totalTickets)
        }
        return textResponse(result.toString())
    }
}
