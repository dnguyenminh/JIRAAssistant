package com.assistant.frontend.pages.dashboard

import com.assistant.auth.UserRole
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.ScanStatusResponse
import com.assistant.frontend.pages.DashboardPage
import com.assistant.frontend.services.HtmlUtils
import com.assistant.scan.ScanStatus
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement

/**
 * Scan control panel: start/pause/resume/cancel + polling.
 */
internal object DashboardScanControl {

    private var pollingJob: Job? = null

    fun bindControls() {
        val isReader = ApiClient.getUserRole() == UserRole.READER
        val btnStart = document.getElementById("btn-start-scan") as? HTMLButtonElement
        val btnPause = document.getElementById("btn-pause-scan") as? HTMLButtonElement
        val btnResume = document.getElementById("btn-resume-scan") as? HTMLButtonElement
        val btnCancel = document.getElementById("btn-cancel-scan") as? HTMLButtonElement

        if (isReader) {
            disableAllButtons(btnStart, btnPause, btnResume, btnCancel)
            return
        }
        btnStart?.addEventListener("click", { DashboardPage.scope.launch { scanAction("start") } })
        btnPause?.addEventListener("click", { DashboardPage.scope.launch { scanAction("pause") } })
        btnResume?.addEventListener("click", { DashboardPage.scope.launch { scanAction("resume") } })
        btnCancel?.addEventListener("click", { DashboardPage.scope.launch { scanAction("cancel") } })
        ScanLogDialog.bind()
    }

    private fun disableAllButtons(vararg btns: HTMLButtonElement?) {
        for (btn in btns) {
            btn?.disabled = true
            btn?.style?.opacity = "0.4"
            btn?.style?.cursor = "not-allowed"
        }
    }

    private suspend fun scanAction(action: String) {
        val projectKey = ApiClient.getProjectKey()
        if (projectKey.isNullOrBlank()) {
            showScanError("No project selected. Please select a project first.")
            return
        }
        // AI readiness check before start
        if (action == "start" && !checkAIReadiness(projectKey)) return

        val concurrency = getConcurrency()
        val forceReanalyze = getForceReanalyze()
        val url = if (action == "start") {
            "/api/projects/$projectKey/scan/$action?concurrency=$concurrency&forceReanalyze=$forceReanalyze"
        } else "/api/projects/$projectKey/scan/$action"
        val msg = when (action) {
            "start" -> if (forceReanalyze) "Force re-analyzing (×$concurrency)..." else "Starting scan (×$concurrency)..."
            "pause" -> "Pausing..."
            "resume" -> "Resuming..."
            "cancel" -> "Cancelling..."
            else -> "Processing..."
        }
        BlockingOverlay.show("scan-control-panel", msg)
        try {
            val response = ApiClient.post(url)
            if (ApiClient.handleUnauthorized(response)) return
            if (action == "start" && response.status.value == 409) {
                handleConflict()
                return
            }
            val body = response.bodyAsText()
            val status = DashboardPage.json.decodeFromString<ScanStatusResponse>(body)
            if (action == "start") ScanLogRenderer.reset()
            updateScanUI(status)
        } catch (e: Exception) {
            console.log("[DashboardPage] Failed to $action scan: ${e.message}")
            showScanError("Failed to $action scan: ${e.message}")
        } finally {
            BlockingOverlay.remove("scan-control-panel")
        }
    }

    private suspend fun checkAIReadiness(projectKey: String): Boolean {
        try {
            val resp = ApiClient.get("/api/projects/$projectKey/scan/ai-status")
            val body = resp.bodyAsText()
            val aiReady = body.contains("\"aiReady\":true")
            if (!aiReady) {
                val proceed = kotlinx.browser.window.confirm(
                    "No AI provider is active. Scan will proceed without AI analysis.\n\nContinue anyway?"
                )
                if (!proceed) return false
            }
        } catch (_: Exception) { /* proceed if check fails */ }
        return true
    }

    private fun getConcurrency(): Int {
        val input = document.getElementById("scan-concurrency") as? org.w3c.dom.HTMLInputElement
        return (input?.value?.toIntOrNull() ?: 3).coerceIn(1, 20)
    }

    private fun getForceReanalyze(): Boolean {
        val cb = document.getElementById("scan-force-reanalyze") as? org.w3c.dom.HTMLInputElement
        return cb?.checked ?: false
    }

    private suspend fun handleConflict() {
        try {
            val resp = ApiClient.get("/api/scan/active")
            if (ApiClient.handleUnauthorized(resp)) return
            val body = resp.bodyAsText()
            val scans = DashboardPage.json
                .decodeFromString<List<ScanStatusResponse>>(body)
            DashboardMultiScanProgress.renderActiveScans(scans)
            DashboardMultiScanProgress.startActivePolling()
        } catch (e: Exception) {
            console.log("[DashboardPage] 409 fallback error: ${e.message}")
            showScanError("Scan already running for this project.")
        }
    }

