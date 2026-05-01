# Project Analysis — Design

---

## Project Analysis (MH4) — Sprint Analytics Module

Trang Project Analysis hiển thị phân tích sprint chi tiết:

**HTML Template:**
```html
<!-- templates/analysis.html -->
<div class="analysis-grid">
    <!-- 4 Metric Cards -->
    <div class="metrics-row">
        <div class="glass-card stat-card">
            <div class="label-small">TOTAL TICKETS</div>
            <div class="stat-value" id="val-total-tickets">--</div>
        </div>
        <div class="glass-card stat-card">
            <div class="label-small">RESOLUTION RATE</div>
            <div class="stat-value" id="val-resolution-rate">--</div>
        </div>
        <div class="glass-card stat-card">
            <div class="label-small">CYCLE TIME (DAYS)</div>
            <div class="stat-value" id="val-cycle-time">--</div>
        </div>
        <div class="glass-card stat-card">
            <div class="label-small">AI VELOCITY</div>
            <div class="stat-value" id="val-ai-velocity">--</div>
        </div>
    </div>

    <!-- Velocity Trend Chart -->
    <div class="glass-card">
        <div class="label-small">VELOCITY TREND</div>
        <div id="velocity-bar-container" class="velocity-chart"></div>
    </div>

    <!-- Bottleneck Radar Panel -->
    <div class="glass-card">
        <div class="label-small">BOTTLENECK RADAR</div>
        <div id="bottleneck-list"></div>
    </div>

    <!-- Dive Into Reports -->
    <button id="btn-dive-reports" class="btn-vibrant">DIVE INTO REPORTS</button>
</div>
```

**Kotlin/JS Controller:**
```kotlin
// AnalysisPage.kt
object AnalysisPage : Page {
    override fun render(container: Element) {
        container.innerHTML = loadTemplate("analysis")

        document.getElementById("btn-dive-reports")?.addEventListener("click", {
            Router.navigateTo("ticket_intelligence")
        })

        MainScope().launch { loadProjectAnalysis() }
    }

    private suspend fun loadProjectAnalysis() {
        val projectKey = AuthState.projectKey ?: return
        val data = ApiClient.get<ProjectAnalysisResponse>(
            "/api/projects/$projectKey/analysis"
        )
        renderMetrics(data)
        renderVelocityChart(data.velocityTrend)
        renderBottleneckRadar(data.bottlenecks)
    }

    private fun renderMetrics(data: ProjectAnalysisResponse) {
        document.getElementById("val-total-tickets")?.textContent = "${data.totalTickets}"
        document.getElementById("val-resolution-rate")?.apply {
            textContent = "${data.resolutionRate.roundToInt()}%"
            (this as? HTMLElement)?.style?.color = "var(--primary)"
        }
        document.getElementById("val-cycle-time")?.textContent = "${data.cycleTimeDays}"
        document.getElementById("val-ai-velocity")?.apply {
            textContent = "❖ ${data.aiVelocity}"
            (this as? HTMLElement)?.style?.color = "var(--accent)"
        }
    }

    private fun renderVelocityChart(sprints: List<SprintVelocity>) {
        val container = document.getElementById("velocity-bar-container") ?: return
        container.innerHTML = ""
        val maxPoints = sprints.maxOfOrNull { it.storyPoints } ?: 1.0

        sprints.forEach { sprint ->
            val heightPercent = (sprint.storyPoints / maxPoints) * 100
            val bar = document.createElement("div").apply {
                className = "v-bar"
                (this as HTMLElement).style.height = "${heightPercent}%"
                setAttribute("data-tooltip", "${sprint.sprintName}: ${sprint.storyPoints} SP")
                innerHTML = """
                    <span class="v-bar-label">${sprint.sprintName.replace("Sprint ", "S")}</span>
                    <span class="v-bar-value">${sprint.storyPoints}</span>
                """.trimIndent()
            }
            // Hover effect qua addEventListener
            bar.addEventListener("mouseenter", { (bar as HTMLElement).classList.add("hovered") })
            bar.addEventListener("mouseleave", { (bar as HTMLElement).classList.remove("hovered") })
            container.appendChild(bar)
        }
    }

    private fun renderBottleneckRadar(bottlenecks: List<BottleneckAlert>) {
        val container = document.getElementById("bottleneck-list") ?: return
        container.innerHTML = ""
        bottlenecks.forEach { alert ->
            val isRisk = alert.type == "RISK"
            val el = document.createElement("div").apply {
                className = "ai-alert ${if (isRisk) "risk" else "optimization"}"
                innerHTML = """
                    <span class="alert-icon ${if (isRisk) "risk" else "optimization"}">
                        ${if (isRisk) "⚠️" else "🚀"}
                    </span>
                    <div class="alert-info">
                        <h4>${alert.title}</h4>
                        <p class="alert-desc">${alert.description}</p>
                        <span class="alert-severity ${alert.severity.lowercase()}">${alert.severity}</span>
                    </div>
                """.trimIndent()
            }
            container.appendChild(el)
        }
    }
}
```

