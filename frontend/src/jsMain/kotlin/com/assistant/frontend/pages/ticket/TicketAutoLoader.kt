package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.TicketPageState
import com.assistant.rbac.Permission
import com.assistant.scan.TicketAnalysisState
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Auto-loads cached analysis results when selecting analyzed tickets.
 * Shows skeleton loading (not full progress bar) while fetching from KB cache.
 * Requirements: 23.3, 23.4, 23.5, 23.6
 */
internal object TicketAutoLoader {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var loadJob: Job? = null

    /**
     * Called after selectTicket — decides action based on analysisState.
     * Req 23.3: ANALYZED/SCANNED → auto-load from KB cache
     * Req 23.5: NOT_ANALYZED → only show ANALYZE button (no-op here)
     * Req 23.6: HAS_UPDATES → load old results + warning badge
     */
    fun onTicketSelected(ticketId: String, state: TicketAnalysisState) {
        cancelLoad()
        hideResults()
        hideWarningBadge()
        CollectionJobPoller.startMonitoring(ticketId)
        when (state) {
            TicketAnalysisState.ANALYZED,
            TicketAnalysisState.SCANNED -> loadCachedResults(ticketId, showWarning = false)
            TicketAnalysisState.HAS_UPDATES -> loadCachedResults(ticketId, showWarning = true)
            TicketAnalysisState.NOT_ANALYZED,
            TicketAnalysisState.ANALYZING -> { /* no auto-load */ }
        }
    }

    fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
        hideSkeleton()
    }

    private fun loadCachedResults(ticketId: String, showWarning: Boolean) {
        showSkeleton()
        loadJob = scope.launch {
            try {
                val response = ApiClient.get("/api/analysis/$ticketId")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val data = json.decodeFromString<AnalysisResponse>(body)
                hideSkeleton()
                displayResults(data)
                if (showWarning) showWarningBadge()
                saveState(ticketId, data)
            } catch (e: Exception) {
                hideSkeleton()
                console.log("[TicketAutoLoader] Failed: ${e.message}")
            }
        }
    }

    private fun displayResults(data: AnalysisResponse) {
        TicketResultTabs.currentAnalysis = data
        TicketResultTabs.activeTab = "context"
        val section = resultsSection() ?: return
        section.style.display = "block"
        TicketResultTabs.updateTabStyles()
        TicketResultTabs.renderTabContent(data)
        showDocGenSection()
    }

    /** Show document generation section for analyzed tickets. Req 9.1 */
    private fun showDocGenSection() {
        val ticketId = TicketCombobox.selectedTicket?.ticketId ?: return
        val isReader = !ApiClient.hasPermission(Permission.ANALYZE_AI)
        DocumentGenerationSection.render(ticketId, true, isReader)
    }

    /** Persist auto-loaded state. Req 23.1, 23.2 */
    private fun saveState(ticketId: String, data: AnalysisResponse) {
        TicketStateManager.save(TicketPageState(
            selectedTicketId = ticketId,
            selectedTicketSummary = TicketCombobox.selectedTicket?.ticketSummary ?: "",
            activeTab = TicketResultTabs.activeTab,
            analysisResult = data
        ))
    }

    private fun showSkeleton() {
        (skeletonSection())?.style?.display = "block"
    }

    private fun hideSkeleton() {
        (skeletonSection())?.style?.display = "none"
    }

    private fun hideResults() {
        TicketResultTabs.currentAnalysis = null
        (resultsSection())?.style?.display = "none"
    }

    /** Req 23.6: Show warning badge for HAS_UPDATES tickets */
    private fun showWarningBadge() {
        (warningBadge())?.style?.display = "flex"
    }

    private fun hideWarningBadge() {
        (warningBadge())?.style?.display = "none"
    }

    private fun skeletonSection() =
        document.getElementById("ti-skeleton-section") as? HTMLElement

    private fun resultsSection() =
        document.getElementById("ti-results-section") as? HTMLElement

    private fun warningBadge() =
        document.getElementById("ti-update-warning") as? HTMLElement
}
