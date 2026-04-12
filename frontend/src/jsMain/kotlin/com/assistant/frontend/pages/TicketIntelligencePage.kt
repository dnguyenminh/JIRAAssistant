package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.pages.ticket.TicketAnalysisFlow
import com.assistant.frontend.pages.ticket.TicketCombobox
import com.assistant.frontend.pages.ticket.TicketResultTabs
import com.assistant.frontend.router.Router
import com.assistant.frontend.services.NavigationContext
import com.assistant.rbac.Permission
import com.assistant.scan.TicketAnalysisState
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Ticket Intelligence page — AI-powered ticket analysis (MH5).
 * API: GET /api/projects/{key}/tickets/status, GET/POST /api/analysis/{ticketId}
 */
object TicketIntelligencePage {

    private val scope = MainScope()
    private var debounceJob: kotlinx.coroutines.Job? = null

    fun render(container: Element) {
        container.innerHTML = ""
        TicketResultTabs.activeTab = "context"
        TicketResultTabs.currentAnalysis = null
        TicketCombobox.selectedTicket = null
        TicketCombobox.ticketList = emptyList()
        TicketCombobox.filteredTickets = emptyList()
        TicketAnalysisFlow.cancelJobs()

        scope.launch {
            val html = ApiClient.loadTemplate("ticket-intelligence")
            container.innerHTML = html
            bindEvents()
            applyRBAC()
            TicketCombobox.loadTicketList()
            applyNavigationContext()
        }
    }

    private fun bindEvents() {
        val searchInput = document.getElementById("ticket-search") as? HTMLInputElement
        searchInput?.addEventListener("input", {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(300)
                TicketCombobox.filterTickets(searchInput.value)
            }
        })
        searchInput?.addEventListener("focus", {
            if (TicketCombobox.ticketList.isNotEmpty()) TicketCombobox.filterTickets(searchInput.value)
        })
        document.addEventListener("click", { event ->
            val target = event.target as? HTMLElement
            val combobox = document.getElementById("ticket-combobox")
            if (combobox != null && target != null && !combobox.contains(target)) {
                (document.getElementById("ticket-dropdown") as? HTMLElement)?.classList?.remove("visible")
            }
        })
        document.getElementById("btn-action")?.addEventListener("click", {
            val ticket = TicketCombobox.selectedTicket ?: return@addEventListener
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
     */
    private fun applyNavigationContext() {
        val params = NavigationContext.consume("ticket_intelligence")
            ?: return
        val ticketKey = params["ticketKey"] ?: return
        val match = TicketCombobox.ticketList.find {
            it.ticketId == ticketKey
        } ?: return
        TicketCombobox.selectTicket(match)
    }
}
