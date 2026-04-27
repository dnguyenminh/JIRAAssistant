package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.AnalysisResponse
import com.assistant.frontend.models.CollectionJobResponse
import io.ktor.client.statement.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Polls Collection Job status and coordinates conflict resolution.
 * Manages the lifecycle: start monitoring → poll → auto-refresh on complete.
 *
 * Requirements: 13.8, 13.12, 14.8, 14.9
 */
internal object CollectionJobPoller {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pollingJob: Job? = null
    private var currentTicketId: String? = null
    var latestJobs: List<CollectionJobResponse> = emptyList()
        private set

    private const val POLL_INTERVAL_MS = 5000L

    /** Start monitoring collection jobs for the given ticket. Req 13.12 */
    fun startMonitoring(ticketId: String) {
        currentTicketId = ticketId
        latestJobs = emptyList()
        scope.launch { fetchAndRender(ticketId) }
        startPolling(ticketId)
    }

    /** Stop polling and hide panel. */
    fun cleanup() {
        pollingJob?.cancel()
        pollingJob = null
        currentTicketId = null
        latestJobs = emptyList()
        CollectionJobPanel.hide()
        ConflictBannerManager.hideAll()
    }

    /** Check if ticket is PROCESSING in any active job. Req 14.8 */
    fun isTicketProcessing(ticketId: String): Boolean =
        latestJobs.any { job ->
            job.jobType == "LINKED_TICKET_ANALYSIS" && job.isActive &&
                job.items.any { it.itemId == ticketId && it.status == "PROCESSING" }
        }

    /** Get parent ticket ID if ticket is in a background job. */
    fun getParentTicketId(ticketId: String): String? =
        latestJobs.firstOrNull { job ->
            job.isActive && job.items.any { it.itemId == ticketId }
        }?.parentTicketId

    // ── Polling — Req 13.8 ──────────────────────────────────

    private fun startPolling(ticketId: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                fetchAndRender(ticketId)
                if (latestJobs.none { it.isActive }) {
                    onAllJobsComplete(ticketId)
                    break
                }
            }
        }
    }

    private suspend fun fetchAndRender(ticketId: String) {
        val jobs = fetchJobs(ticketId) ?: return
        latestJobs = jobs
        CollectionJobPanel.render(jobs)
        ConflictBannerManager.update(ticketId, jobs)
    }

    private suspend fun fetchJobs(ticketId: String): List<CollectionJobResponse>? {
        return try {
            val resp = ApiClient.get("/api/collection-jobs?ticketId=$ticketId")
            if (ApiClient.handleUnauthorized(resp)) return null
            json.decodeFromString<List<CollectionJobResponse>>(resp.bodyAsText())
        } catch (e: Exception) {
            console.log("[CollectionJobPoller] Fetch error: ${e.message}")
            null
        }
    }

    // ── Auto-refresh on completion — Req 14.9 ──────────────

    private fun onAllJobsComplete(ticketId: String) {
        scope.launch { refreshAnalysisResult(ticketId) }
    }

    private suspend fun refreshAnalysisResult(ticketId: String) {
        try {
            val resp = ApiClient.get("/api/analysis/$ticketId")
            if (ApiClient.handleUnauthorized(resp)) return
            val data = json.decodeFromString<AnalysisResponse>(resp.bodyAsText())
            TicketResultTabs.currentAnalysis = data
            TicketResultTabs.renderTabContent(data)
        } catch (e: Exception) {
            console.log("[CollectionJobPoller] Refresh error: ${e.message}")
        }
    }
}
