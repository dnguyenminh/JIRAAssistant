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
