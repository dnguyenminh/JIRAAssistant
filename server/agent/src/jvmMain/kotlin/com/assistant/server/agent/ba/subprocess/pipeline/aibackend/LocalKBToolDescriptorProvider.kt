package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.server.chat.LocalKBToolExecutor

/**
 * Provides Local KB tool descriptors with KB-compatible names
 * for BRD pipeline tool collection.
 *
 * Names use `kb_` prefix so PhaseToolFilter.hasKbTools() detects
 * them via existing patterns (kb_search, kb_ingest, kb_write).
 *
 * Requirements: 2.1, 2.2
 */
object LocalKBToolDescriptorProvider {

    private const val SERVER = LocalKBToolExecutor.SERVER_ID

    private val ALIAS_MAP = mapOf(
        "kb_search_knowledge" to "search_knowledge",
        "kb_get_ticket_info" to "get_ticket_info",
        "kb_search_relationships" to "search_relationships",
        "kb_ingest_knowledge" to "ingest_knowledge"
    )

    /** Returns 4 Local KB tool descriptors with KB-compatible names. */
    fun getDescriptors(): List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = "mcp_${SERVER}_kb_search_knowledge",
            description = "Semantic search in Local Knowledge Base"
        ),
        ToolDescriptor(
            name = "mcp_${SERVER}_kb_get_ticket_info",
            description = "Lookup ticket analysis from Local KB"
        ),
        ToolDescriptor(
            name = "mcp_${SERVER}_kb_search_relationships",
            description = "Search ticket relationships in Local KB"
        ),
        ToolDescriptor(
            name = "mcp_${SERVER}_kb_ingest_knowledge",
            description = "Ingest text content into Local KB for cross-phase data sharing"
        )
    )

    /**
     * Maps KB-aliased tool name back to original name
     * for LocalKBToolExecutor.execute().
     * E.g. "kb_search_knowledge" → "search_knowledge"
     */
    fun mapAliasToOriginal(aliasName: String): String {
        return ALIAS_MAP[aliasName] ?: aliasName
    }
}
