package com.assistant.server.mcp.internal.handlers

import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.scan.BatchScanEngine
import com.assistant.scan.ScanConflictException
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.json.*

/**
 * Scan control tool handlers — start/pause/resume/cancel scan, status, log.
 * Requirements: AC 6.77–6.82, AC 6.110
 */
class ScanHandlers(private val batchScanEngine: BatchScanEngine) {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleStartScan(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val concurrency = args.intOrNull("concurrency")
        val aiConcurrency = args.intOrNull("aiConcurrency")
        val force = args.boolOrNull("forceReanalyze") ?: false
        return try {
            val state = batchScanEngine.startScan(projectKey, concurrency, aiConcurrency, force)
            textResponse(json.encodeToString(state))
        } catch (e: ScanConflictException) {
            errorResponse("Scan conflict: ${e.message}")
        }
    }

    suspend fun handlePauseScan(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val state = batchScanEngine.pauseScan(projectKey)
        return textResponse(json.encodeToString(state))
    }

    suspend fun handleResumeScan(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        return try {
            val state = batchScanEngine.resumeScan(projectKey)
            textResponse(json.encodeToString(state))
        } catch (e: IllegalStateException) {
            errorResponse("Cannot resume: ${e.message}")
        }
    }

    suspend fun handleCancelScan(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val state = batchScanEngine.cancelScan(projectKey)
        return textResponse(json.encodeToString(state))
    }

    suspend fun handleGetScanStatus(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val state = batchScanEngine.getStatus(projectKey)
        return textResponse(json.encodeToString(state))
    }

    suspend fun handleGetScanLog(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val projectKey = args.str("projectKey") ?: return missingField("projectKey")
        val limit = args.intOrNull("limit") ?: 50
        val entries = batchScanEngine.getLog(projectKey, limit)
        return textResponse(json.encodeToString(entries))
    }
}
