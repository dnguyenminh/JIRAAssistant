package com.assistant.server.chat

import com.assistant.settings.SettingsRepository
import kotlinx.coroutines.runBlocking

/**
 * Local KB Tool context helpers for ChatServiceImpl.
 * Extracted for file size compliance.
 * Requirements: 19.61, 19.71, 19.72, 19.73, 19.76
 */
internal object ChatLocalKBContext {

    /** Check if Local KB Tool is enabled via settings. Default: enabled. Req: 19.76 */
    fun isEnabled(settingsRepository: SettingsRepository?): Boolean {
        val repo = settingsRepository ?: return false
        val value = runBlocking { repo.get("local_kb_tool_enabled") }
        return value != "false"
    }

    /** Build tool descriptions for 4 local KB operations. Req: 19.61, 19.72 */
    fun buildToolsContext(enabled: Boolean): List<String> {
        if (!enabled) return emptyList()
        return listOf(
            "[MCP:local-knowledge-base] search_knowledge: Semantic search trong KB — dùng khi user hỏi tổng quát về tickets, features, requirements. Params: query (bắt buộc), chunkType? (TICKET|ATTACHMENT|CONFLUENCE|ANALYSIS|EVOLUTION|RELATIONSHIP|CLUSTER), topK? (default 10)",
            "[MCP:local-knowledge-base] get_ticket_info: Tra cứu chi tiết phân tích ticket — dùng khi user hỏi về 1 ticket cụ thể (ví dụ ICL2-339). Params: ticketId (bắt buộc, ví dụ \"ICL2-339\")",
            "[MCP:local-knowledge-base] search_relationships: Tìm mối quan hệ/dependency giữa tickets — dùng khi user hỏi về liên kết, phụ thuộc. Params: query (bắt buộc)",
            "[MCP:local-knowledge-base] ingest_knowledge: Ghi nội dung vào KB — dùng để cache data cho cross-phase sharing. Params: title (bắt buộc), content (bắt buộc), ticketId? (optional)"
        )
    }

    /** Build priority guidance for local KB tools. Req: 19.71 */
    fun buildPriorityHint(enabled: Boolean): String {
        if (!enabled) return ""
        return "\nLOCAL KB TOOLS — USE FIRST (faster than external Jira tools):" +
            "\nTo look up ticket ICL2-116, respond with EXACTLY:" +
            "\n{\"mcpToolCall\":{\"serverId\":\"local-knowledge-base\",\"toolName\":\"get_ticket_info\",\"arguments\":{\"ticketId\":\"ICL2-116\"}}}" +
            "\nTo search KB, respond with EXACTLY:" +
            "\n{\"mcpToolCall\":{\"serverId\":\"local-knowledge-base\",\"toolName\":\"search_knowledge\",\"arguments\":{\"query\":\"your search query\"}}}" +
            "\nTo find relationships, respond with EXACTLY:" +
            "\n{\"mcpToolCall\":{\"serverId\":\"local-knowledge-base\",\"toolName\":\"search_relationships\",\"arguments\":{\"query\":\"your query\"}}}" +
            "\nDO NOT invent tool names. ONLY use: get_ticket_info, search_knowledge, search_relationships, ingest_knowledge."
    }
}
