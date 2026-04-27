# Dashboard — Design

---

## Dashboard (MH2) — UI Components

Dashboard hiển thị tổng quan dự án. HTML template chứa layout, Kotlin/JS controller bind dữ liệu:

**HTML Template:**
```html
<!-- templates/dashboard.html -->
<div class="dashboard-grid">
    <!-- Hero Metrics Row -->
    <div class="metrics-row">
        <div class="glass-card stat-card">
            <div class="label-small">PROJECT AI HEALTH</div>
            <div class="stat-value" id="stat-ai-health">--</div>
        </div>
        <div class="glass-card stat-card">
            <div class="label-small">ACTIVE KNOWLEDGE NODES</div>
            <div class="stat-value" id="stat-nodes">--</div>
        </div>
        <div class="glass-card stat-card">
            <div class="label-small">NEURAL VELOCITY</div>
            <div class="stat-value" id="stat-velocity">--</div>
        </div>
    </div>

    <!-- Relationship Network Preview -->
    <div class="glass-card">
        <div class="label-small">RELATIONSHIP NETWORK</div>
        <svg id="network-preview-svg" viewBox="0 0 300 130" width="100%"></svg>
        <button id="btn-view-graph" class="btn-vibrant" style="margin-top:16px;">VIEW GRAPH</button>
    </div>

    <!-- AI Estimation Drift Chart -->
    <div class="glass-card">
        <div class="label-small">AI ESTIMATION DRIFT</div>
        <svg id="drift-chart-svg" viewBox="0 0 300 120" width="100%"></svg>
        <button id="btn-analysis-drift" class="btn-vibrant" style="margin-top:16px;">ANALYSIS DRIFT</button>
    </div>

    <!-- Neural Console -->
    <div class="glass-card neural-console" id="neural-console"></div>
</div>
```

**Kotlin/JS Controller:**
```kotlin
// DashboardPage.kt
object DashboardPage : Page {
    override fun render(container: Element) {
        container.innerHTML = loadTemplate("dashboard")

        // Navigation buttons
        document.getElementById("btn-view-graph")?.addEventListener("click", {
            Router.navigateTo("knowledge_graph")
        })
        document.getElementById("btn-analysis-drift")?.addEventListener("click", {
            Router.navigateTo("analysis")
        })

        // Render static SVG previews
        renderNetworkPreview()
        renderDriftChart()

        // Load data from API
        MainScope().launch { loadDashboardData() }
    }

    private suspend fun loadDashboardData() {
        val projectKey = AuthState.projectKey ?: return
        val metrics = ApiClient.get<ProjectAnalysisResponse>(
            "/api/projects/$projectKey/analysis"
        )
        // Bind data to DOM
        document.getElementById("stat-ai-health")?.innerHTML =
            "${metrics.aiVelocity}% <span style='font-size:14px;color:var(--primary);'>+2.1%</span>"
        document.getElementById("stat-nodes")?.textContent = "${metrics.totalTickets}"
        document.getElementById("stat-velocity")?.innerHTML =
            "❖ ${metrics.velocity} <span style='font-size:14px;color:var(--secondary);'>STABLE</span>"
    }

    private fun renderNetworkPreview() {
        val svg = document.getElementById("network-preview-svg") ?: return
        val ns = "http://www.w3.org/2000/svg"
        val nodes = listOf(
            Triple(60, 35, "#2dfecf"), Triple(150, 25, "#3386ff"),
            Triple(240, 40, "#be9dff"), Triple(100, 80, "#2dfecf")
        )
        nodes.forEach { (x, y, color) ->
            val circle = document.createElementNS(ns, "circle")
            circle.setAttribute("cx", "$x"); circle.setAttribute("cy", "$y")
            circle.setAttribute("r", "8"); circle.setAttribute("fill", color)
            circle.setAttribute("opacity", "0.8")
            svg.appendChild(circle)
        }
    }

    private fun renderDriftChart() {
        val svg = document.getElementById("drift-chart-svg") ?: return
        val ns = "http://www.w3.org/2000/svg"
        val line = document.createElementNS(ns, "polyline")
        line.setAttribute("points", "10,100 50,60 90,75 130,30 170,45 210,15 250,35 290,20")
        line.setAttribute("fill", "none")
        line.setAttribute("stroke", "#be9dff")
        line.setAttribute("stroke-width", "2")
        svg.appendChild(line)
    }
}
```

**Hero Metrics Row:** 3 `.glass-card` elements nằm ngang trong `.metrics-row` (CSS flexbox). *(Validates: Req 2.1)*

**Relationship Network Preview Card:** `.glass-card` chứa `<svg>` preview thu nhỏ của đồ thị mạng lưới, kèm button "VIEW GRAPH" điều hướng đến `#knowledge_graph`. *(Validates: Req 2.2)*

