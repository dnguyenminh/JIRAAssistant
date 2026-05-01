package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.ToolCategory
import kotlinx.serialization.json.*

/**
 * Scan tool definitions (start_scan, pause_scan, resume_scan, cancel_scan,
 * get_scan_status, get_scan_log).
 * Requirements: AC 6.77–6.82, AC 6.108
 */
object ScanToolDefs {

    fun all(): List<InternalToolDefinition> = listOf(
        startScan(), pauseScan(), resumeScan(),
        cancelScan(), getScanStatus(), getScanLog()
    )

    private fun startScan() = InternalToolDefinition(
        name = "start_scan",
        description = "Start a batch scan for a Jira project. " +
            "[Permission: ANALYZE_AI] [Role: Neural_Architect]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
                put("concurrency", intProp("Max concurrent fetches"))
                put("aiConcurrency", intProp("Max concurrent AI calls"))
                put("forceReanalyze", boolProp("Force re-analyze all tickets", false))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "ANALYZE_AI",
        requiredRole = "Neural_Architect",
        category = ToolCategory.SCAN
    )

    private fun pauseScan() = InternalToolDefinition(
        name = "pause_scan",
        description = "Pause a running scan. " +
            "[Permission: ANALYZE_AI] [Role: Neural_Architect]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "ANALYZE_AI",
        requiredRole = "Neural_Architect",
        category = ToolCategory.SCAN
    )

    private fun resumeScan() = InternalToolDefinition(
        name = "resume_scan",
        description = "Resume a paused scan. " +
            "[Permission: ANALYZE_AI] [Role: Neural_Architect]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "ANALYZE_AI",
        requiredRole = "Neural_Architect",
        category = ToolCategory.SCAN
    )

    private fun cancelScan() = InternalToolDefinition(
        name = "cancel_scan",
        description = "Cancel a running or paused scan. " +
            "[Permission: ANALYZE_AI] [Role: Neural_Architect]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "ANALYZE_AI",
        requiredRole = "Neural_Architect",
        category = ToolCategory.SCAN
    )

    private fun getScanStatus() = InternalToolDefinition(
        name = "get_scan_status",
        description = "Get current scan status for a project. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.SCAN
    )

    private fun getScanLog() = InternalToolDefinition(
        name = "get_scan_log",
        description = "Get scan log entries with pagination. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("projectKey", stringProp("Jira project key"))
                put("limit", intProp("Max entries to return", 50))
                put("offset", intProp("Offset for pagination", 0))
            },
            required = listOf("projectKey")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.SCAN
    )
}
