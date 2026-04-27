package com.assistant.server.chat

import com.assistant.chat.ChatAction
import com.assistant.chat.ChatResponse
import com.assistant.mcp.models.McpToolCallRequest
import com.assistant.server.chat.models.SyncResult
import org.slf4j.LoggerFactory

/**
 * Sync helpers for McpAgenticLoop: Jira graph sync, Confluence indexing, response finalization.
 * Extracted to keep McpAgenticLoop ≤ 200 lines.
 * Requirements: 18.1, 18.5, 19.3, 19.4
 */
internal object McpLoopSyncHelpers {

    private val logger = LoggerFactory.getLogger(McpLoopSyncHelpers::class.java)

    /** Combine sync warnings and Confluence openUrl actions into response. */
    fun finalizeResponse(
        response: ChatResponse,
        syncResults: List<SyncResult>,
        confluencePages: List<ConfluencePage>
    ): ChatResponse {
        var result = appendSyncWarnings(response, syncResults)
        result = appendConfluenceActions(result, confluencePages)
        return result
    }

    /** Attempt graph sync after a Jira MCP tool call. Req: 18.1, 18.5 */
    suspend fun trySyncGraph(
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
    suspend fun trySyncConfluence(
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
    fun appendConfluenceActions(
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
        return response.copy(actions = response.actions + newActions)
    }

    /** Append sync warning messages to the chat response. Req: 18.5 */
    fun appendSyncWarnings(
        response: ChatResponse, syncResults: List<SyncResult>
    ): ChatResponse {
        val warnings = syncResults.filter { !it.success }.mapNotNull { it.warningMessage }
        if (warnings.isEmpty()) return response
        val warningText = warnings.joinToString("\n⚠️ ")
        return response.copy(reply = "${response.reply}\n\n⚠️ $warningText")
    }
}
