package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.ToolCategory
import kotlinx.serialization.json.*

/**
 * Navigation tool definitions (navigate_to_page, get_current_page, list_available_pages).
 * Requirements: AC 6.74, AC 6.75, AC 6.76, AC 6.108
 */
object NavigationToolDefs {

    private val pageEnum = listOf(
        "dashboard", "analysis", "ticket_intelligence",
        "knowledge_graph", "integrations", "settings", "user_management"
    )

    fun all(): List<InternalToolDefinition> = listOf(
        navigateToPage(),
        getCurrentPage(),
        listAvailablePages()
    )

    private fun navigateToPage() = InternalToolDefinition(
        name = "navigate_to_page",
        description = "Navigate to a specific application page. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("page", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Target page to navigate to"))
                    put("enum", JsonArray(pageEnum.map { JsonPrimitive(it) }))
                })
            },
            required = listOf("page")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.NAVIGATION
    )

    private fun getCurrentPage() = InternalToolDefinition(
        name = "get_current_page",
        description = "Get the currently active page on the frontend. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.NAVIGATION
    )

    private fun listAvailablePages() = InternalToolDefinition(
        name = "list_available_pages",
        description = "List all pages the current user has permission to access. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.NAVIGATION
    )
}
