package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.AnalysisStatus
import com.assistant.scan.TicketAnalysisState
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Analysis flow: start analysis, polling fallback, result handling.
 */
internal object TicketAnalysisFlow {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pollingJob: Job? = null
    private var progressJob: Job? = null

    fun cancelJobs() {
        pollingJob?.cancel(); progressJob?.cancel()
        pollingJob = null; progressJob = null
    }

    fun startAnalysis(ticketId: String, forceReanalyze: Boolean) {
        cancelJobs()
        TicketProgressBar.show()
        hideResults()
        document.getElementById("ti-error-msg")?.remove()
        disableActionButton()

        val startTime = js("Date.now()") as Double
        progressJob = scope.launch { TicketProgressBar.simulateProgress() }

        BlockingOverlay.show("ti-input-section", "Analyzing...")
        scope.launch {
            try {
                val response = if (forceReanalyze) ApiClient.post("/api/analysis/$ticketId/reanalyze")
                else ApiClient.get("/api/analysis/$ticketId")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val data = json.decodeFromString<AnalysisResponse>(body)
                cancelJobs()
                TicketProgressBar.complete()
                delay(400)
                handleResult(data)
            } catch (e: Exception) {
                val elapsed = (js("Date.now()") as Double) - startTime
                if (elapsed > 15000) {
                    startPolling(ticketId)
                } else {
                    cancelJobs(); TicketProgressBar.hide()
                    showError("Analysis failed: ${e.message ?: "Unknown error"}")
                    restoreActionButton()
                }
            } finally {
                BlockingOverlay.remove("ti-input-section")
            }
        }

        pollingJob = scope.launch {
            delay(15000)
            startPolling(ticketId)
        }
    }

    private fun startPolling(ticketId: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                try {
                    val statusResp = ApiClient.get("/api/analysis/$ticketId/status")
                    if (ApiClient.handleUnauthorized(statusResp)) return@launch
                    val statusBody = statusResp.bodyAsText()
                    val status = json.decodeFromString<AnalysisStatus>(statusBody)
                    TicketProgressBar.updateFromStatus(status)
                    if (status.phase == "COMPLETE" || status.progressPercent >= 100) {
                        val fullResp = ApiClient.get("/api/analysis/$ticketId")
                        val fullBody = fullResp.bodyAsText()
                        val data = json.decodeFromString<AnalysisResponse>(fullBody)
                        cancelJobs(); TicketProgressBar.complete()
                        delay(400); handleResult(data)
                        return@launch
                    }
                } catch (e: Exception) {
                    console.log("[TicketIntelligence] Polling error: ${e.message}")
                }
                delay(3000)
            }
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
        val updated = ticket.copy(analysisState = TicketAnalysisState.ANALYZED)
        TicketCombobox.selectedTicket = updated
        TicketCombobox.updateStatusBadge(TicketAnalysisState.ANALYZED)
        TicketCombobox.updateActionButton(TicketAnalysisState.ANALYZED)
    }

    private fun showResults(data: AnalysisResponse) {
        TicketProgressBar.hide()
        (document.getElementById("ti-results-section") as? HTMLElement)?.style?.display = "block"
        TicketResultTabs.activeTab = "context"
        TicketResultTabs.updateTabStyles()
        TicketResultTabs.renderTabContent(data)
    }

    private fun hideResults() {
        (document.getElementById("ti-results-section") as? HTMLElement)?.style?.display = "none"
    }

    private fun disableActionButton() {
        val btn = document.getElementById("btn-action") as? HTMLElement ?: return
        btn.textContent = "ANALYZING..."; btn.setAttribute("disabled", "true")
        btn.style.opacity = "0.5"; btn.style.cursor = "not-allowed"
        btn.asDynamic().style.pointerEvents = "none"
    }

    private fun restoreActionButton() {
        val ticket = TicketCombobox.selectedTicket ?: return
        TicketCombobox.updateActionButton(ticket.analysisState)
    }

    private fun showError(message: String) {
        TicketProgressBar.hide()
        (document.getElementById("ti-results-section") as? HTMLElement)?.style?.display = "none"
        document.getElementById("ti-error-msg")?.remove()
        val errorDiv = document.createElement("div") as HTMLElement
        errorDiv.id = "ti-error-msg"
        errorDiv.style.apply {
            marginTop = "16px"; padding = "16px 20px"
            background = "rgba(255,80,80,0.1)"; border = "1px solid rgba(255,80,80,0.3)"
            borderRadius = "8px"; color = "#ff5050"; fontSize = "13px"
            fontFamily = "'JetBrains Mono', monospace"; letterSpacing = "0.5px"
        }
        errorDiv.textContent = message
        document.getElementById("ti-progress-section")?.parentElement?.appendChild(errorDiv)
    }
}
