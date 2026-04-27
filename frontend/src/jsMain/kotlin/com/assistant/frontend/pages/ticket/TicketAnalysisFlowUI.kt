package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.TicketPageState
import com.assistant.rbac.Permission
import com.assistant.scan.TicketAnalysisState
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * UI helper functions for TicketAnalysisFlow.
 * Extracted to keep TicketAnalysisFlow under 200 lines.
 */

internal fun showResults(data: AnalysisResponse) {
    TicketProgressBar.hide()
    (document.getElementById("ti-results-section") as? HTMLElement)
        ?.style?.display = "block"
    TicketResultTabs.activeTab = "context"
    TicketResultTabs.updateTabStyles()
    TicketResultTabs.renderTabContent(data)
}

internal fun hideResults() {
    (document.getElementById("ti-results-section") as? HTMLElement)
        ?.style?.display = "none"
}

internal fun hideWarningBadge() {
    (document.getElementById("ti-update-warning") as? HTMLElement)
        ?.style?.display = "none"
}

internal fun disableActionButton() {
    val btn = document.getElementById("btn-action") as? HTMLElement ?: return
    btn.textContent = "ANALYZING..."
    btn.setAttribute("disabled", "true")
    btn.style.opacity = "0.5"
    btn.style.cursor = "not-allowed"
    btn.asDynamic().style.pointerEvents = "none"
}

internal fun restoreActionButton() {
    val ticket = TicketCombobox.selectedTicket ?: return
    TicketCombobox.updateActionButton(ticket.analysisState)
}

internal fun showError(message: String) {
    TicketProgressBar.hide()
    (document.getElementById("ti-results-section") as? HTMLElement)
        ?.style?.display = "none"
    document.getElementById("ti-error-msg")?.remove()
    val errorDiv = document.createElement("div") as HTMLElement
    errorDiv.id = "ti-error-msg"
    errorDiv.style.apply {
        marginTop = "16px"; padding = "16px 20px"
        background = "rgba(255,80,80,0.1)"
        border = "1px solid rgba(255,80,80,0.3)"
        borderRadius = "8px"; color = "#ff5050"; fontSize = "13px"
        fontFamily = "'JetBrains Mono', monospace"
        letterSpacing = "0.5px"
    }
    errorDiv.textContent = message
    document.getElementById("ti-progress-section")
        ?.parentElement?.appendChild(errorDiv)
}

/** Show document generation section after successful analysis. Req 9.1 */
internal fun showDocGenSection(data: AnalysisResponse) {
    val unified = data.context?.unified ?: ""
    if (unified.startsWith("Error:")) return
    val ticketId = TicketCombobox.selectedTicket?.ticketId ?: return
    val isReader = !ApiClient.hasPermission(Permission.ANALYZE_AI)
    DocumentGenerationSection.render(ticketId, true, isReader)
}

/** Trigger cascading analysis asynchronously. Req 26.11 */
internal fun triggerCascadeIfSuccess(data: AnalysisResponse) {
    val unified = data.context?.unified ?: ""
    if (unified.startsWith("Error:")) return
    val ticketId = data.ticketId.ifBlank {
        TicketCombobox.selectedTicket?.ticketId ?: return
    }
    CascadeIntegration.triggerCascade(ticketId)
}

/** Save state to sessionStorage after analysis. Req 23.1, 23.2 */
internal fun saveCurrentState(data: AnalysisResponse) {
    val ticketId = TicketCombobox.selectedTicket?.ticketId ?: ""
    val summary = TicketCombobox.selectedTicket?.ticketSummary ?: ""
    TicketStateManager.save(TicketPageState(
        selectedTicketId = ticketId,
        selectedTicketSummary = summary,
        activeTab = TicketResultTabs.activeTab,
        analysisResult = data
    ))
}

/** Req 14.8: Block analysis if ticket is being processed by background job. */
internal fun checkConflictAndBlock(ticketId: String): Boolean {
    if (!CollectionJobPoller.isTicketProcessing(ticketId)) return false
    val parentId = CollectionJobPoller.getParentTicketId(ticketId)
    showError("Ticket đang được phân tích bởi background job từ ${parentId ?: "unknown"}. Vui lòng chờ hoàn tất.")
    return true
}
