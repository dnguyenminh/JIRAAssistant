# Ticket Intelligence — Design

## Ticket Intelligence (MH5) — Frontend-Backend Real-Time Progress — Polling Strategy

Khi Frontend (Kotlin/JS) gửi request phân tích AI qua `ApiClient` (ktor-client-js) đến endpoint `/api/analysis/{ticketId}`, Backend xử lý đồng bộ (synchronous) và trả về kết quả khi hoàn tất. Frontend sử dụng **client-side progress simulation** kết hợp **long-polling fallback** qua Kotlin/JS coroutines:

1. Kotlin/JS coroutine bắt đầu animation progress bar ngay khi gửi request (0% → 95% trong ~10s) qua `delay()` loop + DOM update
2. Khi response trả về, progress nhảy lên 100% và hiển thị kết quả
3. Nếu request kéo dài hơn 15 giây, coroutine gửi `GET /api/analysis/{ticketId}/status` (polling mỗi 3s) để kiểm tra trạng thái xử lý
4. Response format cho status endpoint:

```kotlin
@Serializable
data class AnalysisStatus(
    val ticketId: String,
    val phase: String,       // "METADATA", "AI_ANALYZING", "KB_SYNCING", "COMPLETE"
    val progressPercent: Int  // 0-100
)
```

**HTML Template:**
```html
<!-- templates/ticket-intelligence.html -->
<div class="ticket-analysis">
    <div class="glass-card">
        <label class="label-small">TICKET ID</label>
        <div style="display:flex;gap:12px;">
            <input type="text" id="input-ticket-id" class="glass-input" placeholder="PROJ-123">
            <button id="btn-analyze" class="btn-vibrant">ANALYZE</button>
        </div>
    </div>

    <!-- Progress -->
    <div id="analysis-progress" style="display:none;">
        <div class="neural-loader"><div class="neural-progress" id="analysis-bar"></div></div>
        <span class="status-ticker" id="analysis-status"></span>
    </div>

    <!-- Results (3 tabs) -->
    <div id="analysis-results" style="display:none;">
        <div class="tab-bar">
            <button class="tab-btn active" data-tab="summary">SUMMARY</button>
            <button class="tab-btn" data-tab="estimation">ESTIMATION</button>
            <button class="tab-btn" data-tab="relations">RELATIONS</button>
        </div>
        <div id="tab-summary" class="glass-card tab-content"></div>
        <div id="tab-estimation" class="glass-card tab-content" style="display:none;"></div>
        <div id="tab-relations" class="glass-card tab-content" style="display:none;"></div>
    </div>
</div>
```

**Kotlin/JS Controller:**
```kotlin
// TicketIntelligencePage.kt
object TicketIntelligencePage : Page {
    override fun render(container: Element) {
        container.innerHTML = loadTemplate("ticket-intelligence")

        document.getElementById("btn-analyze")?.addEventListener("click", {
            val ticketId = (document.getElementById("input-ticket-id") as? HTMLInputElement)?.value ?: ""
            if (ticketId.isNotBlank()) startAnalysis(ticketId)
        })

        // Tab switching
        document.querySelectorAll(".tab-btn").asList().forEach { btn ->
            btn.addEventListener("click", {
                switchTab((btn as HTMLElement).getAttribute("data-tab") ?: "summary")
            })
        }
    }

    private fun startAnalysis(ticketId: String) {
        val progressDiv = document.getElementById("analysis-progress") as? HTMLElement
        val resultsDiv = document.getElementById("analysis-results") as? HTMLElement
        progressDiv?.style?.display = "block"
        resultsDiv?.style?.display = "none"

        MainScope().launch {
            // Simulate progress
            val progressJob = launch {
                var progress = 0
                while (progress < 95) {
                    delay(100)
                    progress += 1
                    (document.getElementById("analysis-bar") as? HTMLElement)?.style?.width = "${progress}%"
                }
            }

            // Actual API call
            try {
                val result = ApiClient.get<AnalysisResult>("/api/analysis/$ticketId")
                progressJob.cancel()
                (document.getElementById("analysis-bar") as? HTMLElement)?.style?.width = "100%"
                document.getElementById("analysis-status")?.textContent = "COMPLETE"

                delay(500) // Brief pause at 100%
                progressDiv?.style?.display = "none"
                resultsDiv?.style?.display = "block"
                renderResults(result)
            } catch (e: Exception) {
                progressJob.cancel()
                document.getElementById("analysis-status")?.textContent = "ERROR: ${e.message}"
            }
        }
    }

    private fun renderResults(result: AnalysisResult) {
        document.getElementById("tab-summary")?.innerHTML = """
            <h3>${result.ticketKey}</h3>
            <p>${result.summary}</p>
        """.trimIndent()
        document.getElementById("tab-estimation")?.innerHTML = """
            <div class="stat-value">${result.estimatedPoints} SP</div>
            <p>${result.estimationRationale}</p>
        """.trimIndent()
    }

    private fun switchTab(tabName: String) {
        document.querySelectorAll(".tab-btn").asList().forEach {
            (it as HTMLElement).classList.remove("active")
        }
        document.querySelector("[data-tab='$tabName']")?.let {
            (it as HTMLElement).classList.add("active")
        }
        document.querySelectorAll(".tab-content").asList().forEach {
            (it as HTMLElement).style.display = "none"
        }
        (document.getElementById("tab-$tabName") as? HTMLElement)?.style?.display = "block"
    }
}
```

