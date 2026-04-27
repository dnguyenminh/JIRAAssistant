package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.TicketPageState
import com.assistant.frontend.pages.ticket.CascadeIntegration
import com.assistant.frontend.pages.ticket.CollectionJobPoller
import com.assistant.frontend.pages.ticket.DocumentGenerationSection
import com.assistant.frontend.pages.ticket.TicketAnalysisFlow
import com.assistant.frontend.pages.ticket.TicketAutoLoader
import com.assistant.frontend.pages.ticket.TicketCombobox
import com.assistant.frontend.pages.ticket.TicketResultTabs
import com.assistant.frontend.pages.ticket.TicketStateManager
import com.assistant.frontend.router.Router
import com.assistant.frontend.services.NavigationContext
import com.assistant.rbac.Permission
import com.assistant.scan.TicketAnalysisState
import com.assistant.scan.TicketAnalysisStatus
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Ticket Intelligence page — AI-powered ticket analysis (MH5).
 * API: GET /api/projects/{key}/tickets/status, GET/POST /api/analysis/{ticketId}
 */
object TicketIntelligencePage {

    private val scope = MainScope()

    fun render(container: Element) {
        container.innerHTML = ""
        TicketResultTabs.activeTab = "context"
        TicketResultTabs.currentAnalysis = null
        TicketCombobox.reset()
        TicketAnalysisFlow.cleanup() // RA-009: cancel jobs + remove overlay on navigate
        TicketAutoLoader.cancelLoad()
        CascadeIntegration.cleanup()
        CollectionJobPoller.cleanup()
        DocumentGenerationSection.hide()

        scope.launch {
            val html = ApiClient.loadTemplate("ticket-intelligence")
            container.innerHTML = html
            bindEvents()
            applyRBAC()
            immediateRestoreFromSession()
            TicketCombobox.loadTicketList()
            if (!applyNavigationContext()) restoreState()
            TicketAnalysisFlow.checkAndResumeAnalysis() // RA-007: resume after refresh
        }
    }

    /**
     * Phase 1: Immediate display from sessionStorage.
     * Sets combobox text + analysis result BEFORE API loads.
     * Requirements: 2.1
     */
    private fun immediateRestoreFromSession() {
        val saved = TicketStateManager.restore() ?: return
        if (saved.selectedTicketId.isBlank()) return
        val displayText = "${saved.selectedTicketId} — ${saved.selectedTicketSummary}"
        TicketCombobox.setInputText(displayText)
        restoreAnalysisResult(saved)
    }

    /**
     * Restore persisted state from sessionStorage.
     * Requirements: 23.1, 23.2
     */
    private fun restoreState() {
        val saved = TicketStateManager.restore() ?: return
        restoreSelectedTicket(saved)
        restoreAnalysisResult(saved)
    }

    /**
     * Phase 2: Deferred restore — sync selectedTicket object
     * after ticketList is loaded from API.
     * If ticket found in list, use full object. If not found but valid ID,
     * accept as cross-project ticket. Otherwise clear state.
     * Requirements: 2.1
     */
    private fun restoreSelectedTicket(saved: TicketPageState) {
        if (saved.selectedTicketId.isBlank()) return
        val hasResults = saved.analysisResult != null
        val match = TicketCombobox.ticketList.find {
            it.ticketId == saved.selectedTicketId
        }
        if (match != null) {
            // If we already have results from session, don't trigger auto-load (avoids skeleton flash)
            if (hasResults) TicketCombobox.selectTicketSilently(match)
            else TicketCombobox.selectTicket(match)
        } else if (TicketCombobox.isValidTicketId(saved.selectedTicketId)) {
            TicketCombobox.acceptCrossProjectTicket(saved.selectedTicketId)
        } else {
            TicketCombobox.setInputText("")
            TicketCombobox.selectedTicket = null
            TicketStateManager.clear()
        }
    }

    private fun restoreAnalysisResult(saved: TicketPageState) {
        val analysis = saved.analysisResult ?: return
        TicketResultTabs.currentAnalysis = analysis
        TicketResultTabs.activeTab = saved.activeTab
        (document.getElementById("ti-results-section") as? HTMLElement)
            ?.style?.display = "block"
        TicketResultTabs.updateTabStyles()
        TicketResultTabs.renderTabContent(analysis)
        val ticketId = saved.selectedTicketId
        val isReader = !ApiClient.hasPermission(Permission.ANALYZE_AI)
        DocumentGenerationSection.render(ticketId, true, isReader)
    }

    private fun bindEvents() {
        document.getElementById("btn-action")?.addEventListener("click", {
            val ticket = TicketCombobox.selectedTicket
                ?: tryAcceptTypedTicketId()
                ?: return@addEventListener
            if (!ApiClient.hasPermission(Permission.ANALYZE_AI)) return@addEventListener
            val forceReanalyze = ticket.analysisState != TicketAnalysisState.NOT_ANALYZED
            TicketAnalysisFlow.startAnalysis(ticket.ticketId, forceReanalyze)
        })
        document.getElementById("ticket-retry-btn")?.addEventListener("click", {
            scope.launch { TicketCombobox.loadTicketList() }
        })
        document.getElementById("ticket-project-btn")?.addEventListener("click", {
            Router.navigateTo("project_select")
        })
        bindTabSwitching()
    }

    private fun bindTabSwitching() {
        val tabs = document.querySelectorAll(".ti-tab")
        for (i in 0 until tabs.length) {
            val tab = tabs.item(i) as? HTMLElement ?: continue
            tab.addEventListener("click", {
                val tabName = tab.getAttribute("data-tab") ?: return@addEventListener
                TicketResultTabs.switchTab(tabName)
            })
        }
    }

    private fun applyRBAC() {
        if (!ApiClient.hasPermission(Permission.ANALYZE_AI)) {
            val btn = document.getElementById("btn-action") as? HTMLElement
            btn?.setAttribute("disabled", "true")
            btn?.style?.apply {
                opacity = "0.5"; cursor = "not-allowed"
                asDynamic().pointerEvents = "none"
            }
        }
    }

    /**
     * If navigated here via ChatAction with ticketKey context,
     * auto-select the matching ticket in the combobox.
     * Supports cross-project ticket IDs.
     * Returns true if context was applied (skip state restore).
     */
    private fun applyNavigationContext(): Boolean {
        val params = NavigationContext.consume("ticket_intelligence")
            ?: return false
        val ticketKey = params["ticketKey"] ?: return false
        val match = TicketCombobox.ticketList.find {
            it.ticketId == ticketKey
        }
        if (match != null) {
            TicketCombobox.selectTicket(match)
        } else if (TicketCombobox.isValidTicketId(ticketKey)) {
            TicketCombobox.acceptCrossProjectTicket(ticketKey)
        } else {
            return false
        }
        return true
    }

    /**
     * Try to accept a cross-project ticket ID typed in the combobox.
     * Returns a synthetic TicketAnalysisStatus if the input matches
     * a valid ticket ID pattern (e.g. "ABS-100"), or null otherwise.
     */
    private fun tryAcceptTypedTicketId(): TicketAnalysisStatus? {
        val typedId = TicketCombobox.getTypedTicketId() ?: return null
        TicketCombobox.acceptCrossProjectTicket(typedId)
        return TicketCombobox.selectedTicket
    }
}
