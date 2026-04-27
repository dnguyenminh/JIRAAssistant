# Integrations — Design

## Integrations (MH6) — Status Dot Tooltip Design

Mỗi provider card hiển thị status dot với 3 trạng thái. Tooltip content render qua CSS class + Kotlin/JS event handlers:

**HTML Template (fragment):**
```html
<!-- templates/integrations.html -->
<div class="provider-grid" id="provider-grid">
    <!-- Kotlin/JS sẽ tạo provider cards động -->
</div>
```

**Kotlin/JS Controller tạo provider cards:**
```kotlin
// IntegrationsPage.kt
object IntegrationsPage : Page {
    override fun render(container: Element) {
        container.innerHTML = loadTemplate("integrations")
        MainScope().launch { loadProviders() }
    }

    private suspend fun loadProviders() {
        val providers = ApiClient.get<List<ProviderStatus>>("/api/integrations")
        val grid = document.getElementById("provider-grid") ?: return

        providers.forEach { provider ->
            val card = document.createElement("div").apply {
                className = "glass-card provider-card"
                innerHTML = """
                    <div class="provider-header">
                        <span class="provider-name">${provider.name}</span>
                        <span class="tooltip-wrapper">
                            <span class="status-dot ${provider.status.lowercase()}" 
                                  id="dot-${provider.id}"></span>
                            <span class="glass-tooltip" id="tooltip-${provider.id}">
                                ${tooltipContent(provider)}
                            </span>
                        </span>
                    </div>
                    <div class="provider-details">
                        <span class="label-small">${provider.type}</span>
                    </div>
                """.trimIndent()
            }
            grid.appendChild(card)
        }

        // Setup tooltip hover events
        setupTooltips()
    }

    private fun tooltipContent(provider: ProviderStatus): String = when (provider.status) {
        "ACTIVE" -> "Latency: ${provider.latencyMs}ms | Session active"
        "STANDBY" -> "Status: Ready for Synthesis | Last: ${provider.lastChecked}"
        else -> "Reason: Connection failed | Last: ${provider.lastChecked}"
    }

    private fun setupTooltips() {
        document.querySelectorAll(".status-dot").asList().forEach { dot ->
            val wrapper = (dot as HTMLElement).parentElement
            val tooltip = wrapper?.querySelector(".glass-tooltip") as? HTMLElement
            dot.addEventListener("mouseenter", { tooltip?.classList?.add("visible") })
            dot.addEventListener("mouseleave", { tooltip?.classList?.remove("visible") })
        }
    }
}
```

Dữ liệu tooltip lấy từ `ProviderStatus.latencyMs` và `ProviderStatus.lastChecked` trả về bởi `GET /api/integrations`. *(Validates: Req 6.3)*

---

## Gemini CLI Agent — Process-Based Architecture

Gemini CLI là command-line tool (không phải HTTP server), nên cần agent riêng thay vì dùng `OllamaAgent`.

### Component: `GeminiCliAgent`

**File:** `server/src/jvmMain/kotlin/com/assistant/server/ai/GeminiCliAgent.kt`

```
┌─────────────────────────────────────────────────┐
│                GeminiCliAgent                    │
│  implements AIAgent                              │
├─────────────────────────────────────────────────┤
│  - cliPath: String    (e.g. "gemini")           │
│  - model: String      (e.g. "gemini-2.5-flash") │
├─────────────────────────────────────────────────┤
│  + analyze(prompt, context): AIResult            │
│    → if model is "auto" or blank:               │
│        spawn: gemini < stdin  (no -m flag)      │
│    → else:                                       │
│        spawn: gemini -m {model} < stdin          │
│    → timeout: 240s                               │
│                                                  │
│  + testConnection(): String?                     │
│    → spawn: gemini --version                     │
│    → timeout: 15s                                │
│    → returns: "Connected — 0.38.2" or null       │
├─────────────────────────────────────────────────┤
│  - executeCliCommand(cmd, input, timeout)        │
│    → Windows: cmd /c {command}                   │
│    → Linux/macOS: {command} directly             │
│    → ProcessBuilder + waitFor(timeout)           │
└─────────────────────────────────────────────────┘
```

