package com.assistant.server.chat

import com.assistant.ai.AIResult
import com.assistant.chat.ChatAction
import com.assistant.chat.ChatResponse
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.server.chat.models.SyncResult
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Agentic loop: after AI response, detect MCP tool calls, execute, feed result back.
 * Max 5 rounds. Graceful degradation on tool failure.
 * Requirements: 6.52, 6.53, 6.55, 17.2, 18.1, 18.5, 19.1, 19.4
 */
object McpAgenticLoop {

    private const val MAX_ROUNDS = 5
    private val logger = LoggerFactory.getLogger(McpAgenticLoop::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /** Run the agentic loop without graph sync (backward-compatible). */
    suspend fun execute(
        initialPrompt: String,
        mcpProcessManager: McpProcessManager?,
        callAI: suspend (String) -> AIResult,
        toResponse: (AIResult, Int) -> ChatResponse
    ): ChatResponse = execute(initialPrompt, mcpProcessManager, callAI, toResponse, null, null)

    /** Run the agentic loop with optional Jira/Confluence MCP sync. Req: 17.2, 18.1, 19.4 */
    suspend fun execute(
        initialPrompt: String,
        mcpProcessManager: McpProcessManager?,
        callAI: suspend (String) -> AIResult,
        toResponse: (AIResult, Int) -> ChatResponse,
        syncHandler: JiraMcpSyncHandler?,
        projectKey: String?,
        confluenceHandler: ConfluenceMcpSyncHandler? = null
    ): ChatResponse {
        val syncResults = mutableListOf<SyncResult>()
        val confluencePages = mutableListOf<ConfluencePage>()
        var prompt = initialPrompt
        for (round in 1..MAX_ROUNDS) {
            val aiResult = callAI(prompt)
            val responseText = extractText(aiResult)
            val toolCall = parseMcpToolCall(responseText)
            if (toolCall == null || mcpProcessManager == null) {
                val resp = toResponse(aiResult, prompt.length)
                return finalizeResponse(resp, syncResults, confluencePages)
            }
            val toolResult = executeTool(mcpProcessManager, toolCall)
            trySyncGraph(syncHandler, projectKey, toolCall, toolResult, syncResults)
            trySyncConfluence(confluenceHandler, projectKey, toolCall, toolResult, confluencePages)
            prompt = appendToolResult(prompt, responseText, toolCall, toolResult)
        }
        val finalResult = callAI(prompt)
        val resp = toResponse(finalResult, prompt.length)
        return finalizeResponse(resp, syncResults, confluencePages)
    }

    /** Combine sync warnings and Confluence openUrl actions into response. */
    private fun finalizeResponse(
        response: ChatResponse,
        syncResults: List<SyncResult>,
        confluencePages: List<ConfluencePage>
    ): ChatResponse {
        var result = appendSyncWarnings(response, syncResults)
        result = appendConfluenceActions(result, confluencePages)
        return result
    }

    /** Attempt graph sync after a Jira MCP tool call. Req: 18.1, 18.5 */
    private suspend fun trySyncGraph(
        handler: JiraMcpSyncHandler?, projectKey: String?,
        toolCall: McpToolCallRequest, toolResult: String,
        results: MutableList<SyncResult>
    ) {
        if (handler == null || projectKey == null) return
        if (!handler.isJiraTool(toolCall.toolName)) return
        if (toolResult.startsWith("Tool error") || toolResult.startsWith("Error:")) return
        val result = handler.syncAfterToolCall(projectKey, toolCall.toolName, toolResult)
        results.add(result)
    }

    /** Index Confluence pages after a Confluence MCP tool call. Req: 19.4 */
    private suspend fun trySyncConfluence(
        handler: ConfluenceMcpSyncHandler?, projectKey: String?,
        toolCall: McpToolCallRequest, toolResult: String,
        pages: MutableList<ConfluencePage>
    ) {
        if (handler == null || projectKey == null) return
        if (!handler.isConfluenceTool(toolCall.toolName)) return
        if (toolResult.startsWith("Tool error") || toolResult.startsWith("Error:")) return
        try {
            pages.addAll(handler.processToolResult(projectKey, toolResult))
        } catch (e: Exception) {
            logger.warn("Confluence sync failed: ${e.message}")
        }
    }

    /** Append openUrl actions for Confluence pages found. Req: 19.3 */
    internal fun appendConfluenceActions(
        response: ChatResponse, pages: List<ConfluencePage>
    ): ChatResponse {
        if (pages.isEmpty()) return response
        val newActions = pages.filter { it.url != null }.map { page ->
            ChatAction(
                type = "openUrl",
                label = "📄 ${page.title}",
                params = mapOf("url" to page.url!!)
            )
        }
        if (newActions.isEmpty()) return response
        val existing = response.actions
        return response.copy(actions = existing + newActions)
    }

    /** Append sync warning messages to the chat response. Req: 18.5 */
    internal fun appendSyncWarnings(
        response: ChatResponse, syncResults: List<SyncResult>
    ): ChatResponse {
        val warnings = syncResults.filter { !it.success }.mapNotNull { it.warningMessage }
        if (warnings.isEmpty()) return response
        val warningText = warnings.joinToString("\n⚠️ ")
        return response.copy(reply = "${response.reply}\n\n⚠️ $warningText")
    }

    /** Parse AI response for {"mcpToolCall": {...}} JSON block. Req: 6.53 */
    fun parseMcpToolCall(response: String): McpToolCallRequest? {
        val idx = response.indexOf("\"mcpToolCall\"")
        if (idx < 0) return null
        return try {
            val braceStart = response.lastIndexOf('{', idx)
            if (braceStart < 0) return null
            val outer = extractJsonObject(response, braceStart) ?: return null
            val parsed = json.parseToJsonElement(outer).jsonObject
            val inner = parsed["mcpToolCall"]?.jsonObject ?: return null
            json.decodeFromJsonElement<McpToolCallRequest>(inner)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractText(result: AIResult): String = when (result) {
        is AIResult.Success -> result.response
        is AIResult.Failure -> result.error
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

    /** Extract balanced JSON object starting at braceStart. */
    private fun extractJsonObject(text: String, start: Int): String? {
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }
}
