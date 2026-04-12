package com.assistant.frontend.pages.dashboard

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.ScanStatusResponse
import com.assistant.frontend.pages.DashboardPage
import com.assistant.frontend.services.HtmlUtils
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * Multi-project scan progress — stacked progress bars
 * for all active (SCANNING) projects.
 * Polls GET /api/scan/active every 5s.
 */
internal object DashboardMultiScanProgress {

    private var activePollingJob: Job? = null

    /** Format: "[{KEY}] Scanning... {processed}/{total} — {percent}%" */
    fun formatProgressLabel(scan: ScanStatusResponse): String {
        val key = scan.projectKey
        val processed = scan.processedCount
        val total = scan.totalTickets
        val percent = scan.progressPercent
        return "[$key] Scanning... $processed/$total — $percent%"
    }

    /** Render stacked progress bars for all active scans. */
    fun renderActiveScans(scans: List<ScanStatusResponse>) {
        val container = el("multi-scan-progress") ?: return
        if (scans.isEmpty()) {
            hideContainer(container)
            return
        }
        showContainer(container)
        container.innerHTML = ""
        for (scan in scans) {
            container.appendChild(createProgressItem(scan))
        }
    }

    /** Start polling GET /api/scan/active every 5s. */
    fun startActivePolling() {
        if (activePollingJob?.isActive == true) return
        activePollingJob = DashboardPage.scope.launch {
            while (true) {
                delay(5000)
                val scans = fetchActiveScans()
                if (scans == null) continue
                renderActiveScans(scans)
                if (scans.isEmpty()) break
            }
        }
    }

    /** Stop active polling. */
    fun stopActivePolling() {
        activePollingJob?.cancel()
        activePollingJob = null
    }

    // ── Private helpers ─────────────────────────────────

    private fun createProgressItem(
        scan: ScanStatusResponse
    ): HTMLElement {
        val item = document.createElement("div") as HTMLElement
        item.className = "scan-progress-item"
        item.setAttribute("data-project", scan.projectKey)
        item.appendChild(createLabel(scan))
        item.appendChild(createBar(scan))
        return item
    }

    private fun createLabel(
        scan: ScanStatusResponse
    ): HTMLElement {
        val label = document.createElement("div") as HTMLElement
        label.className = "scan-progress-item-label"
        label.textContent = formatProgressLabel(scan)
        return label
    }

    private fun createBar(
        scan: ScanStatusResponse
    ): HTMLElement {
        val loader = document.createElement("div") as HTMLElement
        loader.className = "neural-loader"
        loader.style.height = "4px"
        val bar = document.createElement("div") as HTMLElement
        bar.className = "neural-progress"
        bar.style.width = "${scan.progressPercent}%"
        loader.appendChild(bar)
        return loader
    }

    private suspend fun fetchActiveScans(): List<ScanStatusResponse>? {
        return try {
            val response = ApiClient.get("/api/scan/active")
            if (ApiClient.handleUnauthorized(response)) {
                stopActivePolling(); return null
            }
            val body = response.bodyAsText()
            DashboardPage.json.decodeFromString<List<ScanStatusResponse>>(body)
        } catch (e: Exception) {
            console.log("[MultiScanProgress] Poll error: ${e.message}")
            null
        }
    }

    private fun showContainer(el: HTMLElement) {
        el.style.display = ""
    }

    private fun hideContainer(el: HTMLElement) {
        el.style.display = "none"
        el.innerHTML = ""
    }

    private fun el(id: String): HTMLElement? =
        document.getElementById(id) as? HTMLElement
}
