package com.assistant.frontend.pages.analysis

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.ScanStatusResponse
import com.assistant.frontend.pages.AnalysisPage
import com.assistant.frontend.services.ScanStatusService
import com.assistant.scan.ScanStatus
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * Scan status polling and badge updates for the Analysis page.
 */
internal object AnalysisScanStatus {

    private var scanStatusPollingJob: Job? = null
    private var analysisPollingJob: Job? = null

    fun cancelJobs() {
        scanStatusPollingJob?.cancel(); scanStatusPollingJob = null
        analysisPollingJob?.cancel(); analysisPollingJob = null
    }

    fun loadScanStatus() {
        val projectKey = ApiClient.getProjectKey() ?: return
        AnalysisPage.scope.launch {
            try {
                val response = ApiClient.get("/api/projects/$projectKey/scan/status")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val status = AnalysisPage.json.decodeFromString<ScanStatusResponse>(body)
                updateBadge(status)
                if (status.status == ScanStatus.SCANNING) {
                    startScanStatusPolling()
                    startAnalysisPolling()
                }
            } catch (e: Exception) {
                console.log("[AnalysisPage] Failed to load scan status: ${e.message}")
            }
        }
    }

    private fun updateBadge(status: ScanStatusResponse) {
        val badge = document.getElementById("analysisScanStatusBadge") as? HTMLElement ?: return
        ScanStatusService.updateBadge(badge, status.status, status.processedCount, status.totalTickets)
    }

    private fun startScanStatusPolling() {
        if (scanStatusPollingJob?.isActive == true) return
        scanStatusPollingJob = AnalysisPage.scope.launch {
            while (true) {
                delay(3000)
                val projectKey = ApiClient.getProjectKey() ?: break
                try {
                    val response = ApiClient.get("/api/projects/$projectKey/scan/status")
                    if (ApiClient.handleUnauthorized(response)) break
                    val body = response.bodyAsText()
                    val status = AnalysisPage.json.decodeFromString<ScanStatusResponse>(body)
                    updateBadge(status)
                    if (status.status == ScanStatus.COMPLETED ||
                        status.status == ScanStatus.CANCELLED ||
                        status.status == ScanStatus.IDLE) {
                        stopAnalysisPolling(); break
                    }
                } catch (e: Exception) {
                    console.log("[AnalysisPage] Scan polling error: ${e.message}"); break
                }
            }
        }
    }

    private fun startAnalysisPolling() {
        if (analysisPollingJob?.isActive == true) return
        analysisPollingJob = AnalysisPage.scope.launch {
            while (true) {
                delay(5000)
                AnalysisPage.loadAnalysisData()
            }
        }
    }

    private fun stopAnalysisPolling() {
        analysisPollingJob?.cancel(); analysisPollingJob = null
    }
}
