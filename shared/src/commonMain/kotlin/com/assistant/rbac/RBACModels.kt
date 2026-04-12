package com.assistant.rbac

import kotlinx.serialization.Serializable

@Serializable
enum class Permission {
    VIEW_DASHBOARD, VIEW_GRAPH, VIEW_ANALYSIS,
    ANALYZE_AI, VIEW_KB, RE_ANALYZE,
    CONFIG_INTEGRATIONS, TEST_PROVIDER,
    MANAGE_USERS, TOGGLE_PERMISSIONS, SIGN_OUT,
    MANAGE_SETTINGS
}

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: com.assistant.auth.UserRole,
    val avatarUrl: String? = null,
    val customPermissions: Set<Permission> = emptySet()
)

@Serializable
data class AuditLogEntry(
    val timestamp: String,
    val actorId: String,
    val targetUserId: String,
    val action: String,
    val oldValue: String,
    val newValue: String,
    val tag: String
)

sealed class RBACResult {
    data class Success(val message: String) : RBACResult()
    data class Failure(val code: Int, val message: String) : RBACResult()
}
