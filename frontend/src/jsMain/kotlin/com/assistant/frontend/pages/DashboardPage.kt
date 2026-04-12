package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.DashboardAnalysis
import com.assistant.frontend.pages.dashboard.DashboardMultiScanProgress
import com.assistant.frontend.pages.dashboard.DashboardNeuralConsole
import com.assistant.frontend.pages.dashboard.DashboardScanControl
import com.assistant.frontend.pages.dashboard.DashboardSvgCharts
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
 * Dashboard page — Project Neural Metrics (MH2).
 * API: GET /api/projects/{projectKey}/analysis → DashboardAnalysis
 */
object DashboardPage {

    internal val scope = MainScope()
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun render(container: Element) {
        container.innerHTML = ""
        cleanup()
        scope.launch {
            val html = ApiClient.loadTemplate("dashboard")
            container.innerHTML = html
            bindEvents()
            DashboardSvgCharts.renderNetworkPreview()
            loadDashboardData()
            DashboardScanControl.bindControls()
            DashboardScanControl.loadScanStatus()
        }
    }

    fun cleanup() {
        DashboardScanControl.stopPolling()
        DashboardMultiScanProgress.stopActivePolling()
    }

    private fun bindEvents() {
        document.getElementById("btnViewGraph")?.addEventListener("click", {
            Router.navigateTo("knowledge_graph")
        })
        document.getElementById("btnAnalysisDrift")?.addEventListener("click", {
            Router.navigateTo("analysis")
        })
        document.getElementById("dashboard-retry-btn")?.addEventListener("click", {
            loadDashboardData()
        })
        document.getElementById("dashboard-integrations-btn")?.addEventListener("click", {
            Router.navigateTo("integrations")
        })
    }

    private fun loadDashboardData() {
        val projectKey = ApiClient.getProjectKey()
        if (projectKey.isNullOrBlank()) {
            showDashboardError("No project selected. Please select a project from the navbar.")
            return
        }
        hideDashboardError()
        scope.launch {
            try {
                val response = ApiClient.get("/api/projects/$projectKey/analysis")
                if (ApiClient.handleUnauthorized(response)) return@launch
                val body = response.bodyAsText()
                val analysis = json.decodeFromString<DashboardAnalysis>(body)
                updateHeroMetrics(analysis)
                DashboardSvgCharts.renderDriftChart(analysis.velocityTrend)
                DashboardNeuralConsole.update(analysis)
            } catch (e: Exception) {
                console.log("[DashboardPage] Failed to load data: ${e.message}")
                showDashboardError("Failed to load dashboard data: ${e.message}")
            }
        }
    }

    private fun showDashboardError(message: String) {
        val errorEl = document.getElementById("dashboard-error") as? HTMLElement
        val msgEl = document.getElementById("dashboard-error-msg") as? HTMLElement
        errorEl?.style?.display = ""
        msgEl?.textContent = message
    }

    private fun hideDashboardError() {
        (document.getElementById("dashboard-error") as? HTMLElement)
            ?.style?.display = "none"
    }

    private fun updateHeroMetrics(data: DashboardAnalysis) {
        val healthEl = document.getElementById("stat-ai-health-value")
        healthEl?.innerHTML =
            "${data.resolutionRate.asDynamic().toFixed(1)}% <span style=\"font-size:14px;color:var(--primary);\">RES</span>"

        val nodesEl = document.getElementById("stat-kb-nodes-value")
        nodesEl?.innerHTML =
            "${HtmlUtils.formatNumber(data.totalTickets)} <span style=\"font-size:14px;opacity:0.4;\">tickets</span>"

        val velEl = document.getElementById("stat-velocity-value")
        velEl?.innerHTML =
            "&#10070; ${data.aiVelocity.asDynamic().toFixed(1)} <span style=\"font-size:14px;color:var(--secondary);\">VEL</span>"
    }
}
