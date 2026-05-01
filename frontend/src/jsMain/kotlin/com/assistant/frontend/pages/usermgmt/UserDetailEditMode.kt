package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.UpdateUserRequest
import com.assistant.frontend.models.UserInfo
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Edit mode logic for UserDetailPanel — handles inline editing of name/email.
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.6, 3.9, 3.10, 3.11
 */
internal object UserDetailEditMode {

    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun enterEditMode() {
        val el = UserDetailPanel.panelElement ?: return
        val user = UserDetailPanel.currentUser ?: return
        UserDetailPanel.isEditMode = true
        replaceWithInput(el, "[data-field='name']", user.displayName, "edit-name")
        replaceWithInput(el, "[data-field='email']", user.email, "edit-email")
        replaceEditActions(el)
    }

    private fun replaceWithInput(el: HTMLElement, selector: String, value: String, inputId: String) {
        val target = el.querySelector(selector) as? HTMLElement ?: return
        val input = document.createElement("input") as HTMLInputElement
        input.className = "field-input"
        input.id = inputId
        input.value = value
        input.style.fontSize = "13px"
        input.style.width = "100%"
        target.textContent = ""
        target.appendChild(input)
    }

    private fun replaceEditActions(el: HTMLElement) {
        val actionsEl = el.querySelector(".um-detail-actions") as? HTMLElement ?: return
        actionsEl.innerHTML = ""
        val saveBtn = document.createElement("button") as HTMLElement
        saveBtn.className = "btn-vibrant"
        saveBtn.textContent = "Save"
        saveBtn.addEventListener("click", { saveEdit() })
        val cancelBtn = document.createElement("button") as HTMLElement
        cancelBtn.className = "btn-ghost"
        cancelBtn.textContent = "Cancel"
        cancelBtn.addEventListener("click", { cancelEdit() })
        actionsEl.appendChild(saveBtn)
        actionsEl.appendChild(cancelBtn)
    }

    private fun cancelEdit() {
        UserDetailPanel.isEditMode = false
        UserDetailPanel.renderDetailPanel()
    }

    private fun saveEdit() {
        val el = UserDetailPanel.panelElement ?: return
        val user = UserDetailPanel.currentUser ?: return
        val nameInput = document.getElementById("edit-name") as? HTMLInputElement
        val emailInput = document.getElementById("edit-email") as? HTMLInputElement
        val name = nameInput?.value?.trim() ?: ""
        val email = emailInput?.value?.trim() ?: ""
        if (!validateEdit(el, name, email)) return
        submitEdit(user.userId, name, email)
    }

    private fun validateEdit(el: HTMLElement, name: String, email: String): Boolean {
        if (name.isBlank()) { showEditError(el, "Name is required"); return false }
        if (!EMAIL_REGEX.matches(email)) { showEditError(el, "Invalid email format"); return false }
        return true
    }

    private fun submitEdit(userId: String, name: String, email: String) {
        BlockingOverlay.show(UserDetailPanel.CONTAINER_ID, "Saving changes...")
        UserDetailPanel.scope.launch {
            try {
                val request = UpdateUserRequest(name = name, email = email)
                val response = ApiClient.put("/api/users/$userId", request)
                if (ApiClient.handleUnauthorized(response)) return@launch
                handleEditResponse(response)
            } catch (e: Exception) {
                showEditError(UserDetailPanel.panelElement, "Connection failed. Please try again.")
            } finally {
                BlockingOverlay.remove(UserDetailPanel.CONTAINER_ID)
            }
        }
    }

    private fun handleEditResponse(response: HttpResponse) {
        if (response.status == HttpStatusCode.OK) {
            UserDetailPanel.scope.launch {
                val body = response.bodyAsText()
                UserDetailPanel.currentUser =
                    UserDetailPanel.json.decodeFromString<UserInfo>(body)
                UserDetailPanel.isEditMode = false
                UserDetailPanel.renderDetailPanel()
                UserDetailPanel.onRefreshCallback?.invoke()
            }
        } else {
            UserDetailPanel.scope.launch { showApiError(response) }
        }
    }

    private suspend fun showApiError(response: HttpResponse) {
        val body = response.bodyAsText()
        val msg = try {
            UserDetailPanel.json.parseToJsonElement(body)
                .jsonObject["error"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }
        showEditError(UserDetailPanel.panelElement, msg ?: "Failed to save (${response.status.value})")
    }

    private fun showEditError(el: HTMLElement?, message: String) {
        val panel = el ?: UserDetailPanel.panelElement ?: return
        var errorEl = panel.querySelector(".um-detail-edit-error") as? HTMLElement
        if (errorEl == null) {
            errorEl = document.createElement("div") as HTMLElement
            errorEl.className = "um-detail-edit-error um-form-error-global"
            panel.appendChild(errorEl)
        }
        errorEl.textContent = message
    }
}