    fun loadScanStatus() {
        val projectKey = ApiClient.getProjectKey()
        if (projectKey.isNullOrBlank()) {
            updateStatusLabel(ScanStatus.IDLE)
            return
        }
        DashboardPage.scope.launch {
            try {
                val response = ApiClient.get("/api/projects/$projectKey/scan/status")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val status = DashboardPage.json.decodeFromString<ScanStatusResponse>(body)
                updateScanUI(status)
            } catch (e: Exception) {
                console.log("[DashboardPage] Failed to load scan status: ${e.message}")
                showScanError("Failed to load scan status: ${e.message}")
            }
        }
    }

    private fun updateScanUI(status: ScanStatusResponse) {
        updateButtonVisibility(status.status)
        updateProgressBar(status)
        updateStatusLabel(status.status, status.totalTickets)
        // Always update log — clear if empty to avoid stale entries
        ScanLogRenderer.render(status.recentLog)
        if (status.status == ScanStatus.SCANNING) startPolling() else stopPolling()
    }

    private fun updateButtonVisibility(status: ScanStatus) {
        val btnStart = el("btn-start-scan")
        val btnPause = el("btn-pause-scan")
        val btnResume = el("btn-resume-scan")
        val btnCancel = el("btn-cancel-scan")
        val progressBar = el("scan-progress")
        val progressLabel = el("scan-progress-label")
        val logContainer = el("scan-log-container")

        when (status) {
            ScanStatus.SCANNING -> {
                btnStart?.style?.display = "none"; btnPause?.style?.display = ""; btnResume?.style?.display = "none"; btnCancel?.style?.display = ""
                progressBar?.style?.display = ""; progressLabel?.style?.display = ""; logContainer?.style?.display = ""
            }
            ScanStatus.PAUSED -> {
                btnStart?.style?.display = "none"; btnPause?.style?.display = "none"; btnResume?.style?.display = ""; btnCancel?.style?.display = ""
                progressBar?.style?.display = ""; progressLabel?.style?.display = ""; logContainer?.style?.display = ""
            }
            ScanStatus.COMPLETED -> {
                btnStart?.style?.display = ""; btnPause?.style?.display = "none"; btnResume?.style?.display = "none"; btnCancel?.style?.display = "none"
                progressBar?.style?.display = ""; progressLabel?.style?.display = ""; logContainer?.style?.display = ""
            }
            ScanStatus.CANCELLED -> {
                btnStart?.style?.display = ""; btnPause?.style?.display = "none"; btnResume?.style?.display = "none"; btnCancel?.style?.display = "none"
                progressBar?.style?.display = "none"; progressLabel?.style?.display = "none"
            }
            ScanStatus.IDLE -> {
                btnStart?.style?.display = ""; btnPause?.style?.display = "none"; btnResume?.style?.display = "none"; btnCancel?.style?.display = "none"
                progressBar?.style?.display = "none"; progressLabel?.style?.display = "none"; logContainer?.style?.display = "none"
            }
        }
    }

    private fun updateProgressBar(status: ScanStatusResponse) {
        el("scan-progress-bar")?.style?.width = "${status.progressPercent}%"
        el("scan-progress-label")?.textContent = "${status.processedCount} / ${status.totalTickets} — ${status.progressPercent}%"
    }

    private fun updateStatusLabel(status: ScanStatus, totalTickets: Int = -1) {
        val label = el("scan-status-label")
        label?.style?.color = ""  // Reset color
        label?.textContent = when (status) {
            ScanStatus.SCANNING -> "Scanning project tickets..."
            ScanStatus.PAUSED -> "Scan paused."
            ScanStatus.COMPLETED -> if (totalTickets == 0) {
                "No tickets found. This project may be empty — try switching to a different project."
            } else {
                "Scan completed — $totalTickets tickets processed."
            }
            ScanStatus.CANCELLED -> "Scan cancelled."
            ScanStatus.IDLE -> "Ready to scan project tickets."
        }
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = DashboardPage.scope.launch {
            while (true) {
                delay(3000)
                val projectKey = ApiClient.getProjectKey() ?: break
                try {
                    val response = ApiClient.get("/api/projects/$projectKey/scan/status")
                    if (ApiClient.handleUnauthorized(response)) break
                    val body = response.bodyAsText()
                    val status = DashboardPage.json.decodeFromString<ScanStatusResponse>(body)
                    updateScanUI(status)
                    if (status.status == ScanStatus.COMPLETED || status.status == ScanStatus.CANCELLED || status.status == ScanStatus.IDLE) break
                } catch (e: Exception) {
                    console.log("[DashboardPage] Polling error: ${e.message}"); break
                }
            }
        }
    }

    fun stopPolling() { pollingJob?.cancel(); pollingJob = null }

    private fun showScanError(message: String) {
        val label = el("scan-status-label")
        label?.textContent = message
        label?.style?.color = "var(--danger)"
        // Also show toast for visibility
        com.assistant.frontend.services.ToastService.show(message, "error")
    }

    private fun el(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement
}
