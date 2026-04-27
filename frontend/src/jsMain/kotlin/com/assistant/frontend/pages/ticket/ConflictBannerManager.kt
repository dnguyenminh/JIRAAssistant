package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.CollectionJobResponse
import com.assistant.scan.TicketAnalysisState
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Manages conflict resolution UI banners on Ticket Intelligence page.
 * Shows/hides banners based on Collection Job state and disables
 * the ANALYZE button when the ticket is actively PROCESSING.
 *
 * Requirements: 14.8, 14.9, 1.10, 14.3
 */
internal object ConflictBannerManager {

    /** Update banners based on current jobs for the selected ticket. */
    fun update(ticketId: String, jobs: List<CollectionJobResponse>) {
        val isProcessing = CollectionJobPoller.isTicketProcessing(ticketId)
        val parentId = CollectionJobPoller.getParentTicketId(ticketId)

        if (isProcessing && parentId != null) {
            showConflictBanner(
                "Ticket đang được phân tích bởi background job từ $parentId"
            )
            disableAnalyzeButton()
        } else {
            hideConflictBanner()
            restoreAnalyzeButton()
        }
    }

    /** Show permission-denied warning. Req 1.10, 16.3 */
    fun showPermissionWarning(count: Int) {
        if (count <= 0) return
        val warning = el("ti-permission-warning") ?: return
        warning.style.display = "flex"
        el("ti-permission-msg")?.textContent =
            "⚠️ $count tickets bị bỏ qua do không có quyền truy cập"
    }

    /** Hide all banners. */
    fun hideAll() {
        hideConflictBanner()
        hidePermissionWarning()
    }

    // ── Conflict banner — Req 14.8 ─────────────────────────

    private fun showConflictBanner(message: String) {
        val banner = el("ti-conflict-banner") ?: return
        banner.style.display = "flex"
        el("ti-conflict-msg")?.textContent = message
    }

    private fun hideConflictBanner() {
        el("ti-conflict-banner")?.style?.display = "none"
    }

    private fun hidePermissionWarning() {
        el("ti-permission-warning")?.style?.display = "none"
    }

    // ── Analyze button control — Req 14.8 ───────────────────

    private fun disableAnalyzeButton() {
        val btn = el("btn-action") ?: return
        btn.setAttribute("disabled", "true")
        btn.style.opacity = "0.5"
        btn.style.cursor = "not-allowed"
        btn.asDynamic().style.pointerEvents = "none"
    }

    private fun restoreAnalyzeButton() {
        val ticket = TicketCombobox.selectedTicket ?: return
        TicketCombobox.updateActionButton(ticket.analysisState)
    }

    private fun el(id: String): HTMLElement? =
        document.getElementById(id) as? HTMLElement
}