**Auto mode:** Khi model = `"auto"`, GeminiCliAgent không truyền flag `-m` — Gemini CLI tự chọn model tối ưu cho task (Auto Gemini 3 hoặc Auto Gemini 2.5). Điều này tránh lỗi 404 ModelNotFound khi truyền model name nội bộ của CLI (ví dụ: `gemini-3.1-pro`) qua API.

### DI Wiring (`ServerModule.kt`)

```kotlin
// buildAgentMap() — GEMINI_CLI branch
ProviderType.GEMINI_CLI ->
    agents[config.providerId] = GeminiCliAgent(
        cliPath = config.endpoint,   // "gemini" or full path
        model = config.model ?: "gemini-2.0-flash"
        // If model is "auto" or blank, GeminiCliAgent omits -m flag
    )
```

### Gemini CLI Model Selection (UI)

Frontend Integrations config modal cho Gemini CLI hiển thị **dropdown select** thay vì text input:

| Value | Label | Behavior |
|-------|-------|----------|
| `auto` | Auto (CLI decides) | Không truyền `-m` flag, CLI tự chọn model |
| `gemini-2.5-pro` | Gemini 2.5 Pro | Truyền `-m gemini-2.5-pro` |
| `gemini-2.5-flash` | Gemini 2.5 Flash | Truyền `-m gemini-2.5-flash` |
| `gemini-2.0-flash` | Gemini 2.0 Flash | Truyền `-m gemini-2.0-flash` |

### Test Connection Flow (`IntegrationRoutes.kt`)

```
POST /api/integrations/gemini_cli/test
  body: { endpoint: "gemini", model: "gemini-2.0-flash" }

  → isGeminiCli = true (providerId == "gemini_cli")
  → GeminiCliAgent("gemini", "gemini-2.0-flash")
  → agent.testConnection()
    → ProcessBuilder: cmd /c gemini --version  (Windows)
    → ProcessBuilder: gemini --version         (Linux/macOS)
    → stdout: "0.38.2"
    → return "Connected — 0.38.2"
  → respond: ProviderTestResult(success=true, message="Connected — 0.38.2")
```

### Cross-Platform Support

| OS | CLI Path | Execution |
|----|----------|-----------|
| Windows | `gemini` (resolves `.cmd` via PATH) | `cmd /c gemini --version` |
| Linux/macOS | `gemini` or `/usr/local/bin/gemini` | `gemini --version` directly |

*(Validates: Req 13, 19, 20, 21)*

---

## Failover-Aware Agent Selection — ChatService & JobExecutor

Tất cả AI consumers (ChatService, JobExecutor, AIOrchestrator) phải tôn trọng provider priority và status, không hardcode provider type.

### Trước (Bug)

```
ChatServiceImpl.aiAgentProvider:
  pcr.findByType(ProviderType.OLLAMA) → OllamaAgent  ← LUÔN Ollama

JobExecutor.resolveAgent():
  configs.filter(ACTIVE).sortedBy(priority).first()
  → OllamaAgent(http, c.model, c.endpoint)           ← LUÔN OllamaAgent
```

### Sau (Fix)

```
ChatServiceImpl.aiAgentProvider:
  buildAgentMap(pcr, http)                            ← Tạo đúng agent type
  → filter(ACTIVE, AI types).sortedBy(priority)
  → agents[bestConfig.providerId]                     ← Chọn theo priority
  → fallback: OllamaAgent("llama3", "localhost:11434")

JobExecutor.resolveAgent():
  configs.filter(ACTIVE).sortedBy(priority).first()
  → when (c.type) {
      GEMINI_CLI → GeminiCliAgent(c.endpoint, c.model)
      else → OllamaAgent(http, c.model, c.endpoint)
    }
```

### Agent Selection Flow

