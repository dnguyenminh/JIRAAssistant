package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.components.Sidebar
import com.assistant.frontend.models.PermToggle
import com.assistant.frontend.models.PermissionToggleRequest
import com.assistant.frontend.pages.UserManagementPage
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * Handles user selection and permission toggle panel.
 */
internal object UserPermissionPanel {

    private val scope = MainScope()

    val permissionToggles = listOf(
        PermToggle("ANALYZE_AI", "Trigger AI Scan"),
        PermToggle("KB_WRITE", "Knowledge Base Write"),
        PermToggle("CONFIG_INTEGRATIONS", "Update Integrations"),
        PermToggle("EXPORT_DATA", "Export Neural Data")
    )

    fun selectUser(userId: String) {
        UserManagementPage.selectedUserId = userId
        highlightRow(userId)
        renderPanel(userId)
    }

    private fun highlightRow(userId: String) {
        val rows = document.querySelectorAll(".um-user-row")
        for (i in 0 until rows.length) {
            val r = rows.item(i) as? HTMLElement ?: continue
            if (r.id == "um-row-$userId") r.classList.add("selected")
            else r.classList.remove("selected")
        }
    }

    private fun renderPanel(userId: String) {
        val panel = document.getElementById("um-perm-panel") as? HTMLElement ?: return
        val nameEl = document.getElementById("um-perm-user-name") as? HTMLElement ?: return
        val togglesEl = document.getElementById("um-perm-toggles") as? HTMLElement ?: return

        val user = UserManagementPage.users.find { it.userId == userId } ?: return
        panel.style.display = "block"
        nameEl.textContent = user.displayName
        togglesEl.innerHTML = ""

        for (toggle in permissionToggles) {
            val isEnabled = user.permissions.contains(toggle.key)
            val card = document.createElement("div") as HTMLElement
            card.className = "um-toggle-card"
            card.innerHTML = """
                <div style="font-size:13px;font-weight:500;">${toggle.label}</div>
                <div class="um-toggle-switch" data-perm="${toggle.key}" data-user-id="${user.userId}">
                    <div class="um-toggle-track ${if (isEnabled) "active" else ""}"></div>
                    <div class="um-toggle-thumb ${if (isEnabled) "active" else ""}"></div>
                </div>
            """.trimIndent()
            togglesEl.appendChild(card)
            bindToggleClick(card, userId)
        }
    }

    private fun bindToggleClick(card: HTMLElement, userId: String) {
        val switchEl = card.querySelector(".um-toggle-switch") as? HTMLElement ?: return
        switchEl.addEventListener("click", {
            val permKey = switchEl.getAttribute("data-perm") ?: return@addEventListener
            val currentlyEnabled = switchEl.querySelector(".um-toggle-track")
                ?.classList?.contains("active") ?: false
            togglePermission(userId, permKey, !currentlyEnabled, switchEl)
        })
    }

    private fun togglePermission(
        userId: String, permKey: String, enabled: Boolean, switchEl: HTMLElement
    ) {
        val user = UserManagementPage.users.find { it.userId == userId } ?: return
        val toggle = permissionToggles.find { it.key == permKey } ?: return

        Sidebar.updateStatus("IAM SYNC: UPDATING...", "SYNCING PERMISSIONS", 0)
        var progress = 0
        val intervalId = window.setInterval({
            progress += 8
            if (progress <= 90) Sidebar.updateStatus("IAM SYNC: UPDATING...", "SYNCING PERMISSIONS", progress)
        }, 100)

        BlockingOverlay.show("um-perm-panel", "Updating permissions...")
        scope.launch {
            try {
                val response = ApiClient.put(
                    "/api/users/$userId/permissions",
                    PermissionToggleRequest(permission = permKey, enabled = enabled)
                )
                if (ApiClient.handleUnauthorized(response)) return@launch
                updateLocalPermissions(userId, permKey, enabled)
                updateToggleUI(switchEl, enabled)
                window.clearInterval(intervalId)
                completeSidebarSync()
                val action = if (enabled) "ENABLED" else "DISABLED"
                UserAuditLog.addEntry("IAM_SYNC", "${toggle.label} $action for ${user.displayName}")
            } catch (e: Exception) {
                window.clearInterval(intervalId)
                failSidebarSync()
                console.log("[UserManagement] Permission toggle failed: ${e.message}")
                UserAuditLog.addEntry("IAM_ERROR", "Permission update failed: ${e.message}")
            } finally {
                BlockingOverlay.remove("um-perm-panel")
            }
        }
    }

    private fun updateLocalPermissions(userId: String, permKey: String, enabled: Boolean) {
        val idx = UserManagementPage.users.indexOfFirst { it.userId == userId }
        if (idx >= 0) {
            val perms = UserManagementPage.users[idx].permissions.toMutableList()
            if (enabled) perms.add(permKey) else perms.remove(permKey)
            UserManagementPage.users[idx] = UserManagementPage.users[idx].copy(permissions = perms)
        }
    }

    private fun updateToggleUI(switchEl: HTMLElement, enabled: Boolean) {
        val track = switchEl.querySelector(".um-toggle-track") as? HTMLElement
        val thumb = switchEl.querySelector(".um-toggle-thumb") as? HTMLElement
        if (enabled) { track?.classList?.add("active"); thumb?.classList?.add("active") }
        else { track?.classList?.remove("active"); thumb?.classList?.remove("active") }
    }

    private suspend fun completeSidebarSync() {
        Sidebar.updateStatus("IAM SYNC: UPDATING...", "SYNCING PERMISSIONS", 100)
        delay(500)
        Sidebar.updateStatus("System Status: ACTIVE", "SYNC COMPLETE", 100)
        delay(2000)
        Sidebar.updateStatus("System Status: ACTIVE", "READY", 100)
    }

    private suspend fun failSidebarSync() {
        Sidebar.updateStatus("System Status: ACTIVE", "SYNC FAILED", 100)
        delay(2000)
        Sidebar.updateStatus("System Status: ACTIVE", "READY", 100)
    }
}
