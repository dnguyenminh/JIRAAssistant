package com.assistant.server.mcp.internal.handlers

import com.assistant.auth.UserRole
import com.assistant.mcp.models.McpToolCallResponse
import com.assistant.rbac.PermissionMatrix
import com.assistant.rbac.RBACEngine
import com.assistant.rbac.RBACResult
import com.assistant.rbac.UserStore
import com.assistant.server.mcp.internal.UserContext
import kotlinx.serialization.json.*

/**
 * User management tool handlers — list_users, update_user_role, get_user_permissions.
 * Requirements: AC 6.92–6.94
 */
class UserManagementHandlers(
    private val rbacEngine: RBACEngine,
    private val userStore: UserStore
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun handleListUsers(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val users = userStore.getAll()
        return textResponse(json.encodeToString(users))
    }

    suspend fun handleUpdateUserRole(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val targetUserId = args.str("userId") ?: return missingField("userId")
        val newRoleStr = args.str("role") ?: return missingField("role")
        val newRole = try {
            UserRole.valueOf(newRoleStr.uppercase())
        } catch (_: Exception) {
            return errorResponse("Invalid role: $newRoleStr")
        }
        return when (val result = rbacEngine.changeRole(ctx.userId, targetUserId, newRole)) {
            is RBACResult.Success -> textResponse("""{"success":true,"message":"${result.message}"}""")
            is RBACResult.Failure -> errorResponse(result.message)
        }
    }

    suspend fun handleGetUserPermissions(args: JsonObject, ctx: UserContext): McpToolCallResponse {
        val roleStr = args.str("role") ?: return missingField("role")
        val role = try {
            UserRole.valueOf(roleStr.uppercase())
        } catch (_: Exception) {
            return errorResponse("Invalid role: $roleStr")
        }
        val permissions = PermissionMatrix.getPermissions(role)
        val result = buildJsonObject {
            put("role", roleStr)
            put("permissions", JsonArray(permissions.map { JsonPrimitive(it.name) }))
        }
        return textResponse(result.toString())
    }
}