*(Validates: Req 13.3)*

---

## Ticket Intelligence — Combobox & Dynamic Actions (MH5 mở rộng)

Ticket Intelligence thay thế text input bằng searchable combobox, hiển thị trạng thái phân tích ticket, và nút hành động động.

### Combobox HTML Template (thay thế input cũ trong ticket-intelligence.html)

```html
<!-- templates/ticket-intelligence.html — updated -->
<div class="ticket-analysis">
    <div class="glass-card">
        <label class="label-small">SELECT TICKET</label>
        <div class="combobox-wrapper" id="combobox-wrapper">
            <input type="text" id="ticket-combobox-input" class="glass-input"
                   placeholder="Search by ticket ID or summary..."
                   autocomplete="off">
            <div class="combobox-dropdown" id="combobox-dropdown" style="display:none;">
                <!-- Ticket options injected by Kotlin/JS -->
            </div>
        </div>

        <!-- Ticket Analysis Status -->
        <div class="ticket-status-row" id="ticket-status-row" style="display:none;">
            <span class="ticket-status-badge" id="ticket-status-badge"></span>
            <span class="ticket-status-text" id="ticket-status-text"></span>
        </div>

        <!-- Dynamic Action Button -->
        <div class="ticket-action-row" id="ticket-action-row" style="display:none;">
            <button id="btn-ticket-action" class="btn-vibrant">ANALYZE</button>
        </div>
    </div>

    <!-- Progress (existing) -->
    <div id="analysis-progress" style="display:none;">
        <div class="neural-loader"><div class="neural-progress" id="analysis-bar"></div></div>
        <span class="status-ticker" id="analysis-status"></span>
    </div>

    <!-- Results 3 tabs (existing) -->
    <div id="analysis-results" style="display:none;">
        <!-- ... existing tab structure ... -->
    </div>
</div>
```

### Combobox Kotlin/JS Controller (bổ sung vào TicketIntelligencePage.kt)

