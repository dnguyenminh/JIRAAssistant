package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.services.HtmlUtils
import com.assistant.rbac.Permission
import com.assistant.scan.TicketAnalysisState
import com.assistant.scan.TicketAnalysisStatus
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Ticket combobox: search, filter, select, status badge, action button.
 */
internal object TicketCombobox {

    var ticketList: List<TicketAnalysisStatus> = emptyList()
    var filteredTickets: List<TicketAnalysisStatus> = emptyList()
    var selectedTicket: TicketAnalysisStatus? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun loadTicketList() {
        val projectKey = ApiClient.getProjectKey() ?: return
        try {
            val response = ApiClient.get("/api/projects/$projectKey/tickets/status")
            if (ApiClient.handleUnauthorized(response)) return
            val body = response.bodyAsText()
            ticketList = json.decodeFromString<List<TicketAnalysisStatus>>(body)
            filteredTickets = ticketList
            hideTicketError()
            if (ticketList.isEmpty()) {
                showTicketError("No tickets found in this project. Verify the project has issues in Jira.")
            }
        } catch (e: Exception) {
            console.log("[TicketIntelligence] Failed to load ticket list: ${e.message}")
            ticketList = emptyList(); filteredTickets = emptyList()
            showTicketError("Failed to load tickets: ${e.message}")
        }
    }

    fun renderCombobox(tickets: List<TicketAnalysisStatus>) {
        val dropdown = document.getElementById("ticket-dropdown") as? HTMLElement ?: return
        if (tickets.isEmpty()) {
            dropdown.innerHTML = """<div style="padding:12px 16px;opacity:0.5;font-size:12px;">No tickets found. Run a scan from the Dashboard or switch project.</div>"""
            dropdown.classList.add("visible"); return
        }
        val html = StringBuilder()
        for (ticket in tickets) {
            html.append(buildOptionHtml(ticket))
        }
        dropdown.innerHTML = html.toString()
        dropdown.classList.add("visible")
        bindOptionClicks(dropdown)
    }

    private fun buildOptionHtml(ticket: TicketAnalysisStatus): String {
        val selectedClass = if (ticket.ticketId == selectedTicket?.ticketId) " selected" else ""
        val badgeClass = badgeClassFor(ticket.analysisState)
        val badgeLabel = badgeLabelFor(ticket.analysisState)
        return """
            <div class="ticket-option$selectedClass" data-ticket-id="${HtmlUtils.escapeHtml(ticket.ticketId)}">
                <span class="ticket-option-id">${HtmlUtils.escapeHtml(ticket.ticketId)}</span>
                <span class="ticket-option-summary">${HtmlUtils.escapeHtml(ticket.ticketSummary)}</span>
                <span class="status-badge $badgeClass">$badgeLabel</span>
            </div>
        """.trimIndent()
    }

    private fun bindOptionClicks(dropdown: HTMLElement) {
        val options = dropdown.querySelectorAll(".ticket-option")
        for (i in 0 until options.length) {
            val option = options.item(i) as? HTMLElement ?: continue
            option.addEventListener("click", {
                val ticketId = option.getAttribute("data-ticket-id") ?: return@addEventListener
                val ticket = ticketList.find { it.ticketId == ticketId } ?: return@addEventListener
                selectTicket(ticket)
            })
        }
    }

    fun filterTickets(query: String) {
        val q = query.trim().lowercase()
        filteredTickets = if (q.isEmpty()) ticketList
        else ticketList.filter { it.ticketId.lowercase().contains(q) || it.ticketSummary.lowercase().contains(q) }
        renderCombobox(filteredTickets)
    }

    fun selectTicket(ticket: TicketAnalysisStatus) {
        selectedTicket = ticket
        val searchInput = document.getElementById("ticket-search") as? HTMLInputElement
        searchInput?.value = "${ticket.ticketId} — ${ticket.ticketSummary}"
        (document.getElementById("ticket-dropdown") as? HTMLElement)?.classList?.remove("visible")
        updateStatusBadge(ticket.analysisState)
        updateActionButton(ticket.analysisState)
    }

    fun updateStatusBadge(state: TicketAnalysisState) {
        val badge = document.getElementById("ticket-status-badge") as? HTMLElement ?: return
        badge.className = "status-badge ${badgeClassFor(state)}"
        badge.textContent = badgeLabelFor(state)
    }

    fun updateActionButton(state: TicketAnalysisState) {
        val btn = document.getElementById("btn-action") as? HTMLElement ?: return
        val canAnalyze = ApiClient.hasPermission(Permission.ANALYZE_AI)
        btn.classList.remove("btn-highlight-orange")
        when (state) {
            TicketAnalysisState.NOT_ANALYZED -> enableBtn(btn, "ANALYZE")
            TicketAnalysisState.ANALYZED -> enableBtn(btn, "RE-ANALYZE")
            TicketAnalysisState.HAS_UPDATES -> { enableBtn(btn, "RE-ANALYZE"); btn.classList.add("btn-highlight-orange") }
            TicketAnalysisState.ANALYZING -> disableBtn(btn, "ANALYZING...")
        }
        if (!canAnalyze) disableBtn(btn, btn.textContent ?: "ANALYZE")
    }

    private fun enableBtn(btn: HTMLElement, text: String) {
        btn.textContent = text; btn.removeAttribute("disabled")
        btn.style.opacity = ""; btn.style.cursor = ""; btn.asDynamic().style.pointerEvents = ""
    }

    private fun disableBtn(btn: HTMLElement, text: String) {
        btn.textContent = text; btn.setAttribute("disabled", "true")
        btn.style.opacity = "0.5"; btn.style.cursor = "not-allowed"; btn.asDynamic().style.pointerEvents = "none"
    }

    private fun badgeClassFor(state: TicketAnalysisState) = when (state) {
        TicketAnalysisState.NOT_ANALYZED -> "not-analyzed"
        TicketAnalysisState.ANALYZED -> "analyzed"
        TicketAnalysisState.HAS_UPDATES -> "has-updates"
        TicketAnalysisState.ANALYZING -> "analyzing"
    }

    private fun badgeLabelFor(state: TicketAnalysisState) = when (state) {
        TicketAnalysisState.NOT_ANALYZED -> "NOT ANALYZED"
        TicketAnalysisState.ANALYZED -> "ANALYZED"
        TicketAnalysisState.HAS_UPDATES -> "HAS UPDATES"
        TicketAnalysisState.ANALYZING -> "ANALYZING"
    }

    private fun showTicketError(message: String) {
        val errorEl = document.getElementById("ticket-error") as? HTMLElement
        val msgEl = document.getElementById("ticket-error-msg") as? HTMLElement
        errorEl?.style?.display = ""
        msgEl?.textContent = message
    }

    private fun hideTicketError() {
        (document.getElementById("ticket-error") as? HTMLElement)
            ?.style?.display = "none"
    }
}