```
Provider DB (priority order)
  ┌──────────────┬──────────┬──────────┐
  │ gemini_cli   │ ACTIVE   │ prio: 1  │ ← Selected
  │ ollama       │ OFFLINE  │ prio: 2  │
  │ gemini       │ OFFLINE  │ prio: 3  │
  └──────────────┴──────────┴──────────┘
                    │
        ┌───────────┴───────────┐
        │ buildAgentMap()       │
        │ → GeminiCliAgent      │
        └───────────┬───────────┘
                    │
    ┌───────────────┼───────────────┐
    │               │               │
ChatService    JobExecutor    AIOrchestrator
(chat sidebar) (doc gen)     (ticket analysis)
```

*(Validates: Req 22, 23)*

---

## Provider Status Badge & Start/Stop Control — UI Design

Mỗi Integration card hiển thị status badge và nút START/STOP cạnh tên provider, giống pattern của MCP server cards.

### Card Header Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  [Logo]  Provider Name  [ACTIVE badge]  [STOP btn]    ● ▲ ▼   │
│          PROVIDER_TYPE                                          │
├─────────────────────────────────────────────────────────────────┤
│  PRIORITY: #1                                                   │
│  [TEST LINK]  [CONFIGURE]                                       │
└─────────────────────────────────────────────────────────────────┘
```

### CSS Classes

| Class | Mô tả |
|-------|-------|
| `.integ-status-badge` | Base badge style: pill shape, 9px font, uppercase |
| `.integ-status-badge.badge-active` | Nền xanh lá, text `#00ff88` |
| `.integ-status-badge.badge-standby` | Nền xanh dương, text `#3386ff` |
| `.integ-status-badge.badge-offline` | Nền đỏ hồng, text `#ff6e84` |
| `.integ-startstop-btn` | Base button: pill shape, 10px font, transparent bg |
| `.integ-startstop-btn.btn-stop` | Text đỏ `#ff4444`, border đỏ |
| `.integ-startstop-btn.btn-start` | Text xanh `#00ff88`, border xanh |

### Component: `IntegrationsStartStop`

**File:** `frontend/src/jsMain/kotlin/com/assistant/frontend/pages/integrations/IntegrationsStartStop.kt`

```
┌─────────────────────────────────────────────────┐
│            IntegrationsStartStop                 │
├─────────────────────────────────────────────────┤
│  + toggle(provider, card)                        │
│    → isActive? stop() : start()                  │
│                                                  │
│  - start(provider, card)                         │
│    → BlockingOverlay.show("Starting...")          │
│    → POST /api/integrations/{id}/test            │
│    → success? ACTIVE : OFFLINE                   │
│    → re-render cards                             │
│                                                  │
│  - stop(provider, card)                          │
│    → BlockingOverlay.show("Stopping...")          │
│    → PUT /api/integrations/{id}/status           │
│      body: {"status": "OFFLINE"}                 │
│    → re-render cards                             │
└─────────────────────────────────────────────────┘
```

### Backend Endpoint

```
PUT /api/integrations/{providerId}/status
  Permission: CONFIG_INTEGRATIONS (Administrator)
  Body: { "status": "ACTIVE" | "STANDBY" | "OFFLINE" }
  Response: { "providerId": "...", "status": "..." }
  → providerConfigRepo.updateStatus(providerId, status)
```

### Start/Stop Flow

```
User clicks STOP on ACTIVE provider:
  Frontend                          Backend
  ────────                          ───────
  BlockingOverlay.show()
  PUT /status {OFFLINE}  ────────→  updateStatus(id, OFFLINE)
                         ←────────  {providerId, OFFLINE}
  Update local state
  Re-render cards (badge=OFFLINE, btn=START)
  Toast "✓ Provider stopped"
  BlockingOverlay.remove()

User clicks START on OFFLINE provider:
  Frontend                          Backend
  ────────                          ───────
  BlockingOverlay.show()
  POST /{id}/test {}     ────────→  testProvider(id)
                         ←────────  {success: true/false}
  Update local state (ACTIVE or OFFLINE)
  Re-render cards
  Toast success/error
  BlockingOverlay.remove()
```

*(Validates: Req 6.64, 6.65, 6.66, 6.67, 6.68)*