**AI Estimation Drift Chart:** `.glass-card` chứa `<svg>` biểu đồ line/area hiển thị sai lệch ước lượng AI qua thời gian, kèm button "ANALYSIS DRIFT" điều hướng đến `#analysis`. *(Validates: Req 2.3)*

**Neural Console:** `.neural-console` div hiển thị tối thiểu 3 mục log real-time với format: `[HH:MM:SS] TAG message`. Tags: `AI_SYNC`, `KB_WRITE`, `HEARTBEAT`. Kotlin/JS tạo console lines qua `document.createElement`.

---

## Dashboard — Batch Project Scan Controls (MH2 mở rộng)

Dashboard bổ sung khu vực điều khiển Batch Scan bên dưới Hero Metrics Row. Gồm: nút hành động (START/PAUSE/RESUME/CANCEL), progress bar, và scan log.

### Scan Controls HTML Template (bổ sung vào dashboard.html)

```html
<!-- Batch Scan Section — thêm vào dashboard-grid -->
<div class="glass-card scan-control-card" id="scan-section">
    <div class="scan-header">
        <div class="label-small">BATCH PROJECT SCAN</div>
        <div class="scan-status-badge" id="scan-status-badge">IDLE</div>
    </div>

    <!-- Action Buttons -->
    <div class="scan-actions" id="scan-actions">
        <button id="btn-scan-start" class="btn-vibrant">START SCAN</button>
        <button id="btn-scan-pause" class="btn-outline" style="display:none;">PAUSE</button>
        <button id="btn-scan-resume" class="btn-vibrant" style="display:none;">RESUME</button>
        <button id="btn-scan-cancel" class="btn-danger" style="display:none;">CANCEL</button>
    </div>

    <!-- Progress Bar -->
    <div class="scan-progress" id="scan-progress" style="display:none;">
        <div class="neural-loader">
            <div class="neural-progress" id="scan-progress-bar"></div>
        </div>
        <div class="scan-progress-text">
            <span id="scan-progress-percent">0%</span>
            <span id="scan-progress-count">0 / 0 tickets</span>
        </div>
    </div>

    <!-- Scan Log -->
    <div class="scan-log" id="scan-log" style="display:none;">
        <div class="label-small">SCAN LOG</div>
        <div class="neural-console scan-log-entries" id="scan-log-entries">
            <!-- Entries injected by Kotlin/JS -->
        </div>
    </div>
</div>
```

### Scan Controls Kotlin/JS Controller (bổ sung vào DashboardPage.kt)

