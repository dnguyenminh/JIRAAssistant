package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.ProjectAnalysisResponse
import com.assistant.frontend.pages.analysis.AnalysisBottleneckRadar
import com.assistant.frontend.pages.analysis.AnalysisScanStatus
import com.assistant.frontend.pages.analysis.AnalysisStateManager
import com.assistant.frontend.pages.analysis.AnalysisVelocityChart
import com.assistant.frontend.router.Router
import com.assistant.frontend.services.HtmlUtils
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * Project Analysis page — Sprint Analytics Module (MH4).
 * API: GET /api/projects/{projectKey}/analysis → ProjectAnalysisResponse
 */
object AnalysisPage {

    internal val scope = MainScope()
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun render(container: Element) {
        container.innerHTML = ""
        cleanup()
        scope.launch {
            val html = ApiClient.loadTemplate("analysis")
            container.innerHTML = html
            bindEvents()
            immediateRestoreFromSession()
            loadAnalysisData()
            AnalysisScanStatus.loadScanStatus()
        }
    }

    fun cleanup() {
        AnalysisScanStatus.cancelJobs()
    }

    private fun bindEvents() {
        document.getElementById("btnDiveReports")?.addEventListener("click", {
            Router.navigateTo("ticket_intelligence")
        })
        document.getElementById("analysis-retry-btn")?.addEventListener("click", {
            loadAnalysisData()
        })
        document.getElementById("analysis-integrations-btn")?.addEventListener("click", {
            Router.navigateTo("integrations")
        })
    }

    /**
     * Phase 1: Immediate display from sessionStorage.
     * Renders metrics/chart/radar BEFORE API loads.
     * Requirements: 2.1, 2.6
     */
    private fun immediateRestoreFromSession() {
        val data = AnalysisStateManager.restore() ?: return
        renderMetrics(data)
        AnalysisVelocityChart.render(data.velocityTrend)
        AnalysisBottleneckRadar.render(data.bottlenecks)
    }

    internal fun loadAnalysisData() {
        val projectKey = ApiClient.getProjectKey() ?: return
        hideAnalysisError()
        scope.launch {
            try {
                val response = ApiClient.get("/api/projects/$projectKey/analysis")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val data = json.decodeFromString<ProjectAnalysisResponse>(body)
                AnalysisStateManager.save(data)
                renderMetrics(data)
                AnalysisVelocityChart.render(data.velocityTrend)
                AnalysisBottleneckRadar.render(data.bottlenecks)
            } catch (e: Exception) {
                console.log("[AnalysisPage] Failed to load: ${e.message}")
                showAnalysisError("Failed to load analysis data: ${e.message}")
            }
        }
    }

    private fun showAnalysisError(message: String) {
        val errorEl = document.getElementById("analysis-error") as? HTMLElement
        val msgEl = document.getElementById("analysis-error-msg") as? HTMLElement
        errorEl?.style?.display = ""
        msgEl?.textContent = message
    }

    private fun hideAnalysisError() {
        (document.getElementById("analysis-error") as? HTMLElement)
            ?.style?.display = "none"
    }

    private fun renderMetrics(data: ProjectAnalysisResponse) {
        document.getElementById("val-total-tickets")?.textContent = "${data.totalTickets}"
        val resEl = document.getElementById("val-resolution-rate") as? HTMLElement
        resEl?.textContent = "${kotlin.math.round(data.resolutionRate).toInt()}%"
        resEl?.style?.color = "var(--primary)"
        document.getElementById("val-cycle-time")?.textContent =
            data.cycleTimeDays.asDynamic().toFixed(1) as String
        val velEl = document.getElementById("val-ai-velocity") as? HTMLElement
        velEl?.textContent = "❖ ${data.aiVelocity.asDynamic().toFixed(1)}"
        velEl?.style?.color = "var(--accent)"
    }
}
