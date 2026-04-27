package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.CascadeResult
import com.assistant.ai.deepanalysis.models.CascadeStatus
import com.assistant.frontend.api.ApiClient
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
 * Panel showing cascading analysis log + progress bar.
 * Polls GET /api/analysis/{ticketId}/cascade/status for real-time updates.
 * Requirements: 26.8, 26.9, 26.10
 */
internal object CascadeLogPanel {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pollingJob: Job? = null
    private var renderedCount = 0
    private const val POLL_INTERVAL_MS = 3000L
    private const val MAX_LOG_NODES = 500

    /** Start cascade polling for a ticket. */
    fun startPolling(ticketId: String) {
        stopPolling()
        renderedCount = 0
        showPanel()
        clearLogEntries()
        updateProgress(0, 0)

        pollingJob = scope.launch {
            while (isActive) {
                val result = fetchCascadeStatus(ticketId)
                if (result != null) {
                    renderUpdate(result)
                    if (isDone(result)) {
                        DocumentGenerationSection.refreshBadges(ticketId)
                        break
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Stop polling and clean up. */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Show the cascade panel section. */
    fun showPanel() {
        val panel = getPanel() ?: return
        panel.style.display = "block"
    }

    /** Hide the cascade panel section. */
    fun hidePanel() {
        val panel = getPanel() ?: return
        panel.style.display = "none"
        stopPolling()
    }

    /** Render a CascadeResult snapshot (for non-polling use). */
    fun renderSnapshot(result: CascadeResult) {
        showPanel()
        renderedCount = 0
        clearLogEntries()
        renderUpdate(result)
    }

    private suspend fun fetchCascadeStatus(ticketId: String): CascadeResult? {
        return try {
            val resp = ApiClient.get("/api/analysis/$ticketId/cascade/status")
            if (ApiClient.handleUnauthorized(resp)) return null
            val body = resp.bodyAsText()
            json.decodeFromString<CascadeResult>(body)
        } catch (e: Exception) {
            console.log("[CascadeLogPanel] Poll error: ${e.message}")
            null
        }
    }

    private fun renderUpdate(result: CascadeResult) {
        updateProgress(result.completedTickets, result.totalTickets)
        appendNewLogEntries(result)
        if (isDone(result)) renderSummary(result)
    }

    private fun isDone(result: CascadeResult): Boolean =
        result.status == CascadeStatus.COMPLETED ||
            result.status == CascadeStatus.FAILED

    private fun updateProgress(completed: Int, total: Int) {
        val bar = document.getElementById("cascade-progress-bar") as? HTMLElement
        val label = document.getElementById("cascade-progress-label") as? HTMLElement
        val percent = if (total > 0) (completed * 100 / total) else 0
        bar?.style?.width = "$percent%"
        label?.textContent = "$completed / $total — $percent%"
    }

    private fun appendNewLogEntries(result: CascadeResult) {
        val container = getLogContainer() ?: return
        val entries = result.logEntries
        if (entries.size <= renderedCount) return
        val newEntries = entries.subList(renderedCount, entries.size)
        for (entry in newEntries) {
            container.appendChild(
                CascadeLogEntryRenderer.createLogLine(entry)
            )
        }
        renderedCount = entries.size
        trimOldNodes(container)
        scrollToBottom(container)
    }

    private fun renderSummary(result: CascadeResult) {
        val container = getLogContainer() ?: return
        container.appendChild(
            CascadeLogEntryRenderer.createSummaryLine(
                result.completedTickets,
                result.failedTickets,
                result.totalTickets
            )
        )
        scrollToBottom(container)
    }

    private fun clearLogEntries() {
        val container = getLogContainer() ?: return
        container.innerHTML = ""
        renderedCount = 0
    }

    /** Cap DOM nodes to MAX_LOG_NODES. Req: memory management. */
    private fun trimOldNodes(container: HTMLElement) {
        while (container.childElementCount > MAX_LOG_NODES) {
            container.firstElementChild?.remove()
            renderedCount = (renderedCount - 1).coerceAtLeast(0)
        }
    }

    private fun scrollToBottom(el: HTMLElement) {
        el.scrollTop = el.scrollHeight.toDouble()
    }

    private fun getPanel(): HTMLElement? =
        document.getElementById("ti-cascade-panel") as? HTMLElement

    private fun getLogContainer(): HTMLElement? =
        document.getElementById("cascade-log-entries") as? HTMLElement
}
