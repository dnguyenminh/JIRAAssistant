package com.assistant.chat

import kotlinx.serialization.Serializable

/**
 * Response chứa tool permissions per-user và global defaults.
 * Requirements: 3.2
 */
@Serializable
data class ToolPermissionsResponse(
    val permissions: Map<String, String>,  // "serverId::toolName" → "enabled"|"disabled"
    val defaults: Map<String, String>      // global defaults từ mcp_servers.auto_approve
)

/**
 * Request cập nhật tool permissions per-user.
 * Requirements: 3.3
 */
@Serializable
data class ToolPermissionsUpdateRequest(
    val permissions: Map<String, String>   // "serverId::toolName" → "enabled"|"disabled"
)

/**
 * Request bulk enable/disable tất cả tools của 1 server.
 * Requirements: 3.6
 */
@Serializable
data class ToolPermissionsBulkRequest(
    val serverId: String,
    val action: String  // "enable_all" | "disable_all"
)
