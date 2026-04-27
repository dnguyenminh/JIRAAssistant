package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.GeneratedDocumentFull
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * Approve/Reject buttons for DRAFT documents in doc-preview-modal.
 * Requirements: 6.3, 6.4, 6.5, 6.7, 6.8
 */
internal object ReviewPanel {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var currentDocId: String? = null

    @Serializable
    data class RejectBody(val reason: String)

    /** Show or hide review buttons based on document status. */
    fun render(doc: GeneratedDocumentFull, docId: String?, isReader: Boolean) {
        val area = document.getElementById("review-panel-area") as? HTMLElement ?: return
        currentDocId = docId
        if (doc.approvalStatus != "DRAFT" || isReader || docId == null) {
            area.style.display = "none"
            return
        }
        area.style.display = "flex"
        bindApproveButton(docId)
        bindRejectButton(docId)
    }

    fun hide() {
        (document.getElementById("review-panel-area") as? HTMLElement)?.style?.display = "none"
    }

    private fun bindApproveButton(docId: String) {
        val btn = document.getElementById("btn-doc-approve") as? HTMLElement ?: return
        btn.onclick = { approveDocument(docId) }
    }

    private fun bindRejectButton(docId: String) {
        val btn = document.getElementById("btn-doc-reject") as? HTMLElement ?: return
        btn.onclick = { showRejectDialog(docId) }
    }

    private fun approveDocument(docId: String) {
        BlockingOverlay.show("doc-preview-modal", "Approving...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/documents/$docId/approve")
                if (resp.status.value < 400) {
                    showToast("✅ Tài liệu đã được duyệt")
                    hide()
                    refreshPreview()
                } else {
                    showToast("❌ Approve thất bại: ${resp.bodyAsText()}")
                }
            } catch (e: Exception) {
                showToast("❌ Approve thất bại: ${e.message}")
            } finally {
                BlockingOverlay.remove("doc-preview-modal")
            }
        }
    }

    private fun showRejectDialog(docId: String) {
        val dialog = document.getElementById("reject-reason-dialog") as? HTMLElement ?: return
        val input = document.getElementById("reject-reason-input") as? HTMLTextAreaElement
        val error = document.getElementById("reject-reason-error") as? HTMLElement
        input?.value = ""
        error?.style?.display = "none"
        dialog.style.display = "flex"
        bindRejectDialogButtons(docId)
    }

    private fun bindRejectDialogButtons(docId: String) {
        val cancelBtn = document.getElementById("btn-reject-cancel") as? HTMLElement
        val confirmBtn = document.getElementById("btn-reject-confirm") as? HTMLElement
        cancelBtn?.onclick = { closeRejectDialog() }
        confirmBtn?.onclick = { submitReject(docId) }
    }

    private fun submitReject(docId: String) {
        val input = document.getElementById("reject-reason-input") as? HTMLTextAreaElement
        val reason = input?.value?.trim() ?: ""
        val error = document.getElementById("reject-reason-error") as? HTMLElement
        if (reason.length < 10) {
            error?.style?.display = "block"
            return
        }
        error?.style?.display = "none"
        closeRejectDialog()
        BlockingOverlay.show("doc-preview-modal", "Rejecting...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/documents/$docId/reject", RejectBody(reason))
                if (resp.status.value < 400) {
                    showToast("⚠️ Tài liệu đã bị reject")
                    hide()
                    refreshPreview()
                } else {
                    showToast("❌ Reject thất bại: ${resp.bodyAsText()}")
                }
            } catch (e: Exception) {
                showToast("❌ Reject thất bại: ${e.message}")
            } finally {
                BlockingOverlay.remove("doc-preview-modal")
            }
        }
    }

    private fun closeRejectDialog() {
        (document.getElementById("reject-reason-dialog") as? HTMLElement)?.style?.display = "none"
    }

    private fun refreshPreview() {
        val ticketId = TicketCombobox.selectedTicket?.ticketId ?: return
        DocumentGenerationSection.refreshBadges(ticketId)
    }

    private fun showToast(msg: String) {
        val toast = document.createElement("div") as HTMLElement
        toast.className = "app-toast"
        toast.textContent = msg
        toast.style.apply {
            position = "fixed"; bottom = "24px"; left = "50%"
            transform = "translateX(-50%)"; zIndex = "10000"
            background = "rgba(45,254,207,0.95)"; color = "#1a1a2e"
            padding = "12px 24px"; borderRadius = "8px"
            fontSize = "14px"; fontWeight = "500"; opacity = "0"
        }
        toast.style.asDynamic().transition = "opacity 0.3s ease"
        document.body?.appendChild(toast)
        kotlinx.browser.window.setTimeout({ toast.style.opacity = "1" }, 50)
        kotlinx.browser.window.setTimeout({
            toast.style.opacity = "0"
            kotlinx.browser.window.setTimeout({ toast.remove() }, 400)
        }, 3000)
    }
}
