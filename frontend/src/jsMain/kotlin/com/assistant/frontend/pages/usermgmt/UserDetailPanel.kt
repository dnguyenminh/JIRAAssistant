package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.UserInfo
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * User Detail Panel — displays user profile info with view/edit modes.
 * Clones tmpl-user-detail template, fetches user data, supports inline editing.
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 3.1–3.4, 3.6, 3.9–3.11
 */
object UserDetailPanel {

    internal val scope = MainScope()
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }
    internal var panelElement: HTMLElement? = null
    internal var currentUser: UserInfo? = null
    internal var isEditMode = false
    internal var onRefreshCallback: (() -> Unit)? = null

    internal const val CONTAINER_ID = "um-main-content"
    private const val PANEL_ANCHOR_ID = "um-perm-panel"

    fun init(onRefresh: () -> Unit) {
        onRefreshCallback = onRefresh
    }

    fun selectUser(userId: String) {
        isEditMode = false
        fetchAndRender(userId)
    }

    fun hide() {
        panelElement?.remove()
        panelElement = null
        currentUser = null
        isEditMode = false
    }

    private fun fetchAndRender(userId: String) {
        showLoadingSkeleton()
        BlockingOverlay.show(CONTAINER_ID, "Loading user...")
        scope.launch {
            try {
                val response = ApiClient.get("/api/users/$userId")
                if (ApiClient.handleUnauthorized(response)) return@launch
                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    currentUser = json.decodeFromString<UserInfo>(body)
                    renderDetailPanel()
                } else {
                    showError("Failed to load user details.")
                }
            } catch (e: Exception) {
                showError("Connection failed. Please try again.")
            } finally {
                BlockingOverlay.remove(CONTAINER_ID)
            }
        }
    }

    private fun showLoadingSkeleton() {
        removePanel()
        val skeleton = document.createElement("div") as HTMLElement
        skeleton.className = "um-detail-panel glass-card"
        skeleton.id = "um-detail-panel"
        skeleton.textContent = "Loading user details..."
        skeleton.style.opacity = "0.5"
        skeleton.style.textAlign = "center"
        skeleton.style.padding = "32px"
        insertPanel(skeleton)
    }

    internal fun showError(message: String) {
        removePanel()
        val errorEl = document.createElement("div") as HTMLElement
        errorEl.className = "um-detail-panel glass-card"
        errorEl.id = "um-detail-panel"
        val msgEl = document.createElement("div") as HTMLElement
        msgEl.textContent = message
        msgEl.style.color = "var(--danger)"
        msgEl.style.marginBottom = "12px"
        errorEl.appendChild(msgEl)
        val retryBtn = document.createElement("button") as HTMLElement
        retryBtn.className = "btn-vibrant"
        retryBtn.textContent = "Retry"
        retryBtn.addEventListener("click", {
            currentUser?.let { selectUser(it.userId) }
        })
        errorEl.appendChild(retryBtn)
        insertPanel(errorEl)
    }

    internal fun renderDetailPanel() {
        removePanel()
        val user = currentUser ?: return
        val tmpl = document.getElementById("tmpl-user-detail") as? HTMLTemplateElement ?: return
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement ?: return
        el.id = "um-detail-panel"
        populateFields(el, user)
        bindActions(el, user)
        insertPanel(el)
        panelElement = el
    }

    private fun populateFields(el: HTMLElement, user: UserInfo) {
        val initials = user.displayName.split(" ")
            .take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        el.querySelector("[data-field='avatar']")?.let { (it as HTMLElement).textContent = initials }
        el.querySelector("[data-field='name']")?.let { (it as HTMLElement).textContent = user.displayName }
        el.querySelector("[data-field='email']")?.let { (it as HTMLElement).textContent = user.email }
        el.querySelector("[data-field='role']")?.let {
            (it as HTMLElement).textContent = user.role.replace("_", " ")
        }
        el.querySelector("[data-field='createdAt']")?.let {
            (it as HTMLElement).textContent = formatDate(user.createdAt)
        }
        applyStatusBadge(el, user.status)
    }

    private fun applyStatusBadge(el: HTMLElement, status: String) {
        val badge = el.querySelector("[data-field='status']") as? HTMLElement ?: return
        badge.textContent = status
        badge.className = "um-status-badge"
        when (status.uppercase()) {
            "ACTIVE" -> badge.classList.add("um-status-active")
            "DISABLED" -> badge.classList.add("um-status-disabled")
            "PENDING" -> badge.classList.add("um-status-pending")
        }
    }

    private fun bindActions(el: HTMLElement, user: UserInfo) {
        el.querySelector("[data-action='edit']")?.addEventListener("click", {
            UserDetailEditMode.enterEditMode()
        })
        bindStatusAction(el, user)
        bindDeleteAction(el, user)
    }

    private fun bindStatusAction(el: HTMLElement, user: UserInfo) {
        val btn = el.querySelector("[data-action='disable']") as? HTMLElement ?: return
        if (user.status.uppercase() == "DISABLED") {
            btn.textContent = "Enable"
            btn.className = "btn-ghost"
            btn.addEventListener("click", { executeEnable(user.userId) })
        } else {
            btn.addEventListener("click", {
                UserConfirmDialog.showDisableConfirm(user.userId, user.displayName) {
                    onRefreshCallback?.invoke()
                    selectUser(user.userId)
                }
            })
        }
    }

    private fun bindDeleteAction(el: HTMLElement, user: UserInfo) {
        el.querySelector("[data-action='delete']")?.addEventListener("click", {
            UserConfirmDialog.showDeleteConfirm(user.userId, user.displayName) {
                onRefreshCallback?.invoke()
            }
        })
    }

    private fun executeEnable(userId: String) {
        BlockingOverlay.show(CONTAINER_ID, "Updating status...")
        scope.launch {
            try {
                val body = com.assistant.frontend.models.UpdateStatusRequest(status = "ACTIVE")
                val response = ApiClient.put("/api/users/$userId/status", body)
                if (response.status == HttpStatusCode.OK) {
                    onRefreshCallback?.invoke()
                    selectUser(userId)
                } else {
                    showError("Failed to enable user.")
                }
            } catch (e: Exception) {
                showError("Connection failed. Please try again.")
            } finally {
                BlockingOverlay.remove(CONTAINER_ID)
            }
        }
    }

    private fun formatDate(iso: String): String {
        if (iso.isBlank()) return "N/A"
        return iso.substringBefore("T").ifBlank { iso }
    }

    private fun removePanel() {
        document.getElementById("um-detail-panel")?.remove()
        panelElement = null
    }

    private fun insertPanel(el: HTMLElement) {
        val anchor = document.getElementById(PANEL_ANCHOR_ID)
        val container = document.getElementById(CONTAINER_ID) as? HTMLElement ?: return
        if (anchor != null) {
            container.insertBefore(el, anchor)
        } else {
            container.appendChild(el)
        }
    }
}
