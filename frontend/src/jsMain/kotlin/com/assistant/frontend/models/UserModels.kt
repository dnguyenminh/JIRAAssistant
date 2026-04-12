package com.assistant.frontend.models

import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "",
    val permissions: List<String> = emptyList()
)

@Serializable
data class AuditLogEntry(
    val timestamp: String = "",
    val actor: String = "",
    val target: String = "",
    val action: String = "",
    val oldValue: String = "",
    val newValue: String = ""
)

@Serializable
data class RoleChangeRequest(val role: String)

@Serializable
data class PermissionToggleRequest(
    val permission: String,
    val enabled: Boolean
)

data class PermToggle(val key: String, val label: String)