```kotlin
// DashboardPage.kt — thêm scan control logic

private var scanPollingJob: Job? = null

private fun initScanControls() {
    document.getElementById("btn-scan-start")?.addEventListener("click", {
        MainScope().launch { startScan() }
    })
    document.getElementById("btn-scan-pause")?.addEventListener("click", {
        MainScope().launch { pauseScan() }
    })
    document.getElementById("btn-scan-resume")?.addEventListener("click", {
        MainScope().launch { resumeScan() }
    })
    document.getElementById("btn-scan-cancel")?.addEventListener("click", {
        MainScope().launch { cancelScan() }
    })

    // Load initial scan status
    MainScope().launch { loadScanStatus() }
}

private suspend fun loadScanStatus() {
    val projectKey = ApiClient.getProjectKey() ?: return
    try {
        val status = ApiClient.get<ScanStatusResponse>(
            "/api/projects/$projectKey/scan/status"
        )
        updateScanUI(status)
        if (status.status == ScanStatus.SCANNING) {
            startScanPolling()
        }
    } catch (_: Exception) {
        updateScanUI(ScanStatusResponse(projectKey, ScanStatus.IDLE, 0, 0, 0, null, null, null))
    }
}

private suspend fun startScan() {
    val projectKey = ApiClient.getProjectKey() ?: return
    val status = ApiClient.post<ScanStatusResponse>(
        "/api/projects/$projectKey/scan/start"
    )
    updateScanUI(status)
    startScanPolling()
}

private suspend fun pauseScan() {
    val projectKey = ApiClient.getProjectKey() ?: return
    scanPollingJob?.cancel()
    val status = ApiClient.post<ScanStatusResponse>(
        "/api/projects/$projectKey/scan/pause"
    )
    updateScanUI(status)
}

private suspend fun resumeScan() {
    val projectKey = ApiClient.getProjectKey() ?: return
    val status = ApiClient.post<ScanStatusResponse>(
        "/api/projects/$projectKey/scan/resume"
    )
    updateScanUI(status)
    startScanPolling()
}

private suspend fun cancelScan() {
    val projectKey = ApiClient.getProjectKey() ?: return
    scanPollingJob?.cancel()
    val status = ApiClient.post<ScanStatusResponse>(
        "/api/projects/$projectKey/scan/cancel"
    )
    updateScanUI(status)
}

private fun startScanPolling() {
    scanPollingJob?.cancel()
    scanPollingJob = MainScope().launch {
        while (isActive) {
            delay(2000)
            val projectKey = ApiClient.getProjectKey() ?: break
            try {
                val status = ApiClient.get<ScanStatusResponse>(
                    "/api/projects/$projectKey/scan/status"
                )
                updateScanUI(status)
                if (status.status != ScanStatus.SCANNING) break
            } catch (_: Exception) { break }
        }
    }
}

private fun updateScanUI(status: ScanStatusResponse) {
    val btnStart = document.getElementById("btn-scan-start") as? HTMLElement
    val btnPause = document.getElementById("btn-scan-pause") as? HTMLElement
    val btnResume = document.getElementById("btn-scan-resume") as? HTMLElement
    val btnCancel = document.getElementById("btn-scan-cancel") as? HTMLElement
    val progressDiv = document.getElementById("scan-progress") as? HTMLElement
    val logDiv = document.getElementById("scan-log") as? HTMLElement
    val badge = document.getElementById("scan-status-badge") as? HTMLElement

    badge?.textContent = status.status.name
    badge?.className = "scan-status-badge ${status.status.name.lowercase()}"

    when (status.status) {
        ScanStatus.IDLE, ScanStatus.COMPLETED, ScanStatus.CANCELLED -> {
            btnStart?.style?.display = "inline-flex"
            btnPause?.style?.display = "none"
            btnResume?.style?.display = "none"
            btnCancel?.style?.display = "none"
            progressDiv?.style?.display = if (status.status == ScanStatus.COMPLETED) "block" else "none"
        }
        ScanStatus.SCANNING -> {
            btnStart?.style?.display = "none"
            btnPause?.style?.display = "inline-flex"
            btnResume?.style?.display = "none"
            btnCancel?.style?.display = "inline-flex"
            progressDiv?.style?.display = "block"
            logDiv?.style?.display = "block"
        }
        ScanStatus.PAUSED -> {
            btnStart?.style?.display = "none"
            btnPause?.style?.display = "none"
            btnResume?.style?.display = "inline-flex"
            btnCancel?.style?.display = "inline-flex"
            progressDiv?.style?.display = "block"
            logDiv?.style?.display = "block"
        }
    }

    (document.getElementById("scan-progress-bar") as? HTMLElement)?.style?.width = "${status.progressPercent}%"
    document.getElementById("scan-progress-percent")?.textContent = "${status.progressPercent}%"
    document.getElementById("scan-progress-count")?.textContent =
        "${status.processedCount} / ${status.totalTickets} tickets"

    renderScanLog(status.recentLog)
}

private fun renderScanLog(entries: List<ScanLogEntryResponse>) {
    val container = document.getElementById("scan-log-entries") ?: return
    container.innerHTML = ""
    entries.forEach { entry ->
        val statusClass = when (entry.status) {
            "COMPLETED" -> "log-success"
            "FAILED" -> "log-error"
            else -> "log-info"
        }
        val line = document.createElement("div").apply {
            className = "console-line $statusClass"
            innerHTML = """
                <span class="log-time">[${entry.timestamp.takeLast(8)}]</span>
                <span class="log-ticket">${entry.ticketId}</span>
                <span class="log-status">${entry.status}</span>
                <span class="log-msg">${entry.message}</span>
            """.trimIndent()
        }
        container.appendChild(line)
    }
}
```

### Project Switch — Auto-Pause Scan

Khi người dùng chuyển project qua Navbar Project Badge, `DashboardPage` tự động pause scan hiện tại:

```kotlin
// Navbar.kt — trong project switch handler
fun onProjectSwitch(newProjectKey: String) {
    val currentKey = ApiClient.getProjectKey()
    if (currentKey != null && currentKey != newProjectKey) {
        MainScope().launch {
            try {
                ApiClient.post<ScanStatusResponse>(
                    "/api/projects/$currentKey/scan/pause"
                )
            } catch (_: Exception) { /* ignore if no active scan */ }
        }
    }
    ApiClient.setProjectKey(newProjectKey)
    Router.navigateTo("dashboard")
}
```

*(Validates: Req 2.9–2.15)*

---

## Liên kết Spec

> **Deep Analysis Enhancement (spec `ticket-intelligence`, phần Deep Analysis)**: Dashboard START SCAN gọi `BatchScanEngine.processTicket()` → `AIOrchestrator.analyzeTicket()`. Deep Analysis nâng cấp `analyzeTicket()` (Jira extraction + prompt + data model) — scan tự động sử dụng pipeline mới mà không cần thay đổi code Dashboard hay BatchScanEngine.
