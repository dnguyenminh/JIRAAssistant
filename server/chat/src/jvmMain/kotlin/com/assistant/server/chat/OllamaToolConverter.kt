package com.assistant.server.chat

import com.assistant.ai.models.OllamaChatFunctionDef
import com.assistant.ai.models.OllamaChatToolDef
import com.assistant.mcp.McpProcessManager
import com.assistant.mcp.models.McpAggregatedTool
import com.assistant.server.mcp.internal.InternalMcpBridge
import kotlinx.serialization.json.*

/**
 * Converts MCP tool definitions (internal, external, local KB) into
 * Ollama /api/chat tool format for native tool calling.
 * Requirements: 6.52, 6.53
 */
object OllamaToolConverter {

    /**
     * Build complete list of Ollama tool defs from all sources.
     * Each tool name is prefixed with serverId for routing.
     */
    fun buildToolDefs(
        internalBridge: InternalMcpBridge?,
        mcpProcessManager: McpProcessManager?,
        localKBEnabled: Boolean,
        userId: String?,
        permService: UserToolPermissionService?
    ): List<OllamaChatToolDef> {
        val disabled = resolveDisabled(userId, permService)
        val tools = mutableListOf<OllamaChatToolDef>()
        tools.addAll(convertInternal(internalBridge, disabled))
        tools.addAll(convertExternal(mcpProcessManager, disabled))
        if (localKBEnabled) tools.addAll(buildLocalKBTools())
        return tools
    }

    /** Convert internal MCP tools to Ollama format. */
    private fun convertInternal(
        bridge: InternalMcpBridge?, disabled: Set<String>
    ): List<OllamaChatToolDef> {
        val tools = bridge?.getAggregatedTools() ?: return emptyList()
        return tools.filterDisabled(disabled).map { it.toOllamaDef() }
    }

    /** Convert external MCP tools to Ollama format. */
    private fun convertExternal(
        pm: McpProcessManager?, disabled: Set<String>
    ): List<OllamaChatToolDef> {
        val tools = pm?.getActiveTools() ?: return emptyList()
        return tools.filterDisabled(disabled).map { it.toOllamaDef() }
    }

    /** Build local KB tool definitions in Ollama format. */
    private fun buildLocalKBTools(): List<OllamaChatToolDef> = listOf(
        buildLocalKBTool("search_knowledge", "Semantic search in KB",
            mapOf("query" to propString("Search query")),
            listOf("query")),
        buildLocalKBTool("get_ticket_info", "Get ticket analysis from KB",
            mapOf("ticketId" to propString("Ticket ID, e.g. ICL2-339")),
            listOf("ticketId")),
        buildLocalKBTool("search_relationships", "Find ticket relationships",
            mapOf("query" to propString("Relationship query")),
            listOf("query")),
        buildLocalKBTool("ingest_knowledge", "Ingest content into KB",
            mapOf(
                "title" to propString("Content title"),
                "content" to propString("Content body"),
                "ticketId" to propString("Optional ticket ID")
            ), listOf("title", "content"))
    )

    private fun buildLocalKBTool(
        name: String, desc: String,
        props: Map<String, JsonElement>, required: List<String>
    ) = OllamaChatToolDef(
        function = OllamaChatFunctionDef(
            name = "local-knowledge-base__$name",
            description = desc,
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
                putJsonArray("required") { required.forEach { add(it) } }
            }
        )
    )

    private fun propString(desc: String) = buildJsonObject {
        put("type", "string"); put("description", desc)
    }

    private fun resolveDisabled(
        userId: String?, svc: UserToolPermissionService?
    ): Set<String> {
        if (userId == null || svc == null) return emptySet()
        return kotlinx.coroutines.runBlocking { svc.getDisabledTools(userId) }
    }
}

/** Extension: filter out disabled tools. */
private fun List<McpAggregatedTool>.filterDisabled(
    disabled: Set<String>
): List<McpAggregatedTool> {
    if (disabled.isEmpty()) return this
    return filter { "${it.serverId}::${it.name}" !in disabled }
}

/** Extension: convert McpAggregatedTool → OllamaChatToolDef. */
private fun McpAggregatedTool.toOllamaDef() = OllamaChatToolDef(
    function = OllamaChatFunctionDef(
        name = "${serverId}__$name",
        description = description,
        parameters = inputSchema
    )
)
