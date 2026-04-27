package com.assistant.frontend.components

import com.assistant.auth.UserRole
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.NavScanStatusResponse
import com.assistant.frontend.router.Router
import com.assistant.scan.ScanStatus
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Navbar dropdown components: project selector and user widget.
 */
internal object NavbarDropdown {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun renderProjectSelector(container: HTMLElement) {
        val projectKey = ApiClient.getProjectKey()

        val selector = document.createElement("div") as HTMLElement
        selector.className = "project-selector"
        selector.id = "navbar-project-selector"

        val badge = document.createElement("div") as HTMLElement
        badge.className = "project-badge"
        badge.textContent = if (projectKey.isNullOrBlank()) "Select Project" else "[$projectKey]"
        selector.appendChild(badge)

        if (projectKey.isNullOrBlank()) {
            selector.addEventListener("click", {
                Router.navigateTo("project_select")
            })
        } else {
            val dropdown = buildProjectDropdown()
            selector.appendChild(dropdown)
            selector.addEventListener("click", { e ->
                e.stopPropagation()
                dropdown.classList.toggle("active")
                closeUserDropdown()
            })
        }
        container.appendChild(selector)
    }

    private fun buildProjectDropdown(): HTMLElement {
        val dropdown = document.createElement("div") as HTMLElement
        dropdown.className = "project-selector-dropdown"
        dropdown.id = "navbar-project-dropdown"

        val changeItem = document.createElement("a") as HTMLElement
        changeItem.className = "dropdown-item"
        changeItem.setAttribute("href", "#project_select")
        changeItem.innerHTML = """<span>🔄</span> Change Project"""
        changeItem.addEventListener("click", { e ->
            e.preventDefault()
            closeAll()
            val currentKey = ApiClient.getProjectKey()
            if (!currentKey.isNullOrBlank()) {
                scope.launch {
                    autoPauseScanIfNeeded(currentKey)
                    Router.navigateTo("project_select")
                }
            } else {
                Router.navigateTo("project_select")
            }
        })
        dropdown.appendChild(changeItem)
        return dropdown
    }

    fun renderUserWidget(container: HTMLElement) {
        val widget = document.createElement("div") as HTMLElement
        widget.className = "user-widget"
        widget.id = "navbar-user-widget"

        val email = ApiClient.getUserEmail() ?: "User"
        val role = ApiClient.getUserRole()
        val displayName = email.substringBefore("@")

        widget.innerHTML = buildUserWidgetHtml(displayName)

        val dropdown = buildUserDropdown(email, role, displayName)
        widget.appendChild(dropdown)

        widget.addEventListener("click", { e ->
            e.stopPropagation()
            dropdown.classList.toggle("active")
        })
        container.appendChild(widget)
    }

    private fun buildUserWidgetHtml(displayName: String): String = """
        <div class="avatar-mk" style="width:36px;height:36px;border-radius:50%;border:2px solid var(--accent);display:flex;align-items:center;justify-content:center;background:rgba(51,134,255,0.15);font-size:14px;font-weight:700;">
            ${displayName.firstOrNull()?.uppercase() ?: "U"}
        </div>
        <span id="navbar-username">$displayName</span>
    """.trimIndent()

    private fun buildUserDropdown(
        email: String, role: UserRole?, displayName: String
    ): HTMLElement {
        val dropdown = document.createElement("div") as HTMLElement
        dropdown.className = "user-dropdown"
        dropdown.id = "navbar-user-dropdown"
        val roleDisplay = role?.name?.replace("_", " ") ?: "Reader"

        val header = document.createElement("div") as HTMLElement
        header.className = "dropdown-header"
        header.innerHTML = """
            <div style="font-weight:700;font-size:14px;">$email</div>
            <div style="font-size:11px;opacity:0.5;">$roleDisplay</div>
        """.trimIndent()
        dropdown.appendChild(header)

        addDropdownItem(dropdown, "👤", "Account Settings", "user_management")
        if (role == UserRole.ADMINISTRATOR) {
            addDropdownItem(dropdown, "⚙️", "App Settings", "settings")
        }
        addDivider(dropdown)
        addSignOutItem(dropdown)
        return dropdown
    }

    private fun addDropdownItem(
        dropdown: HTMLElement, icon: String, label: String, route: String
    ) {
        val item = document.createElement("a") as HTMLElement
        item.className = "dropdown-item"
        item.setAttribute("href", "#$route")
        item.innerHTML = """<span>$icon</span> $label"""
        item.addEventListener("click", { e ->
            e.preventDefault(); closeAll(); Router.navigateTo(route)
        })
        dropdown.appendChild(item)
    }

    private fun addDivider(dropdown: HTMLElement) {
        val divider = document.createElement("div") as HTMLElement
        divider.style.cssText = "height:1px;background:var(--glass-border);margin:8px 0;"
        dropdown.appendChild(divider)
    }

    private fun addSignOutItem(dropdown: HTMLElement) {
        val item = document.createElement("a") as HTMLElement
        item.className = "dropdown-item danger"
        item.id = "navbar-signout"
        item.setAttribute("href", "#")
        item.innerHTML = """<span>🚪</span> Sign Out"""
        item.addEventListener("click", { e ->
            e.preventDefault(); closeAll(); ApiClient.signOut()
        })
        dropdown.appendChild(item)
    }

    /**
     * Refresh the project selector badge to reflect current sessionStorage value.
     * Called after project selection or navigation to ensure badge is up-to-date.
     */
    fun refreshProjectSelector() {
        val container = document.getElementById("navbar-project-selector")
            ?.parentElement as? HTMLElement ?: return
        (document.getElementById("navbar-project-selector") as? HTMLElement)?.remove()
        renderProjectSelector(container)
    }

    fun closeAll() {
        closeUserDropdown(); closeProjectDropdown()
    }

    private fun closeUserDropdown() {
        (document.getElementById("navbar-user-dropdown") as? HTMLElement)
            ?.classList?.remove("active")
    }

    private fun closeProjectDropdown() {
        (document.getElementById("navbar-project-dropdown") as? HTMLElement)
            ?.classList?.remove("active")
    }

    private suspend fun autoPauseScanIfNeeded(projectKey: String) {
        try {
            val resp = ApiClient.get("/api/projects/$projectKey/scan/status")
            if (ApiClient.handleUnauthorized(resp)) return
            val body = resp.bodyAsText()
            val status = json.decodeFromString<NavScanStatusResponse>(body)
            if (status.status == ScanStatus.SCANNING) {
                ApiClient.post("/api/projects/$projectKey/scan/pause")
            }
        } catch (e: Exception) {
            console.log("[Navbar] Failed to check/pause scan: ${e.message}")
        }
    }
}
