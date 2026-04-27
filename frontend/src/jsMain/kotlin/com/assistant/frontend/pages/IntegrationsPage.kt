package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.ProviderInfo
import com.assistant.frontend.pages.integrations.*
import com.assistant.frontend.services.HtmlUtils
import com.assistant.rbac.Permission
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/**
 * Integrations page — AI Provider management + MCP Servers (MH6).
 */
object IntegrationsPage {

    internal val scope = MainScope()
    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }
    internal var providers = mutableListOf<ProviderInfo>()
    internal var activeModal: String? = null

    fun render(container: Element) {
        container.innerHTML = ""
        providers.clear(); activeModal = null
        scope.launch {
            val html = ApiClient.loadTemplate("integrations")
            val page = document.createElement("div") as HTMLElement
            page.id = "integrations-page"; page.innerHTML = html
            container.appendChild(page)
            document.getElementById("integrations-retry-btn")?.addEventListener("click", {
                loadProviders()
            })
            loadProviders()
            initMcpSection()
            initPipelineToggle()
        }
    }

    private fun initMcpSection() {
        McpServerCards.onConfigure = { server -> McpConfigModal.open(server) }
        McpServerCards.load()
        document.getElementById("btn-add-mcp")?.addEventListener("click", { McpConfigModal.open() })
        document.getElementById("btn-atlassian-rovo")?.addEventListener("click", { McpAtlassianPreset.open() })
        document.getElementById("btn-import-mcp")?.addEventListener("click", { importMcpConfig() })
        document.getElementById("btn-export-mcp")?.addEventListener("click", { exportMcpConfig() })
    }

    private fun importMcpConfig() {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"; input.accept = ".json"
        input.addEventListener("change", {
            val file = input.files?.item(0) ?: return@addEventListener
            val reader = org.w3c.files.FileReader()
            reader.onload = {
                val content = reader.result as? String
                if (content != null) {
                    scope.launch {
                        try {
                            ApiClient.post("/api/integrations/mcp/import", json.parseToJsonElement(content))
                            McpServerCards.load()
                        } catch (e: Exception) {
                            console.log("[IntegrationsPage] Import failed: ${e.message}")
                        }
                    }
                }
            }
            reader.readAsText(file)
        })
        input.click()
    }

    private fun exportMcpConfig() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/integrations/mcp/export")
                val body = resp.bodyAsText()
                val blob = org.w3c.files.Blob(arrayOf(body), org.w3c.files.BlobPropertyBag("application/json"))
                val url = org.w3c.dom.url.URL.createObjectURL(blob)
                val a = document.createElement("a") as HTMLElement
                a.setAttribute("href", url)
                a.setAttribute("download", "mcp.json")
                a.click()
                org.w3c.dom.url.URL.revokeObjectURL(url)
            } catch (e: Exception) {
                console.log("[IntegrationsPage] Export failed: ${e.message}")
            }
        }
    }

    private fun loadProviders() {
        scope.launch {
            try {
                val response = ApiClient.get("/api/integrations")
                if (ApiClient.handleUnauthorized(response)) {
                    providers.clear(); providers.addAll(defaultProviders())
                    renderProviderCards(); return@launch
                }
                val body = response.bodyAsText()
                val statuses = json.decodeFromString<List<ProviderInfo>>(body)
                providers.clear()
                if (statuses.isEmpty()) providers.addAll(defaultProviders())
                else providers.addAll(statuses.sortedBy { it.priority })
                hideIntegrationsError()
                renderProviderCards()
            } catch (e: Exception) {
                console.log("[IntegrationsPage] Failed to load providers: ${e.message}")
                showIntegrationsError("Failed to load providers: ${e.message}")
                providers.clear(); providers.addAll(defaultProviders())
                renderProviderCards()
            }
        }
    }

    private fun defaultProviders(): List<ProviderInfo> = listOf(
        ProviderInfo("jira", "Jira Cloud Services", "JIRA", "STANDBY", priority = 0),
        ProviderInfo("ollama", "Ollama (Local)", "OLLAMA", "OFFLINE", priority = 1),
        ProviderInfo("gemini", "Google Gemini API", "GEMINI", "OFFLINE", priority = 2),
        ProviderInfo("lm_studio", "LM Studio", "LM_STUDIO", "OFFLINE", priority = 3),
        ProviderInfo("gemini_cli", "Gemini CLI Interface", "GEMINI_CLI", "OFFLINE", priority = 4),
        ProviderInfo("copilot_cli", "Copilot CLI (GitHub)", "COPILOT_CLI", "OFFLINE", priority = 5),
        ProviderInfo("kiro_cli", "Kiro CLI (Amazon)", "KIRO_CLI", "OFFLINE", priority = 6),
        ProviderInfo("embedding", "Embedding Model", "EMBEDDING", "ACTIVE", priority = 10,
            endpoint = "http://localhost:11434", model = "nomic-embed-text")
    )

    internal fun renderProviderCards() {
        val grid = document.getElementById("integ-grid") ?: return
        grid.innerHTML = ""
        val canConfig = ApiClient.hasPermission(Permission.CONFIG_INTEGRATIONS)
        for ((index, provider) in providers.withIndex()) {
            val card = document.createElement("div") as HTMLElement
            card.className = "glass-card integ-card"
            card.id = "integ-card-${provider.providerId}"
            card.setAttribute("draggable", if (canConfig) "true" else "false")
            card.innerHTML = IntegrationsCardBuilder.buildCardHtml(provider, index, canConfig)
            grid.appendChild(card)
            IntegrationsCardBuilder.bindCardEvents(card, provider, index, canConfig)
        }
    }

    internal fun swapPriority(fromIdx: Int, toIdx: Int) {
        if (fromIdx < 0 || toIdx < 0 || fromIdx >= providers.size || toIdx >= providers.size) return
        val temp = providers[fromIdx]
        providers[fromIdx] = providers[toIdx]; providers[toIdx] = temp
        providers.forEachIndexed { i, p -> providers[i] = p.copy(priority = i) }
        renderProviderCards()
        val orderedIds = providers.map { it.providerId }
        scope.launch {
            try {
                for ((i, id) in orderedIds.withIndex()) {
                    ApiClient.put("/api/integrations/$id/config", com.assistant.frontend.models.PriorityUpdate(priority = i))
                }
            } catch (e: Exception) {
                console.log("[IntegrationsPage] Failed to save priority: ${e.message}")
                showIntegrationsError("Failed to save provider priority: ${e.message}")
            }
        }
    }

    internal fun updateCardStatus(providerId: String, status: String, latencyMs: Long?) {
        val card = document.getElementById("integ-card-$providerId") ?: return
        val dot = card.querySelector(".status-dot") as? HTMLElement ?: return
        val statusClass = when (status.uppercase()) {
            "ACTIVE" -> "status-dot-active"; "STANDBY" -> "status-dot-standby"; else -> "status-dot-offline"
        }
        dot.className = "status-dot $statusClass"
        val idx = providers.indexOfFirst { it.providerId == providerId }
        if (idx >= 0) {
            val p = providers[idx].copy(status = status, latencyMs = latencyMs)
            dot.setAttribute("data-tooltip", IntegrationsCardBuilder.buildTooltipText(p))
        }
    }

    internal fun escapeHtml(text: String): String = HtmlUtils.escapeHtml(text)

    private fun showIntegrationsError(message: String) {
        val errorEl = document.getElementById("integrations-error") as? HTMLElement
        val msgEl = document.getElementById("integrations-error-msg") as? HTMLElement
        errorEl?.style?.display = ""
        msgEl?.textContent = message
    }

    private fun hideIntegrationsError() {
        (document.getElementById("integrations-error") as? HTMLElement)
            ?.style?.display = "none"
    }
}
