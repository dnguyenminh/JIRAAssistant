package com.assistant.mcp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Category of an internal MCP tool.
 * Requirements: AC 6.108
 */
@Serializable
enum class ToolCategory {
    NAVIGATION,
    SCAN,
    ANALYSIS,
    CHAT,
    SETTINGS,
    USER_MANAGEMENT,
    INTEGRATIONS,
    KNOWLEDGE_GRAPH,
    DASHBOARD
}

/**
 * Definition of an internal MCP tool exposed by the built-in server.
 * Requirements: AC 6.108
 */
@Serializable
data class InternalToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonElement,
    val requiredPermission: String,
    val requiredRole: String,
    val category: ToolCategory
)
