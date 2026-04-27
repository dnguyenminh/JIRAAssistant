package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.CascadeResult
import com.assistant.ai.deepanalysis.models.CascadeStatus
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.AnalysisResponse
import io.ktor.client.statement.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Integrates cascading analysis into the ticket analysis flow.
 * After main analysis completes, triggers cascade POST and starts
 * CascadeLogPanel polling. When cascade completes, refreshes
 * the Complexity tab with updated dependencies.
 * Requirements: 26.11
 */
internal object CascadeIntegration {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var monitorJob: Job? = null

    /**
     * Trigger cascading analysis asynchronously after main analysis.
     * POST /api/analysis/{ticketId}/cascade returns 202 Accepted.
     * Does not block the main analysis flow.
     */
    fun triggerCascade(ticketId: String) {
        scope.launch {
            val accepted = postCascade(ticketId)
            if (accepted) startCascadePolling(ticketId)
        }
    }

    /** Hide cascade panel and stop polling. */
    fun reset() {
        monitorJob?.cancel()
        monitorJob = null
        CascadeLogPanel.hidePanel()
    }

    /** Stop cascade polling (page cleanup). */
    fun cleanup() {
        monitorJob?.cancel()
        monitorJob = null
        CascadeLogPanel.stopPolling()
    }

    private suspend fun postCascade(ticketId: String): Boolean {
        return try {
            val resp = ApiClient.post("/api/analysis/$ticketId/cascade")
            if (ApiClient.handleUnauthorized(resp)) return false
            resp.status.value in 200..299
        } catch (e: Exception) {
            console.log("[CascadeIntegration] POST error: ${e.message}")
            false
        }
    }

    private fun startCascadePolling(ticketId: String) {
        CascadeLogPanel.startPolling(ticketId)
        monitorCascadeCompletion(ticketId)
    }

    /**
     * Monitor cascade status; when done, refresh Complexity tab.
     * Polls separately from CascadeLogPanel to detect completion.
     */
    private fun monitorCascadeCompletion(ticketId: String) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            delay(2000)
            while (isActive) {
                if (isCascadeDone(ticketId)) {
                    refreshComplexityTab(ticketId)
                    DocumentGenerationSection.refreshBadges(ticketId)
                    break
                }
                delay(3000)
            }
        }
    }

    private suspend fun isCascadeDone(ticketId: String): Boolean {
        return try {
            val resp = ApiClient.get("/api/analysis/$ticketId/cascade/status")
            val body = resp.bodyAsText()
            val result = json.decodeFromString<CascadeResult>(body)
            result.status == CascadeStatus.COMPLETED ||
                result.status == CascadeStatus.FAILED
        } catch (_: Exception) { false }
    }

    /**
     * Re-fetch analysis result and re-render Complexity tab
     * to show updated dependencies from cascading analysis.
     * Req 26.11
     */
    private suspend fun refreshComplexityTab(ticketId: String) {
        try {
            val resp = ApiClient.get("/api/analysis/$ticketId")
            if (ApiClient.handleUnauthorized(resp)) return
            val body = resp.bodyAsText()
            val data = json.decodeFromString<AnalysisResponse>(body)
            TicketResultTabs.currentAnalysis = data
            if (TicketResultTabs.activeTab == "complexity") {
                TicketResultTabs.renderTabContent(data)
            }
        } catch (e: Exception) {
            console.log("[CascadeIntegration] Refresh error: ${e.message}")
        }
    }
}
