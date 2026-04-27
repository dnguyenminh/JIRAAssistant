package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.AnalysisStatus
import com.assistant.scan.TicketAnalysisState
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

/**
 * Fire-and-forget analysis flow:
 * 1. POST /reanalyze → 202 Accepted (server runs async)
 * 2. Poll /status every 3s → update progress bar
 * 3. When COMPLETE or 404 → fetch result via window.fetch → show tabs
 *
 * No long-running HTTP request — avoids ktor-client-js coroutine timeout.
 * Fixes: RA-007 (refresh), RA-008 (double-click), RA-009 (navigate).
 */
internal object TicketAnalysisFlow {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pollingJob: Job? = null
    private var progressJob: Job? = null
    private var isAnalyzing = false
    private const val STORAGE_KEY = "ti_analyzing_ticket"

    fun cancelJobs() {
        pollingJob?.cancel(); progressJob?.cancel()
        pollingJob = null; progressJob = null
    }

    fun startAnalysis(ticketId: String, forceReanalyze: Boolean) {
        if (isAnalyzing) return
        cancelJobs()
        TicketAutoLoader.cancelLoad()
        CascadeIntegration.reset()
        if (checkConflictAndBlock(ticketId)) return
        isAnalyzing = true
        markAnalyzing(ticketId)
        TicketProgressBar.show()
        hideResults()
        hideWarningBadge()
        document.getElementById("ti-error-msg")?.remove()
        disableActionButton()
        BlockingOverlay.show("ti-input-section", "Analyzing...")
        progressJob = scope.launch { TicketProgressBar.simulateProgress() }
        // Fire-and-forget: POST returns 202 immediately
        fireAnalysisRequest(ticketId, forceReanalyze)
        // Start polling after short delay (give server time to set status)
        pollingJob = scope.launch { delay(2000); startPolling(ticketId) }
    }

    /** Fire POST request via window.fetch — don't wait for response. */
    private fun fireAnalysisRequest(ticketId: String, forceReanalyze: Boolean) {
        val url = if (forceReanalyze) "/api/analysis/$ticketId/reanalyze"
        else "/api/analysis/$ticketId"
        val method = if (forceReanalyze) "POST" else "GET"
        val token = window.sessionStorage.getItem("jira_assistant_jwt") ?: ""
        val headers = js("({})")
        headers["Authorization"] = "Bearer $token"
        headers["Content-Type"] = "application/json"
        val opts = js("({})")
        opts["method"] = method
        opts["headers"] = headers
        window.fetch(url, opts).catch { e ->
            console.log("[TicketAnalysisFlow] Fire request error: $e")
        }
    }

    fun checkAndResumeAnalysis() {
        val ticketId = getAnalyzingTicket() ?: return
        // Quick check: if status endpoint returns 404, analysis already finished — clear and skip
        val token = window.sessionStorage.getItem("jira_assistant_jwt") ?: ""
        val headers = js("({})"); headers["Authorization"] = "Bearer $token"
        val opts = js("({})"); opts["method"] = "GET"; opts["headers"] = headers
        window.fetch("/api/analysis/$ticketId/status", opts)
            .then { resp ->
                if (resp.status.toInt() == 404 || resp.status.toInt() == 401) {
                    // Analysis not active or unauthorized — clear stale key
                    clearAnalyzing()
                    return@then
                }
                // Analysis still running — resume UI
                console.log("[TicketAnalysisFlow] Resuming for $ticketId after refresh")
                isAnalyzing = true
                TicketProgressBar.show()
                disableActionButton()
                BlockingOverlay.show("ti-input-section", "Analyzing...")
                progressJob = scope.launch { TicketProgressBar.simulateProgress() }
                pollingJob = scope.launch { startPolling(ticketId) }
            }
            .catch { clearAnalyzing() }
    }

    private suspend fun startPolling(ticketId: String) {
        while (true) {
            try {
                val token = window.sessionStorage.getItem("jira_assistant_jwt") ?: ""
                val headers = js("({})"); headers["Authorization"] = "Bearer $token"
                val opts = js("({})"); opts["method"] = "GET"; opts["headers"] = headers
                val resp = window.fetch("/api/analysis/$ticketId/status", opts).await()
                if (resp.status.toInt() == 401) { finishState(); return }
                if (resp.status.toInt() == 404) { fetchAndShowResult(ticketId); return }
                val body = resp.text().await() as String
                val status = json.decodeFromString<AnalysisStatus>(body)
                TicketProgressBar.updateFromStatus(status)
                if (status.phase == "COMPLETE" || status.progressPercent >= 100) {
                    fetchAndShowResult(ticketId); return
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("404") || msg.contains("Not Found")) {
                    fetchAndShowResult(ticketId); return
                }
                console.log("[TicketAnalysisFlow] Poll error: $msg")
            }
            delay(3000)
        }
    }

    private fun fetchAndShowResult(ticketId: String) {
        val token = window.sessionStorage.getItem("jira_assistant_jwt") ?: ""
        val headers = js("({})"); headers["Authorization"] = "Bearer $token"
        headers["Content-Type"] = "application/json"
        val opts = js("({})"); opts["method"] = "GET"; opts["headers"] = headers
        window.fetch("/api/analysis/$ticketId", opts)
            .then { resp ->
                if (resp.status.toInt() == 202) {
                    // Analysis still running — retry after delay
                    window.setTimeout({ fetchAndShowResult(ticketId) }, 3000)
                    return@then Unit
                }
                if (!resp.ok) throw Exception("HTTP ${resp.status}")
                resp.text().then { body ->
                    val data = json.decodeFromString<AnalysisResponse>(body as String)
                    cancelJobs(); TicketProgressBar.complete()
                    window.setTimeout({
                        try { handleResult(data) } catch (e: dynamic) {
                            console.log("[TicketAnalysisFlow] handleResult error: $e")
                        }
                        finishState()
                    }, 400)
                }
                Unit
            }
            .catch { err ->
                console.log("[TicketAnalysisFlow] Fetch error: $err")
                cancelJobs(); TicketProgressBar.hide()
                showError("Failed to load results: $err")
                restoreActionButton(); finishState()
            }
    }

    private fun handleResult(data: AnalysisResponse) {
        TicketResultTabs.currentAnalysis = data
        val unified = data.context?.unified ?: ""
        if (unified.startsWith("Error:")) {
            TicketProgressBar.hide(); showError(unified)
        } else {
            showResults(data)
        }
        val ticket = TicketCombobox.selectedTicket ?: return
        TicketCombobox.selectedTicket = ticket.copy(analysisState = TicketAnalysisState.ANALYZED)
        TicketCombobox.updateStatusBadge(TicketAnalysisState.ANALYZED)
        TicketCombobox.updateActionButton(TicketAnalysisState.ANALYZED)
        saveCurrentState(data)
        triggerCascadeIfSuccess(data)
        showDocGenSection(data)
    }

    private fun finishState() {
        BlockingOverlay.remove("ti-input-section")
        isAnalyzing = false; clearAnalyzing()
    }

    private fun markAnalyzing(id: String) { window.sessionStorage.setItem(STORAGE_KEY, id) }
    private fun clearAnalyzing() { window.sessionStorage.removeItem(STORAGE_KEY) }
    private fun getAnalyzingTicket(): String? = window.sessionStorage.getItem(STORAGE_KEY)?.ifBlank { null }

    fun cleanup() {
        cancelJobs(); isAnalyzing = false
        BlockingOverlay.remove("ti-input-section")
    }
}