```kotlin
// TicketIntelligencePage.kt — updated with combobox

private var allTickets: List<TicketAnalysisStatus> = emptyList()
private var selectedTicket: TicketAnalysisStatus? = null
private var debounceJob: Job? = null

override fun render(container: Element) {
    container.innerHTML = loadTemplate("ticket-intelligence")

    val input = document.getElementById("ticket-combobox-input") as? HTMLInputElement

    // Debounced search input
    input?.addEventListener("input", {
        debounceJob?.cancel()
        debounceJob = MainScope().launch {
            delay(300) // 300ms debounce
            val query = input.value.trim()
            filterAndShowDropdown(query)
        }
    })

    // Show dropdown on focus
    input?.addEventListener("focus", {
        if (allTickets.isNotEmpty()) showDropdown()
    })

    // Hide dropdown on outside click
    document.addEventListener("click", { event ->
        val wrapper = document.getElementById("combobox-wrapper")
        if (wrapper != null && !(wrapper as HTMLElement).contains(event.target as? org.w3c.dom.Node)) {
            hideDropdown()
        }
    })

    // Load ticket list
    MainScope().launch { loadTicketList() }

    // Tab switching (existing)
    setupTabSwitching()
}

private suspend fun loadTicketList() {
    val projectKey = ApiClient.getProjectKey() ?: return
    allTickets = ApiClient.get<List<TicketAnalysisStatus>>(
        "/api/projects/$projectKey/tickets/analysis-status"
    )
}

private fun filterAndShowDropdown(query: String) {
    val filtered = if (query.isEmpty()) allTickets
    else allTickets.filter { ticket ->
        ticket.ticketId.contains(query, ignoreCase = true) ||
        ticket.ticketSummary.contains(query, ignoreCase = true)
    }
    renderDropdown(filtered)
    showDropdown()
}

private fun renderDropdown(tickets: List<TicketAnalysisStatus>) {
    val dropdown = document.getElementById("combobox-dropdown") ?: return
    dropdown.innerHTML = ""
    tickets.forEach { ticket ->
        val stateClass = ticket.analysisState.name.lowercase().replace("_", "-")
        val option = document.createElement("div").apply {
            className = "combobox-option"
            innerHTML = """
                <span class="ticket-id">${ticket.ticketId}</span>
                <span class="ticket-summary">${ticket.ticketSummary.take(60)}</span>
                <span class="analysis-badge $stateClass">${formatState(ticket.analysisState)}</span>
            """.trimIndent()
        }
        option.addEventListener("click", {
            selectTicket(ticket)
            hideDropdown()
        })
        dropdown.appendChild(option)
    }
}

private fun selectTicket(ticket: TicketAnalysisStatus) {
    selectedTicket = ticket
    (document.getElementById("ticket-combobox-input") as? HTMLInputElement)?.value =
        "${ticket.ticketId} — ${ticket.ticketSummary.take(50)}"

    // Show status badge
    val statusRow = document.getElementById("ticket-status-row") as? HTMLElement
    statusRow?.style?.display = "flex"
    val badge = document.getElementById("ticket-status-badge") as? HTMLElement
    val stateClass = ticket.analysisState.name.lowercase().replace("_", "-")
    badge?.className = "ticket-status-badge $stateClass"
    badge?.textContent = formatState(ticket.analysisState)

    // Show dynamic action button
    val actionRow = document.getElementById("ticket-action-row") as? HTMLElement
    actionRow?.style?.display = "flex"
    val btn = document.getElementById("btn-ticket-action") as? HTMLElement
    when (ticket.analysisState) {
        TicketAnalysisState.NOT_ANALYZED -> {
            btn?.textContent = "ANALYZE"
            btn?.className = "btn-vibrant"
            btn?.removeAttribute("disabled")
        }
        TicketAnalysisState.ANALYZED -> {
            btn?.textContent = "RE-ANALYZE"
            btn?.className = "btn-outline"
            btn?.removeAttribute("disabled")
        }
        TicketAnalysisState.HAS_UPDATES -> {
            btn?.textContent = "RE-ANALYZE"
            btn?.className = "btn-vibrant"
            btn?.removeAttribute("disabled")
        }
        TicketAnalysisState.ANALYZING -> {
            btn?.textContent = "ANALYZING..."
            btn?.className = "btn-vibrant disabled"
            btn?.setAttribute("disabled", "true")
        }
    }

    // Bind action
    btn?.onclick = {
        if (ticket.analysisState != TicketAnalysisState.ANALYZING) {
            val forceReanalyze = ticket.analysisState != TicketAnalysisState.NOT_ANALYZED
            startAnalysis(ticket.ticketId, forceReanalyze)
        }
    }
}

private fun formatState(state: TicketAnalysisState): String = when (state) {
    TicketAnalysisState.NOT_ANALYZED -> "Chưa phân tích"
    TicketAnalysisState.ANALYZED -> "Đã phân tích"
    TicketAnalysisState.HAS_UPDATES -> "Có cập nhật mới"
    TicketAnalysisState.ANALYZING -> "Đang phân tích"
}

private fun showDropdown() {
    (document.getElementById("combobox-dropdown") as? HTMLElement)?.style?.display = "block"
}

private fun hideDropdown() {
    (document.getElementById("combobox-dropdown") as? HTMLElement)?.style?.display = "none"
}
```

### Combobox CSS

```css
/* Combobox styles */
.combobox-wrapper {
    position: relative;
}
.combobox-dropdown {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    max-height: 300px;
    overflow-y: auto;
    background: rgba(20, 20, 40, 0.95);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 8px;
    z-index: 100;
    backdrop-filter: blur(20px);
}
.combobox-option {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 16px;
    cursor: pointer;
    transition: background 0.2s;
}
.combobox-option:hover {
    background: rgba(45, 254, 207, 0.1);
}
.ticket-id { font-weight: 600; color: var(--primary); min-width: 80px; }
.ticket-summary { flex: 1; opacity: 0.7; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.analysis-badge { font-size: 11px; padding: 2px 8px; border-radius: 4px; }
.analysis-badge.not-analyzed { background: rgba(255,255,255,0.1); color: rgba(255,255,255,0.5); }
.analysis-badge.analyzed { background: rgba(45, 254, 207, 0.15); color: var(--primary); }
.analysis-badge.has-updates { background: rgba(255, 180, 50, 0.15); color: #ffb432; }
.analysis-badge.analyzing { background: rgba(51, 134, 255, 0.15); color: var(--accent); }
```

*(Validates: Req 5.1, 5.11–5.15)*
