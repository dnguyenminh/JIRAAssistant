package com.assistant.rbac

import com.assistant.auth.UserRole

/**
 * RBAC Engine interface for role-based access control.
 * Checks permissions against the PermissionMatrix and manages role/permission changes.
 */
interface RBACEngine {
    fun hasPermission(role: UserRole, permission: Permission): Boolean
    fun getPermissions(role: UserRole): Set<Permission>
    suspend fun changeRole(adminId: String, targetUserId: String, newRole: UserRole): RBACResult
    suspend fun togglePermission(adminId: String, targetUserId: String, permission: Permission, enabled: Boolean): RBACResult
}
