package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.RoleChangeRequest
import com.assistant.frontend.pages.UserManagementPage
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLSelectElement

/**
 * Handles user role changes via API.
 */
internal object UserRoleChanger {

    private val scope = MainScope()

    fun changeRole(userId: String, newRole: String) {
        val user = UserManagementPage.users.find { it.userId == userId } ?: return
        val oldRole = user.role

        BlockingOverlay.show("um-main-content", "Updating role...")
        scope.launch {
            try {
                val response = ApiClient.put(
                    "/api/users/$userId/role",
                    RoleChangeRequest(role = newRole)
                )
                if (ApiClient.handleUnauthorized(response)) return@launch

                val idx = UserManagementPage.users.indexOfFirst { it.userId == userId }
                if (idx >= 0) {
                    UserManagementPage.users[idx] = UserManagementPage.users[idx].copy(role = newRole)
                }

                UserAuditLog.addEntry(
                    "ROLE_CHANGE",
                    "Role changed for ${user.displayName}: ${oldRole.replace("_", " ")} → ${newRole.replace("_", " ")}"
                )
                UserAuditLog.loadFromBackend()

                if (UserManagementPage.selectedUserId == userId) {
                    UserPermissionPanel.selectUser(userId)
                }
            } catch (e: Exception) {
                console.log("[UserManagement] Role change failed: ${e.message}")
                val select = document.querySelector("#um-row-$userId .um-role-select") as? HTMLSelectElement
                select?.value = oldRole
            } finally {
                BlockingOverlay.remove("um-main-content")
            }
        }
    }
}