*(Validates: Req 4.2, 4.3, 4.4, 4.5)*

---

## Project Analysis — Progressive Display (MH4 mở rộng)

Project Analysis bổ sung cập nhật dữ liệu tăng dần khi Batch Scan đang chạy. Frontend poll `GET /api/projects/{key}/analysis` định kỳ.

### Progressive Display HTML (bổ sung vào analysis.html)

```html
<!-- Scan status indicator — thêm vào đầu analysis-grid -->
<div class="analysis-scan-indicator" id="analysis-scan-indicator" style="display:none;">
    <span class="scan-dot scanning"></span>
    <span id="analysis-scan-text">Đang quét...</span>
    <span class="label-small" id="analysis-scan-percent">0%</span>
</div>
```

### Progressive Display Kotlin/JS (bổ sung vào AnalysisPage.kt)

```kotlin
// AnalysisPage.kt — thêm progressive display logic

private var analysisPollingJob: Job? = null

override fun render(container: Element) {
    container.innerHTML = loadTemplate("analysis")
    // ... existing setup ...
    MainScope().launch {
        loadProjectAnalysis()
        checkScanAndPoll()
    }
}

override fun cleanup() {
    analysisPollingJob?.cancel()
}

private suspend fun checkScanAndPoll() {
    val projectKey = ApiClient.getProjectKey() ?: return
    try {
        val scanStatus = ApiClient.get<ScanStatusResponse>(
            "/api/projects/$projectKey/scan/status"
        )
        updateAnalysisScanIndicator(scanStatus)
        if (scanStatus.status == ScanStatus.SCANNING) {
            startAnalysisPolling()
        }
    } catch (_: Exception) { /* no scan */ }
}

private fun startAnalysisPolling() {
    analysisPollingJob?.cancel()
    analysisPollingJob = MainScope().launch {
        while (isActive) {
            delay(5000) // Poll every 5 seconds (metrics change less frequently)
            val projectKey = ApiClient.getProjectKey() ?: break
            try {
                // Refresh metrics
                val data = ApiClient.get<ProjectAnalysisResponse>(
                    "/api/projects/$projectKey/analysis"
                )
                renderMetrics(data)
                renderVelocityChart(data.velocityTrend)
                renderBottleneckRadar(data.bottlenecks)

                // Check scan status
                val scanStatus = ApiClient.get<ScanStatusResponse>(
                    "/api/projects/$projectKey/scan/status"
                )
                updateAnalysisScanIndicator(scanStatus)
                if (scanStatus.status != ScanStatus.SCANNING) break
            } catch (_: Exception) { break }
        }
    }
}

private fun updateAnalysisScanIndicator(status: ScanStatusResponse) {
    val indicator = document.getElementById("analysis-scan-indicator") as? HTMLElement
    when (status.status) {
        ScanStatus.SCANNING -> {
            indicator?.style?.display = "flex"
            document.getElementById("analysis-scan-text")?.textContent = "Đang quét..."
            document.getElementById("analysis-scan-percent")?.textContent = "${status.progressPercent}%"
        }
        ScanStatus.COMPLETED -> {
            indicator?.style?.display = "flex"
            document.getElementById("analysis-scan-text")?.textContent = "Đã hoàn tất"
            document.getElementById("analysis-scan-percent")?.textContent = "100%"
        }
        else -> {
            indicator?.style?.display = if (status.totalTickets > 0) "flex" else "none"
            document.getElementById("analysis-scan-text")?.textContent = "Chưa quét"
        }
    }
}
```

*(Validates: Req 4.6–4.8)*
