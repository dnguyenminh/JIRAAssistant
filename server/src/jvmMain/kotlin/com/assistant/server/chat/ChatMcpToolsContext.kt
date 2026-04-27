package com.assistant.server.chat

import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.server.mcp.internal.InternalMcpBridge
import kotlinx.coroutines.runBlocking

/**
 * Helper for building MCP tools context in system prompt with per-user filtering.
 * Requirements: 1.3, 6.4, 6.52, 6.53
 */
internal object ChatMcpToolsContext {

    /** Build full MCP tools context, filtering disabled tools per-user. Req: 1.3, 6.4 */
    fun build(
        internalMcpBridge: InternalMcpBridge?,
        mcpProcessManager: McpProcessManager?,
        localLines: List<String>,
        userId: String?,
        permService: UserToolPermissionService?
    ): String {
        val disabled = resolveDisabledTools(userId, permService)
        val internal = filterTools(internalMcpBridge?.getAggregatedTools(), disabled)
        val external = filterTools(mcpProcessManager?.getActiveTools(), disabled)
        return formatToolsPrompt(internal, external, localLines)
    }

    /** Resolve disabled tool keys for user. Req: 1.3, 6.4 */
    private fun resolveDisabledTools(userId: String?, svc: UserToolPermissionService?): Set<String> {
        if (userId == null || svc == null) return emptySet()
        return runBlocking { svc.getDisabledTools(userId) }
    }

    /** Filter tools by removing disabled ones. Req: 1.3, 6.4 */
    private fun filterTools(tools: List<McpAggregatedTool>?, disabled: Set<String>): List<McpAggregatedTool> {
        if (tools == null) return emptyList()
        if (disabled.isEmpty()) return tools
        return tools.filter { "${it.serverId}::${it.name}" !in disabled }
    }

    /** Format filtered tools into prompt string. Req: 6.52, 6.53 */
    private fun formatToolsPrompt(
        internal: List<McpAggregatedTool>,
        external: List<McpAggregatedTool>,
        localLines: List<String>
    ): String {
        val internalLines = internal.joinToString("\n") { "[Internal] ${it.name}: ${it.description}" }
        val externalLines = external.joinToString("\n") { "[MCP:${it.serverName}] ${it.name}: ${it.description}" }
        val allLines = listOf(internalLines, externalLines, localLines.joinToString("\n"))
            .filter { it.isNotBlank() }.joinToString("\n")
        val count = internal.size + external.size + localLines.size
        if (count == 0) return "Available MCP Tools: none (0 tools)"
        val instruction = buildInstruction(count)
        val confluenceHint = buildConfluenceHint(external)
        return "Available MCP Tools ($count):\n$allLines\n$instruction$confluenceHint"
    }

    private fun buildInstruction(count: Int): String =
        "You have $count MCP tools available. When user asks about tools or how many tools you have, " +
            "answer from this list directly — do NOT call any tool.\n" +
            """To use a tool, respond with JSON: {"mcpToolCall": {"serverId": "...", "toolName": "...", "arguments": {...}}}"""

    private fun buildConfluenceHint(tools: List<McpAggregatedTool>): String {
        val has = tools.any {
            it.name.contains("confluence", ignoreCase = true) ||
                it.serverName.contains("atlassian", ignoreCase = true)
        }
        if (!has) return ""
        return "\nConfluence tools are available. When user asks about documentation, " +
            "use Confluence search tools. Include openUrl actions for each page found."
    }
}
