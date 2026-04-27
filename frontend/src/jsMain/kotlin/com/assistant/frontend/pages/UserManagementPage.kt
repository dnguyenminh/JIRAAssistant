package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.UserInfo
import com.assistant.frontend.pages.usermgmt.UserAuditLog
import com.assistant.frontend.pages.usermgmt.UserPermissionPanel
import com.assistant.frontend.pages.usermgmt.UserRoleChanger
import com.assistant.frontend.services.HtmlUtils
import com.assistant.rbac.Permission
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement

/**
 * User Management page — RBAC roles & permissions (MH7).
 * API: GET /api/users, PUT /api/users/{userId}/role, PUT /api/users/{userId}/permissions
 */
object UserManagementPage {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    internal var users = mutableListOf<UserInfo>()
    internal var selectedUserId: String? = null

    private val roles = listOf("ADMINISTRATOR", "NEURAL_ARCHITECT", "READER")

    fun render(container: Element) {
        container.innerHTML = ""
        users.clear()
        UserAuditLog.clear()
        selectedUserId = null

        scope.launch {
            val html = ApiClient.loadTemplate("user-management")
            container.innerHTML = html
            if (!ApiClient.hasPermission(Permission.MANAGE_USERS)) {
                showAccessDenied(); return@launch
            }
            showMainContent()
            loadUsers()
        }
    }

    private fun showAccessDenied() {
        (document.getElementById("um-access-denied") as? HTMLElement)?.style?.display = ""
        (document.getElementById("um-main-content") as? HTMLElement)?.style?.display = "none"
    }

    private fun showMainContent() {
        (document.getElementById("um-access-denied") as? HTMLElement)?.style?.display = "none"
        (document.getElementById("um-main-content") as? HTMLElement)?.style?.display = ""
    }

    private fun loadUsers() {
        scope.launch {
            try {
                val response = ApiClient.get("/api/users")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val userList = json.decodeFromString<List<UserInfo>>(body)
                users.clear(); users.addAll(userList)
                renderUserList()
                UserAuditLog.loadFromBackend()
            } catch (e: Exception) {
                console.log("[UserManagement] Failed to load users: ${e.message}")
                showLoadError()
            }
        }
    }

    private fun showLoadError() {
        val listEl = document.getElementById("um-user-list") as? HTMLElement
        listEl?.innerHTML = """
            <div style="text-align:center;padding:32px;">
                <div style="font-size:24px;margin-bottom:8px;">⚠️</div>
                <div style="font-size:13px;color:var(--danger);margin-bottom:12px;">Failed to load users. Check your connection and try again.</div>
                <button class="btn-vibrant" id="um-retry-btn" style="padding:8px 20px;font-size:11px;">RETRY</button>
            </div>
        """.trimIndent()
        document.getElementById("um-retry-btn")?.addEventListener("click", {
            listEl?.innerHTML = "<div style=\"text-align:center;padding:32px;opacity:0.4;font-size:13px;\">Loading users...</div>"
            loadUsers()
        })
    }

    internal fun renderUserList() {
        val listEl = document.getElementById("um-user-list") as? HTMLElement ?: return
        listEl.innerHTML = ""
        for (user in users) {
            val row = createUserRow(user)
            listEl.appendChild(row)
        }
    }

    private fun createUserRow(user: UserInfo): HTMLElement {
        val row = document.createElement("div") as HTMLElement
        row.className = "um-user-row"
        row.id = "um-row-${user.userId}"
        if (user.userId == selectedUserId) row.classList.add("selected")

        val initials = user.displayName.split(" ")
            .take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        val roleOptions = roles.joinToString("") { role ->
            val sel = if (role == user.role) "selected" else ""
            "<option value=\"$role\" $sel>${role.replace("_", " ")}</option>"
        }

        row.innerHTML = """
            <div class="um-avatar">${HtmlUtils.escapeHtml(initials)}</div>
            <div style="flex:1;min-width:0;">
                <div style="font-size:14px;font-weight:600;">${HtmlUtils.escapeHtml(user.displayName)}</div>
                <div style="font-size:12px;opacity:0.4;margin-top:2px;">${HtmlUtils.escapeHtml(user.email)}</div>
            </div>
            <select class="um-role-select" data-user-id="${user.userId}">$roleOptions</select>
        """.trimIndent()

        row.addEventListener("click", { e ->
            if (e.target is HTMLSelectElement) return@addEventListener
            UserPermissionPanel.selectUser(user.userId)
        })
        val select = row.querySelector(".um-role-select") as? HTMLSelectElement
        select?.addEventListener("change", { _ ->
            UserRoleChanger.changeRole(user.userId, select.value)
        })
        return row
    }
}
