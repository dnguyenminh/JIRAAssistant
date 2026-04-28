package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.server.mcp.internal.InternalMcpBridge
import com.assistant.server.chat.models.AIModelContext
import com.assistant.server.chat.models.SyncResult
import com.assistant.settings.SettingsRepository
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Agentic loop: after AI response, detect MCP tool calls, execute, feed result back.
 * Max 5 rounds. Graceful degradation on tool failure.
 * Requirements: 6.52, 6.53, 6.55, 17.2, 18.1, 18.5, 19.1, 19.4, 19.67–19.70
 */
object McpAgenticLoop {

    private const val MAX_ROUNDS = 5
    internal const val FINAL_ROUND_INSTRUCTION =
        "IMPORTANT: You have used all available tool rounds. You MUST now provide a final text answer " +
        "summarizing the information gathered. Do NOT call any more tools. Respond with a helpful text " +
        "reply in the same language as the user's question."
    private val logger = LoggerFactory.getLogger(McpAgenticLoop::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /** Run the agentic loop without graph sync (backward-compatible). Req: 19.67 */
    suspend fun execute(
        initialPrompt: String,
        mcpProcessManager: McpProcessManager?,
        callAI: suspend (String) -> AIResult,
        toResponse: (AIResult, Int) -> ChatResponse,
        localKBExecutor: LocalKBToolExecutor? = null,
        internalMcpBridge: InternalMcpBridge? = null
    ): ChatResponse = execute(
        initialPrompt, mcpProcessManager, callAI, toResponse,
        null, null, localKBExecutor = localKBExecutor,
        userId = null, permService = null,
        internalMcpBridge = internalMcpBridge
    )

    /** Run the agentic loop with optional Jira/Confluence MCP sync. Req: 17.2, 18.1, 19.4, 19.67, 19.94 */
    suspend fun execute(
        initialPrompt: String,
        mcpProcessManager: McpProcessManager?,
        callAI: suspend (String) -> AIResult,
        toResponse: (AIResult, Int) -> ChatResponse,
        syncHandler: JiraMcpSyncHandler?,
        projectKey: String?,
        confluenceHandler: ConfluenceMcpSyncHandler? = null,
        localKBExecutor: LocalKBToolExecutor? = null,
        modelCtx: AIModelContext? = null,
        settingsRepo: SettingsRepository? = null,
        userId: String? = null,
        permService: UserToolPermissionService? = null,
        internalMcpBridge: InternalMcpBridge? = null
    ): ChatResponse {
        val syncResults = mutableListOf<SyncResult>()
        val confluencePages = mutableListOf<ConfluencePage>()
        var prompt = initialPrompt
        for (round in 1..MAX_ROUNDS) {
            val aiResult = callAI(prompt)
            val responseText = extractText(aiResult)
            logger.info("[AgenticLoop] Round $round — AI response (first 500 chars): ${responseText.take(500)}")
            val toolCall = parseMcpToolCall(responseText, modelCtx, settingsRepo)
            logger.info("[AgenticLoop] Round $round — toolCall parsed: ${toolCall?.toolName ?: "NONE"} (serverId: ${toolCall?.serverId ?: "N/A"})")
            val canExecute = toolCall != null && (mcpProcessManager != null || isLocalKBCall(toolCall, localKBExecutor) || isInternalCall(toolCall, internalMcpBridge))
            if (toolCall == null || !canExecute) {
                logger.info("[AgenticLoop] Round $round — no executable tool call, returning response")
                val resp = toResponse(aiResult, prompt.length)
                return McpLoopSyncHelpers.finalizeResponse(resp, syncResults, confluencePages)
            }
            logger.info("[AgenticLoop] Round $round — executing tool: ${toolCall.toolName} on ${toolCall.serverId}")
            val toolResult = executeToolWithLocalRouting(mcpProcessManager, toolCall, localKBExecutor, userId, permService, internalMcpBridge)
            logger.info("[AgenticLoop] Round $round — tool result (first 300 chars): ${toolResult.take(300)}")
            McpLoopSyncHelpers.trySyncGraph(syncHandler, projectKey, toolCall, toolResult, syncResults)
            McpLoopSyncHelpers.trySyncConfluence(confluenceHandler, projectKey, toolCall, toolResult, confluencePages)
            prompt = appendToolResult(prompt, responseText, toolCall, toolResult)
        }
        prompt = "$prompt\n--- SYSTEM ---\n$FINAL_ROUND_INSTRUCTION"
        val finalResult = callAI(prompt)
        val resp = toResponse(finalResult, prompt.length)
        return McpLoopSyncHelpers.finalizeResponse(resp, syncResults, confluencePages)
    }

    /** Parse AI response for tool call — delegates to McpToolCallParser. Req: 6.53, 19.94 */
    fun parseMcpToolCall(
        response: String,
        modelCtx: AIModelContext? = null,
        settingsRepo: SettingsRepository? = null
    ): McpToolCallRequest? = McpToolCallParser.parse(response, modelCtx, settingsRepo)

    private fun extractText(result: AIResult): String = when (result) {
        is AIResult.Success -> result.response
        is AIResult.Failure -> result.error
    }

    private fun isLocalKBCall(req: McpToolCallRequest, executor: LocalKBToolExecutor?): Boolean =
        req.serverId == LocalKBToolExecutor.SERVER_ID && executor != null

    private fun isInternalCall(req: McpToolCallRequest, bridge: InternalMcpBridge?): Boolean =
        req.serverId == InternalMcpBridge.INTERNAL_SERVER_ID && bridge != null

    /** Route tool call: local KB in-process or external MCP. Req: 6.50, 19.67, 19.69, 1.2, 6.1 */
    private suspend fun executeToolWithLocalRouting(
        pm: McpProcessManager?, req: McpToolCallRequest,
        localKBExecutor: LocalKBToolExecutor?,
        userId: String? = null,
        permService: UserToolPermissionService? = null,
        bridge: InternalMcpBridge? = null
    ): String {
        // Per-user permission check. Req: 1.2, 6.1, 6.3
        if (isToolDisabledByUser(userId, permService, req)) {
            logger.info("[AgenticLoop] Tool '${req.toolName}' disabled by user '$userId', skipping")
            return "Tool '${req.toolName}' is disabled by user"
        }
        if (req.serverId == LocalKBToolExecutor.SERVER_ID && localKBExecutor != null) {
            return executeLocalKBTool(localKBExecutor, req)
        }
        // Internal tools bypass per-user check (RBAC handled by executor)
        if (req.serverId == InternalMcpBridge.INTERNAL_SERVER_ID) {
            return executeInternalTool(bridge, req, userId)
        }
        if (pm == null) return "Error: no MCP process manager available"
        return executeTool(pm, req)
    }

    /** Check if tool is disabled by per-user permission. Req: 1.2, 6.1 */
    private suspend fun isToolDisabledByUser(
        userId: String?, permService: UserToolPermissionService?, req: McpToolCallRequest
    ): Boolean {
        if (permService == null || userId == null) return false
        return !permService.isEnabled(userId, req.serverId, req.toolName)
    }

    /** Execute local KB tool in-process with error handling. Req: 19.67, 19.69 */
    private suspend fun executeLocalKBTool(executor: LocalKBToolExecutor, req: McpToolCallRequest): String = try {
        val args = req.arguments.mapValues { (_, v) ->
            v.jsonPrimitive.contentOrNull ?: v.toString()
        }
        executor.execute(req.toolName, args)
    } catch (e: Exception) {
        logger.warn("Local KB tool error: {}", e.message)
        "Tool error: ${e.message}"
    }

    /** Execute internal tool via InternalMcpBridge. Req: 1.1, 2.1, 6.71 */
    private suspend fun executeInternalTool(
        bridge: InternalMcpBridge?, req: McpToolCallRequest, userId: String?
    ): String {
        if (bridge == null) return "Error: no internal MCP bridge available"
        return try {
            val resp = bridge.callTool(req.toolName, req.arguments, userId ?: "system", "READER")
            if (resp.isError) "Tool error: ${resp.content.firstOrNull()?.text ?: "unknown"}"
            else resp.content.mapNotNull { it.text }.joinToString("\n")
        } catch (e: Exception) {
            logger.warn("Internal tool execution failed: {}", e.message)
            "Tool execution failed: ${e.message}"
        }
    }

    private suspend fun executeTool(
        pm: McpProcessManager, req: McpToolCallRequest
    ): String = try {
        val client = pm.getClient(req.serverId)
            ?: return "Error: server '${req.serverId}' not running"
        val resp = client.callTool(req.toolName, req.arguments)
        if (resp.isError) "Tool error: ${resp.content.firstOrNull()?.text ?: "unknown"}"
        else resp.content.mapNotNull { it.text }.joinToString("\n")
    } catch (e: Exception) {
        logger.warn("Tool execution failed: {}", e.message)
        "Tool execution failed: ${e.message}"
    }

    private fun appendToolResult(
        prompt: String, aiReply: String,
        call: McpToolCallRequest, result: String
    ): String = buildString {
        append(prompt)
        append("\n--- AI ---\n").append(aiReply)
        append("\n--- TOOL RESULT [${call.toolName}] ---\n").append(result)
        append("\n--- USER ---\nPlease incorporate the tool result above.")
    }
}
