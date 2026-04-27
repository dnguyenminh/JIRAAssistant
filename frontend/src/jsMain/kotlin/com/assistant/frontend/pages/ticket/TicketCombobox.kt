package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.TicketPageState
import com.assistant.frontend.services.HtmlUtils
import com.assistant.rbac.Permission
import com.assistant.scan.TicketAnalysisState
import com.assistant.scan.TicketAnalysisStatus
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Ticket combobox — search, filter, select tickets.
 * Dropdown appended to document.body with position:fixed to escape
 * glass-card stacking contexts. All event binding is self-contained.
 */
internal object TicketCombobox {

    var ticketList: List<TicketAnalysisStatus> = emptyList()
    var filteredTickets: List<TicketAnalysisStatus> = emptyList()
    var selectedTicket: TicketAnalysisStatus? = null

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var debounceJob: kotlinx.coroutines.Job? = null
    private var eventsbound = false

    /** Reset state when page re-renders. */
    fun reset() {
        console.log("[Combobox] reset()")
        ticketList = emptyList()
        filteredTickets = emptyList()
        selectedTicket = null
        debounceJob?.cancel()
        eventsbound = false
        getDropdown()?.let { it.parentElement?.removeChild(it) }
    }

    /** Load ticket list from API, then bind events on the input. */
    suspend fun loadTicketList() {
        val projectKey = ApiClient.getProjectKey()
        console.log("[Combobox] loadTicketList: projectKey=$projectKey")
        if (projectKey == null) return
        try {
            val resp = ApiClient.get("/api/projects/$projectKey/tickets/status")
            console.log("[Combobox] loadTicketList: HTTP ${resp.status}")
            if (ApiClient.handleUnauthorized(resp)) return
            val body = resp.bodyAsText()
            ticketList = json.decodeFromString<List<TicketAnalysisStatus>>(body)
            filteredTickets = ticketList
            console.log("[Combobox] loadTicketList: loaded ${ticketList.size} tickets")
            hideTicketError()
            if (ticketList.isEmpty()) showTicketError("No tickets found.")
        } catch (e: Exception) {
            console.log("[Combobox] loadTicketList FAILED: ${e.message}")
            ticketList = emptyList(); filteredTickets = emptyList()
            showTicketError("Failed to load tickets: ${e.message}")
        }
        bindInputEvents()
    }

    /** Bind input/focus/click-outside events. Safe to call multiple times. */
    private fun bindInputEvents() {
        console.log("[Combobox] bindInputEvents: eventsbound=$eventsbound")
        if (eventsbound) return
        val input = document.getElementById("ticket-search") as? HTMLInputElement
        console.log("[Combobox] bindInputEvents: input=${input != null}")
        if (input == null) return
        eventsbound = true
        input.addEventListener("input", {
            console.log("[Combobox] INPUT event: value='${input.value}', ticketList.size=${ticketList.size}")
            debounceJob?.cancel()
            debounceJob = scope.launch { delay(250); filterTickets(input.value) }
        })
        input.addEventListener("focus", {
            console.log("[Combobox] FOCUS event: ticketList.size=${ticketList.size}")
            if (ticketList.isNotEmpty()) filterTickets(input.value)
        })
        document.addEventListener("click", { e ->
            val target = e.target as? HTMLElement ?: return@addEventListener
            val cb = document.getElementById("ticket-combobox")
            val dd = getDropdown()
            val clickInCombobox = cb != null && cb.contains(target)
            val clickInDropdown = dd != null && dd.contains(target)
            if (!clickInCombobox && !clickInDropdown) handleClickOutside()
        })
        window.addEventListener("resize", { positionDropdown() })
        window.addEventListener("scroll", { positionDropdown() }, js("({capture:true})"))
    }

    /** Auto-select matching ticket on click-outside, or accept cross-project ID. */
    private fun handleClickOutside() {
        val typedText = (document.getElementById("ticket-search") as? HTMLInputElement)
            ?.value?.trim() ?: ""
        val match = ticketList.find { it.ticketId.equals(typedText, ignoreCase = true) }
        when {
            match != null && match.ticketId != selectedTicket?.ticketId -> selectTicket(match)
            match == null && isValidTicketId(typedText) -> { acceptCrossProjectTicket(typedText); hideDropdown() }
            match == null && typedText.isNotBlank() -> { restoreInputOnBlur(); hideDropdown() }
            else -> hideDropdown()
        }
    }

