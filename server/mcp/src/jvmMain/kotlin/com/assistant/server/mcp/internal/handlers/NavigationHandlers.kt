package com.assistant.server.mcp.internal.handlers

import com.assistant.auth.UserRole
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.rbac.Permission
import com.assistant.rbac.PermissionMatrix
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Navigation tool handlers — navigate_to_page, get_current_page, list_available_pages.
 * Requirements: AC 6.74, AC 6.75, AC 6.76
 */
class NavigationHandlers {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleNavigateToPage(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val page = args.str("page") ?: return missingField("page")
        val pageInfo = PAGE_REGISTRY[page]
            ?: return errorResponse("Page not found: $page")
        return textResponse(json.encodeToString(pageInfo))
    }

    suspend fun handleGetCurrentPage(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val result = buildJsonObject {
            put("page", "unknown")
            put("note", "Backend does not track frontend navigation state")
        }
        return textResponse(result.toString())
    }

    suspend fun handleListAvailablePages(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val role = safeParseRole(ctx.userRole)
        val permissions = PermissionMatrix.getPermissions(role)
        val filtered = PAGE_REGISTRY.values.filter { hasPagePermission(it, permissions) }
        return textResponse(json.encodeToString(filtered))
    }
}

private fun hasPagePermission(page: PageInfo, permissions: Set<Permission>): Boolean {
    if (page.requiredPermission == null) return true
    return try {
        permissions.contains(Permission.valueOf(page.requiredPermission))
    } catch (_: Exception) { true }
}

private fun safeParseRole(role: String): UserRole = try {
    UserRole.valueOf(role.uppercase())
} catch (_: Exception) { UserRole.READER }

@Serializable
data class PageInfo(
    val page: String,
    val route: String,
    val title: String,
    val description: String,
    val requiredPermission: String? = null
)

internal val PAGE_REGISTRY = listOf(
    PageInfo("dashboard", "#/dashboard", "Dashboard", "Project overview", "VIEW_DASHBOARD"),
    PageInfo("analysis", "#/analysis", "Ticket Intelligence", "AI ticket analysis", "VIEW_ANALYSIS"),
    PageInfo("graph", "#/graph", "Knowledge Graph", "Feature network visualization", "VIEW_GRAPH"),
    PageInfo("scan", "#/scan", "Batch Scan", "Batch ticket scanning", "ANALYZE_AI"),
    PageInfo("chat", "#/chat", "AI Chat", "AI assistant chat", "VIEW_ANALYSIS"),
    PageInfo("settings", "#/settings", "Settings", "Application settings", "MANAGE_SETTINGS"),
    PageInfo("users", "#/users", "User Management", "Manage users and roles", "MANAGE_USERS"),
    PageInfo("integrations", "#/integrations", "Integrations", "AI providers & MCP servers", "CONFIG_INTEGRATIONS")
).associateBy { it.page }
