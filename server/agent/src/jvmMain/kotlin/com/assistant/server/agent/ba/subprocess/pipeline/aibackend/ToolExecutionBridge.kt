package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.ba.models.ToolCallLogEntry
import com.assistant.agent.progress.ProgressReporter
import com.assistant.agent.subprocess.SubprocessProxy
import com.assistant.agent.subprocess.ToolCallRequest
import com.assistant.agent.subprocess.ToolCallResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolBridgeResult
import com.assistant.server.agent.ba.subprocess.pipeline.aibackend.models.ToolRequest
import com.assistant.server.chat.LocalKBToolExecutor
import com.assistant.server.mcp.internal.InternalMcpBridge
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Bridges tool calls to SubprocessProxy, McpProcessManager, or InternalMcpBridge.
 */
class ToolExecutionBridge(
    private val subprocessProxy: SubprocessProxy,
    private val progressReporter: ProgressReporter,
    private val mcpProcessManager: McpProcessManager? = null,
    private val internalMcpBridge: InternalMcpBridge? = null,
    private val localKBToolExecutor: LocalKBToolExecutor? = null
) {

    private val log = LoggerFactory.getLogger(ToolExecutionBridge::class.java)

    suspend fun execute(toolRequest: ToolRequest): ToolBridgeResult {
        log.info("Executing tool: {}", toolRequest.tool)
        val startTime = System.currentTimeMillis()

        val response = if (shouldRouteThroughMcp(toolRequest)) {
            executeMcpTool(toolRequest)
        } else {
            val callRequest = toToolCallRequest(toolRequest)
            subprocessProxy.handleToolCallRequest(callRequest)
        }
        val durationMs = System.currentTimeMillis() - startTime

        val formatted = formatToolResult(
            toolRequest.tool, response.success, response.data, response.error
        )
        reportToolCall(toolRequest.tool, response.success)
        val logEntry = buildLogEntry(
            toolRequest.tool, durationMs, response
        )

        log.debug(
            "Tool '{}' completed in {}ms, success={}",
            toolRequest.tool, durationMs, response.success
        )
        return ToolBridgeResult(formatted, logEntry, response)
    }

    private fun shouldRouteThroughMcp(request: ToolRequest): Boolean {
        return mcpProcessManager != null && request.tool.startsWith("mcp_")
    }

    private suspend fun executeMcpTool(request: ToolRequest): ToolCallResponse {
        val id = UUID.randomUUID().toString()
        // Parse tool name: mcp_{serverName}_{toolName}
        val parts = request.tool.removePrefix("mcp_").split("_", limit = 2)
        if (parts.size < 2) {
            return ToolCallResponse(id, false, "", "Invalid MCP tool name: ${request.tool}")
        }
        val serverName = parts[0]
        val toolName = parts[1]

        // Check internal tools first (Jira Assistant UI, 30 tools)
        val internalTools = internalMcpBridge?.getAggregatedTools() ?: emptyList()
        val internalMatch = internalTools.firstOrNull {
            request.tool == "mcp_${it.serverName}_${it.name}"
        }
        if (internalMatch != null && internalMcpBridge != null) {
            return try {
                val jsonArgs = JsonObject(request.params.mapValues { JsonPrimitive(it.value) })
                val response = internalMcpBridge.callTool(
                    internalMatch.name, jsonArgs,
                    userId = "ba-agent", userRole = "ADMINISTRATOR"
                )
                val text = response.content.joinToString("\n") { it.text ?: "" }
                ToolCallResponse(id, !response.isError, text, if (response.isError) text else "")
            } catch (e: Exception) {
                log.error("Internal MCP tool call failed: {}", e.message)
                ToolCallResponse(id, false, "", e.message ?: "Internal MCP call failed")
            }
        }

        // Check Local KB tools (server: local-knowledge-base)
        if (serverName == LocalKBToolExecutor.SERVER_ID && localKBToolExecutor != null) {
            return executeLocalKBTool(id, toolName, request.params)
        }

        // Then check external tools (from McpProcessManager/database)
        val activeTools = mcpProcessManager?.getActiveTools() ?: emptyList()
        val matchingTool = activeTools.firstOrNull {
            request.tool == "mcp_${it.serverName}_${it.name}"
        }

        if (matchingTool == null) {
            return ToolCallResponse(id, false, "", "MCP tool not found: ${request.tool}")
        }

        val mgr = mcpProcessManager!!
        val client = mgr.getClient(matchingTool.serverId)
            ?: run {
                // MCP server may still be starting — retry once after delay
                log.warn("MCP client not ready for '{}', retrying in 3s...", matchingTool.serverName)
                kotlinx.coroutines.delay(3000)
                mgr.getClient(matchingTool.serverId)
            }
            ?: return ToolCallResponse(id, false, "", "No MCP client for server '${matchingTool.serverName}' — server may not be ready yet")

        return try {
            val jsonArgs = JsonObject(request.params.mapValues { JsonPrimitive(it.value) })
            val response = client.callTool(matchingTool.name, jsonArgs)
            val text = response.content.joinToString("\n") { it.text ?: "" }
            ToolCallResponse(id, !response.isError, text, if (response.isError) text else "")
        } catch (e: Exception) {
            log.error("MCP tool call failed: {}", e.message)
            ToolCallResponse(id, false, "", e.message ?: "MCP call failed")
        }
    }

    /** Route Local KB tool call to LocalKBToolExecutor. */
    private suspend fun executeLocalKBTool(
        id: String, toolName: String, params: Map<String, String>
    ): ToolCallResponse {
        val originalName = LocalKBToolDescriptorProvider.mapAliasToOriginal(toolName)
        return try {
            val result = localKBToolExecutor!!.execute(originalName, params)
            ToolCallResponse(id, true, result, "")
        } catch (e: Exception) {
            log.error("Local KB tool call failed: {}", e.message)
            ToolCallResponse(id, false, "", e.message ?: "Local KB call failed")
        }
    }

    private fun toToolCallRequest(request: ToolRequest): ToolCallRequest {
        return ToolCallRequest(
            id = UUID.randomUUID().toString(),
            name = request.tool,
            arguments = request.params
        )
    }

    private suspend fun reportToolCall(toolName: String, success: Boolean) {
        val status = if (success) "success" else "failed"
        progressReporter.reportToolCall(toolName, status)
    }

    private fun buildLogEntry(
        toolName: String,
        durationMs: Long,
        response: ToolCallResponse
    ): ToolCallLogEntry {
        val resultSize = if (response.success) {
            response.data.length
        } else {
            response.error.length
        }
        return ToolCallLogEntry(
            toolName = toolName,
            durationMs = durationMs,
            success = response.success,
            resultSizeChars = resultSize
        )
    }

    companion object {
        /**
         * Formats a tool result as POC protocol JSON.
         *
         * Output: `{"type":"tool_result","tool":"...","success":true/false,"data":"...","error":"..."}`
         */
        fun formatToolResult(
            tool: String,
            success: Boolean,
            data: String,
            error: String
        ): String {
            val escapedTool = escapeJson(tool)
            val escapedData = escapeJson(data)
            val escapedError = escapeJson(error)
            return buildString {
                append("{\"type\":\"tool_result\"")
                append(",\"tool\":\"$escapedTool\"")
                append(",\"success\":$success")
                append(",\"data\":\"$escapedData\"")
                append(",\"error\":\"$escapedError\"")
                append("}")
            }
        }

        private fun escapeJson(s: String): String =
            s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
    }
}
