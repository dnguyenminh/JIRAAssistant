package com.assistant.rbac

import com.assistant.auth.UserRole
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * RBAC Engine implementation.
 * Uses PermissionMatrix for permission checks and logs all changes to AuditLogStore.
 */
class RBACEngineImpl(
    private val userStore: UserStore,
    private val auditLogStore: AuditLogStore
) : RBACEngine {

    override fun hasPermission(role: UserRole, permission: Permission): Boolean =
        PermissionMatrix.check(role, permission)

    override fun getPermissions(role: UserRole): Set<Permission> =
        PermissionMatrix.getPermissions(role)

    override suspend fun changeRole(
        adminId: String,
        targetUserId: String,
        newRole: UserRole
    ): RBACResult {
        val targetUser = userStore.findById(targetUserId)
            ?: return RBACResult.Failure(404, "User not found: $targetUserId")

        val oldRole = targetUser.role
        if (oldRole == newRole) {
            return RBACResult.Success("Role unchanged")
        }

        val updated = userStore.updateRole(targetUserId, newRole)
        if (!updated) {
            return RBACResult.Failure(500, "Failed to update role")
        }

        auditLogStore.append(
            AuditLogEntry(
                timestamp = Clock.System.now().toLocalDateTime(TimeZone.UTC).toString(),
                actorId = adminId,
                targetUserId = targetUserId,
                action = "CHANGE_ROLE",
                oldValue = oldRole.name,
                newValue = newRole.name,
                tag = "IAM_SYNC"
            )
        )

        return RBACResult.Success("Role changed from ${oldRole.name} to ${newRole.name}")
    }

    override suspend fun togglePermission(
        adminId: String,
        targetUserId: String,
        permission: Permission,
        enabled: Boolean
    ): RBACResult {
        val targetUser = userStore.findById(targetUserId)
            ?: return RBACResult.Failure(404, "User not found: $targetUserId")

        val oldPermissions = targetUser.customPermissions
        val newPermissions = if (enabled) {
            oldPermissions + permission
        } else {
            oldPermissions - permission
        }

        val updated = userStore.updatePermissions(targetUserId, newPermissions)
        if (!updated) {
            return RBACResult.Failure(500, "Failed to update permissions")
        }

        auditLogStore.append(
            AuditLogEntry(
                timestamp = Clock.System.now().toLocalDateTime(TimeZone.UTC).toString(),
                actorId = adminId,
                targetUserId = targetUserId,
                action = "TOGGLE_PERMISSION",
                oldValue = oldPermissions.joinToString(",") { it.name },
                newValue = newPermissions.joinToString(",") { it.name },
                tag = "IAM_SYNC"
            )
        )

        return RBACResult.Success("Permission ${permission.name} ${if (enabled) "enabled" else "disabled"}")
    }
}
