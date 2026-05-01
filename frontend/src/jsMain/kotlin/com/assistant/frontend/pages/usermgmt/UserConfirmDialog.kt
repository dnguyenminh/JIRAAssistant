package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.UpdateStatusRequest
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Confirmation dialogs for disable and delete user actions.
 * Clones tmpl-confirm-dialog template, appends to document.body as fixed overlay.
 * Requirements: 4.1, 4.9, 4.10, 5.1, 5.2, 5.5, 5.8, 5.9, 5.10
 */
object UserConfirmDialog {

    private val scope = MainScope()
    private var overlayElement: HTMLElement? = null

    fun showDisableConfirm(userId: String, userName: String, onSuccess: () -> Unit) {
        val message = "Are you sure you want to disable $userName?"
        val overlay = cloneDialog(message, showInput = false)
        bindDisableActions(overlay, userId, onSuccess)
        showOverlay(overlay)
    }

    fun showDeleteConfirm(userId: String, userName: String, onSuccess: () -> Unit) {
        val message = "Are you sure you want to delete $userName? This action cannot be undone."
        val overlay = cloneDialog(message, showInput = true, placeholder = userName)
        bindDeleteActions(overlay, userId, userName, onSuccess)
        showOverlay(overlay)
    }

    private fun cloneDialog(message: String, showInput: Boolean, placeholder: String = ""): HTMLElement {
        val tmpl = document.getElementById("tmpl-confirm-dialog") as HTMLTemplateElement
        val el = tmpl.content.firstElementChild?.cloneNode(true) as HTMLElement
        el.querySelector("[data-field='message']")?.let { (it as HTMLElement).textContent = message }
        if (showInput) {
            val wrap = el.querySelector("[data-field='input-wrap']") as? HTMLElement
            wrap?.style?.display = ""
            val input = el.querySelector("[data-field='confirm-input']") as? HTMLInputElement
            input?.placeholder = placeholder
        }
        return el
    }

    private fun bindDisableActions(overlay: HTMLElement, userId: String, onSuccess: () -> Unit) {
        overlay.querySelector("[data-action='confirm']")?.addEventListener("click", {
            executeDisable(userId, onSuccess)
        })
        overlay.querySelector("[data-action='cancel']")?.addEventListener("click", {
            closeDialog()
        })
    }

    private fun bindDeleteActions(
        overlay: HTMLElement, userId: String, userName: String, onSuccess: () -> Unit
    ) {
        val confirmBtn = overlay.querySelector("[data-action='confirm']") as? HTMLElement
        val input = overlay.querySelector("[data-field='confirm-input']") as? HTMLInputElement

        // BUG-001 fix: Disable confirm button by default, enable only when input matches userName
        confirmBtn?.let { btn ->
            btn.setAttribute("disabled", "true")
            btn.classList.add("btn-disabled")
        }

        // Toggle disabled state on input change
        input?.addEventListener("input", {
            val matches = input.value.trim() == userName
            if (matches) {
                confirmBtn?.removeAttribute("disabled")
                confirmBtn?.classList?.remove("btn-disabled")
            } else {
                confirmBtn?.setAttribute("disabled", "true")
                confirmBtn?.classList?.add("btn-disabled")
            }
        })

        confirmBtn?.addEventListener("click", {
            if (input?.value?.trim() == userName) {
                executeDelete(userId, onSuccess)
            }
        })
        overlay.querySelector("[data-action='cancel']")?.addEventListener("click", {
            closeDialog()
        })
    }

    private fun executeDisable(userId: String, onSuccess: () -> Unit) {
        BlockingOverlay.show("um-main-content", "Updating status...")
        scope.launch {
            try {
                val body = UpdateStatusRequest(status = "DISABLED")
                val response = ApiClient.put("/api/users/$userId/status", body)
                if (response.status == HttpStatusCode.OK) {
                    closeDialog()
                    onSuccess()
                } else {
                    showToastError("Failed to disable user.")
                }
            } catch (e: Exception) {
                showToastError("Connection failed. Please try again.")
            } finally {
                BlockingOverlay.remove("um-main-content")
            }
        }
    }

    private fun executeDelete(userId: String, onSuccess: () -> Unit) {
        BlockingOverlay.show("um-main-content", "Deleting user...")
        scope.launch {
            try {
                val response = ApiClient.delete("/api/users/$userId")
                if (response.status == HttpStatusCode.NoContent) {
                    closeDialog()
                    UserDetailPanel.hide()
                    onSuccess()
                } else {
                    showToastError("Failed to delete user.")
                }
            } catch (e: Exception) {
                showToastError("Connection failed. Please try again.")
            } finally {
                BlockingOverlay.remove("um-main-content")
            }
        }
    }

    private fun showOverlay(overlay: HTMLElement) {
        closeDialog()
        overlayElement = overlay
        document.body?.appendChild(overlay)
    }

    private fun closeDialog() {
        overlayElement?.remove()
        overlayElement = null
    }

    private fun showToastError(message: String) {
        console.log("[UserConfirmDialog] Error: $message")
        UserDetailPanel.showError(message)
    }
}