    /** Restore input to previously selected ticket, or clear if none selected. */
    private fun restoreInputOnBlur() {
        val input = document.getElementById("ticket-search") as? HTMLInputElement ?: return
        val current = selectedTicket
        input.value = if (current != null) "${current.ticketId} — ${current.ticketSummary}" else ""
    }

    fun filterTickets(query: String) {
        console.log("[Combobox] filterTickets: query='$query', ticketList.size=${ticketList.size}")
        val q = query.trim().lowercase()
        filteredTickets = if (q.isEmpty()) ticketList
        else ticketList.filter {
            it.ticketId.lowercase().contains(q) || it.ticketSummary.lowercase().contains(q)
        }
        showDropdown(filteredTickets)
    }

    fun selectTicket(ticket: TicketAnalysisStatus) {
        console.log("[Combobox] selectTicket: ${ticket.ticketId}")
        selectedTicket = ticket
        val input = document.getElementById("ticket-search") as? HTMLInputElement
        input?.value = "${ticket.ticketId} — ${ticket.ticketSummary}"
        hideDropdown()
        updateStatusBadge(ticket.analysisState)
        updateActionButton(ticket.analysisState)
        saveSelection(ticket.ticketId)
        TicketAutoLoader.onTicketSelected(ticket.ticketId, ticket.analysisState)
    }

    /** Set ticket state without triggering auto-load. Used during session restore. */
    fun selectTicketSilently(ticket: TicketAnalysisStatus) {
        selectedTicket = ticket
        val input = document.getElementById("ticket-search") as? HTMLInputElement
        input?.value = "${ticket.ticketId} — ${ticket.ticketSummary}"
        hideDropdown()
        updateStatusBadge(ticket.analysisState)
        updateActionButton(ticket.analysisState)
        saveSelection(ticket.ticketId)
    }

    // ── Dropdown rendering ──

    private fun showDropdown(tickets: List<TicketAnalysisStatus>) {
        console.log("[Combobox] showDropdown: ${tickets.size} tickets")
        val dd = ensureDropdown()
        dd.innerHTML = ""
        if (tickets.isEmpty()) {
            dd.innerHTML = "<div style='padding:12px 16px;opacity:0.5;font-size:12px;'>No matching tickets.</div>"
        } else {
            for (t in tickets) dd.appendChild(createOption(t))
        }
        dd.style.display = "block"
        positionDropdown()
    }

    private fun hideDropdown() {
        getDropdown()?.style?.display = "none"
    }

    private fun getDropdown(): HTMLElement? =
        document.getElementById("ticket-dropdown") as? HTMLElement

    private fun ensureDropdown(): HTMLElement {
        var dd = getDropdown()
        if (dd == null) {
            dd = document.createElement("div") as HTMLElement
            dd.id = "ticket-dropdown"
            dd.className = "ticket-dropdown"
            document.body?.appendChild(dd)
        }
        return dd!!
    }

    private fun positionDropdown() {
        val input = document.getElementById("ticket-search") as? HTMLElement ?: return
        val dd = getDropdown() ?: return
        val r = input.getBoundingClientRect()
        dd.style.top = "${r.bottom + 4}px"
        dd.style.left = "${r.left}px"
        dd.style.width = "${r.width}px"
    }

    private fun createOption(ticket: TicketAnalysisStatus): HTMLElement {
        val row = document.createElement("div") as HTMLElement
        row.className = "ticket-option" + if (ticket.ticketId == selectedTicket?.ticketId) " selected" else ""
        row.setAttribute("data-ticket-id", ticket.ticketId)
        val id = document.createElement("span") as HTMLElement
        id.className = "ticket-option-id"; id.textContent = ticket.ticketId
        val summary = document.createElement("span") as HTMLElement
        summary.className = "ticket-option-summary"; summary.textContent = ticket.ticketSummary
        val badge = document.createElement("span") as HTMLElement
        badge.className = "status-badge ${badgeClassFor(ticket.analysisState)}"
        badge.textContent = badgeLabelFor(ticket.analysisState)
        row.appendChild(id); row.appendChild(summary); row.appendChild(badge)
        row.addEventListener("click", {
            console.log("[Combobox] option clicked: ${ticket.ticketId}")
            selectTicket(ticket)
        })
        return row
    }

