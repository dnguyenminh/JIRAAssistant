package com.assistant.frontend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    @SerialName("id") val userId: String = "",
    val email: String = "",
    @SerialName("name") val displayName: String = "",
    val role: String = "",
    @SerialName("customPermissions") val permissions: List<String> = emptyList(),
    val status: String = "ACTIVE",
    val createdAt: String = ""
)

@Serializable
data class AuditLogEntry(
    val timestamp: String = "",
    @SerialName("actorId") val actor: String = "",
    @SerialName("targetUserId") val target: String = "",
    val action: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val tag: String = ""
)

@Serializable
data class RoleChangeRequest(val role: String)

@Serializable
data class PermissionToggleRequest(
    val permission: String,
    val enabled: Boolean
)

data class PermToggle(val key: String, val label: String)

@Serializable
data class CreateUserRequest(
    val name: String,
    val email: String,
    val role: String
)

@Serializable
data class UpdateUserRequest(
    val name: String,
    val email: String
)

@Serializable
data class UpdateStatusRequest(
    val status: String
)
