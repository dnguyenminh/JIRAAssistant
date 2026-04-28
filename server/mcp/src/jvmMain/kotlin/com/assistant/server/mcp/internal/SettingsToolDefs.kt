package com.assistant.server.mcp.internal

import com.assistant.mcp.models.InternalToolDefinition
import com.assistant.mcp.models.ToolCategory
import kotlinx.serialization.json.*

/**
 * Settings + User Management tool definitions.
 * Requirements: AC 6.89–6.94, AC 6.108
 */
object SettingsToolDefs {

    private val roleEnum = listOf("Reader", "Neural_Architect", "Administrator")

    fun all(): List<InternalToolDefinition> = settingsTools() + userMgmtTools()

    private fun settingsTools() = listOf(
        getSettings(), updateSetting(), getSetting()
    )

    private fun userMgmtTools() = listOf(
        listUsers(), updateUserRole(), getUserPermissions()
    )

    private fun getSettings() = InternalToolDefinition(
        name = "get_settings",
        description = "Get all application settings. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.SETTINGS
    )

    private fun updateSetting() = InternalToolDefinition(
        name = "update_setting",
        description = "Update an application setting value. " +
            "[Permission: MANAGE_SETTINGS] [Role: Administrator]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("key", stringProp("Setting key"))
                put("value", stringProp("New setting value"))
            },
            required = listOf("key", "value")
        ),
        requiredPermission = "MANAGE_SETTINGS",
        requiredRole = "Administrator",
        category = ToolCategory.SETTINGS
    )

    private fun getSetting() = InternalToolDefinition(
        name = "get_setting",
        description = "Get a specific application setting by key. " +
            "[Permission: VIEW_ANALYSIS] [Role: Reader]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("key", stringProp("Setting key"))
            },
            required = listOf("key")
        ),
        requiredPermission = "VIEW_ANALYSIS",
        requiredRole = "Reader",
        category = ToolCategory.SETTINGS
    )

    private fun listUsers() = InternalToolDefinition(
        name = "list_users",
        description = "List all registered users. " +
            "[Permission: MANAGE_USERS] [Role: Administrator]",
        inputSchema = buildSchema(),
        requiredPermission = "MANAGE_USERS",
        requiredRole = "Administrator",
        category = ToolCategory.USER_MANAGEMENT
    )

    private fun updateUserRole() = InternalToolDefinition(
        name = "update_user_role",
        description = "Update a user's role. " +
            "[Permission: MANAGE_USERS] [Role: Administrator]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("userId", stringProp("User ID"))
                put("role", enumProp("New role for the user", roleEnum))
            },
            required = listOf("userId", "role")
        ),
        requiredPermission = "MANAGE_USERS",
        requiredRole = "Administrator",
        category = ToolCategory.USER_MANAGEMENT
    )

    private fun getUserPermissions() = InternalToolDefinition(
        name = "get_user_permissions",
        description = "Get permissions for a specific user based on their role. " +
            "[Permission: MANAGE_USERS] [Role: Administrator]",
        inputSchema = buildSchema(
            properties = buildJsonObject {
                put("userId", stringProp("User ID"))
            },
            required = listOf("userId")
        ),
        requiredPermission = "MANAGE_USERS",
        requiredRole = "Administrator",
        category = ToolCategory.USER_MANAGEMENT
    )
}