    // ── Status badge & action button ──

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
            TicketAnalysisState.SCANNED -> enableBtn(btn, "ANALYZE")
            TicketAnalysisState.ANALYZED -> enableBtn(btn, "RE-ANALYZE")
            TicketAnalysisState.HAS_UPDATES -> { enableBtn(btn, "RE-ANALYZE"); btn.classList.add("btn-highlight-orange") }
            TicketAnalysisState.ANALYZING -> disableBtn(btn, "ANALYZING...")
        }
        if (!canAnalyze) disableBtn(btn, btn.textContent ?: "ANALYZE")
    }

    private fun enableBtn(b: HTMLElement, t: String) {
        b.textContent = t; b.removeAttribute("disabled")
        b.style.opacity = ""; b.style.cursor = ""; b.asDynamic().style.pointerEvents = ""
    }

    private fun disableBtn(b: HTMLElement, t: String) {
        b.textContent = t; b.setAttribute("disabled", "true")
        b.style.opacity = "0.5"; b.style.cursor = "not-allowed"; b.asDynamic().style.pointerEvents = "none"
    }

    private fun badgeClassFor(s: TicketAnalysisState) = when (s) {
        TicketAnalysisState.NOT_ANALYZED -> "not-analyzed"
        TicketAnalysisState.SCANNED -> "scanned"
        TicketAnalysisState.ANALYZED -> "analyzed"
        TicketAnalysisState.HAS_UPDATES -> "has-updates"
        TicketAnalysisState.ANALYZING -> "analyzing"
    }

    private fun badgeLabelFor(s: TicketAnalysisState) = when (s) {
        TicketAnalysisState.NOT_ANALYZED -> "NOT ANALYZED"
        TicketAnalysisState.SCANNED -> "SCANNED"
        TicketAnalysisState.ANALYZED -> "ANALYZED"
        TicketAnalysisState.HAS_UPDATES -> "HAS UPDATES"
        TicketAnalysisState.ANALYZING -> "ANALYZING"
    }

    // ── Helpers ──

    private fun saveSelection(ticketId: String) {
        TicketStateManager.save(TicketPageState(
            selectedTicketId = ticketId,
            selectedTicketSummary = selectedTicket?.ticketSummary ?: "",
            activeTab = TicketResultTabs.activeTab,
            analysisResult = TicketResultTabs.currentAnalysis
        ))
    }

    /** Set combobox input text from outside (e.g. immediate restore). */
    fun setInputText(text: String) {
        (document.getElementById("ticket-search") as? HTMLInputElement)
            ?.value = text
    }

    internal fun dimResultSections(dim: Boolean) {
        val o = if (dim) "0.3" else ""; val p = if (dim) "none" else ""
        listOf("ti-results-section", "ti-cascade-panel").forEach { id ->
            val el = document.getElementById(id) as? HTMLElement ?: return@forEach
            el.style.opacity = o; el.asDynamic().style.pointerEvents = p
        }
    }

    // ── Cross-project ticket support ──

    /** Validate ticket ID pattern: PROJECT-123 format. */
    fun isValidTicketId(text: String): Boolean {
        val t = text.trim()
        return t.isNotBlank() && Regex("^[A-Z][A-Z0-9]+-\\d+$", RegexOption.IGNORE_CASE).matches(t)
    }

    /**
     * Extract typed ticket ID from input, even if not in dropdown list.
     * Returns uppercase ticket ID or null if input is not a valid pattern.
     */
    fun getTypedTicketId(): String? {
        val raw = (document.getElementById("ticket-search") as? HTMLInputElement)
            ?.value?.trim() ?: return null
        // Handle "ICL2-100 — some summary" format (already selected)
        val idPart = raw.split("—", "–", " — ").first().trim()
        return if (isValidTicketId(idPart)) idPart.uppercase() else null
    }

    /**
     * Accept a cross-project ticket ID typed by user.
     * Creates a synthetic TicketAnalysisStatus so ANALYZE button works.
     */
    fun acceptCrossProjectTicket(ticketId: String) {
        val normalized = ticketId.trim().uppercase()
        console.log("[Combobox] acceptCrossProjectTicket: $normalized")
        val synthetic = TicketAnalysisStatus(
            ticketId = normalized,
            ticketSummary = "(cross-project)",
            analysisState = TicketAnalysisState.NOT_ANALYZED
        )
        selectedTicket = synthetic
        val input = document.getElementById("ticket-search") as? HTMLInputElement
        input?.value = normalized
        updateStatusBadge(TicketAnalysisState.NOT_ANALYZED)
        updateActionButton(TicketAnalysisState.NOT_ANALYZED)
        saveSelection(normalized)
    }

    private fun showTicketError(msg: String) {
        (document.getElementById("ticket-error") as? HTMLElement)?.style?.display = ""
        (document.getElementById("ticket-error-msg") as? HTMLElement)?.textContent = msg
    }

    private fun hideTicketError() {
        (document.getElementById("ticket-error") as? HTMLElement)?.style?.display = "none"
    }
}
