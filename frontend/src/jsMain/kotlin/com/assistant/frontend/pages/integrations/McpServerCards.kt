package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.models.McpServerInfo
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Renders MCP server cards with status, tools, start/stop controls.
 * Requirements: 6.21, 6.29, 6.57, 6.58
 */
object McpServerCards {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    var servers = mutableListOf<McpServerInfo>()
    var onConfigure: ((McpServerInfo) -> Unit)? = null

    fun load() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/integrations/mcp")
                if (resp.status == HttpStatusCode.OK) {
                    servers.clear()
                    servers.addAll(json.decodeFromString<List<McpServerInfo>>(resp.bodyAsText()))
                }
            } catch (e: Exception) {
                console.log("[McpServerCards] Load failed: ${e.message}")
            }
            render()
            McpStatusPoller.start(servers)
        }
    }

    fun render() {
        val grid = document.getElementById("mcp-grid") as? HTMLElement ?: return
        val empty = document.getElementById("mcp-empty") as? HTMLElement
        grid.innerHTML = ""
        // Local KB card always at top of grid (Req: 19.75)
        LocalKBCard.render(grid)
        if (servers.isEmpty()) {
            empty?.style?.display = "none"; return
        }
        empty?.style?.display = "none"
        for (server in servers) grid.appendChild(createCard(server))
    }

    fun cleanup() {
        McpStatusPoller.stop()
    }

    private fun createCard(server: McpServerInfo): HTMLElement {
        val card = document.createElement("div") as HTMLElement
        card.className = "glass-card mcp-server-card"
        card.id = "mcp-card-${server.id}"
        card.style.position = "relative"
        appendCardHeader(card, server)
        appendRuntimeInfo(card, server)
        appendCardBody(card, server)
        appendToolsSection(card, server)
        appendCardActions(card, server)
        return card
    }

    private fun appendCardHeader(card: HTMLElement, server: McpServerInfo) {
        val header = document.createElement("div") as HTMLElement
        header.style.cssText = "display:flex;align-items:center;gap:10px;"
        val dot = createStatusDot(server.status)
        val name = document.createElement("span") as HTMLElement
        name.textContent = server.name
        name.style.cssText = "font-weight:600;font-size:14px;flex:1;"
        header.appendChild(dot); header.appendChild(name)
        if (server.internal) header.appendChild(createBuiltinBadge())
        val startStop = LocalServerStartStop.createButton(server, card)
        if (startStop != null) header.appendChild(startStop)
        card.appendChild(header)
    }

    private fun createBuiltinBadge(): HTMLElement {
        val badge = document.createElement("span") as HTMLElement
        badge.className = "local-kb-type-badge"
        badge.textContent = "LOCAL"
        return badge
    }

    private fun createStatusDot(status: String): HTMLElement {
        val dot = document.createElement("span") as HTMLElement
        dot.className = "mcp-status-dot"
        val color = McpStatusPoller.stateColor(mapLegacyStatus(status))
        dot.style.cssText = "width:8px;height:8px;border-radius:50%;display:inline-block;background:$color;box-shadow:0 0 8px $color;"
        dot.title = status
        return dot
    }

    private fun appendRuntimeInfo(card: HTMLElement, server: McpServerInfo) {
        val info = document.createElement("div") as HTMLElement
        info.className = "mcp-runtime-info"
        info.style.cssText = "font-size:10px;opacity:0.5;letter-spacing:0.5px;margin-top:4px;"
        info.textContent = "— · 0 tools"
        card.appendChild(info)
    }

    private fun appendCardBody(card: HTMLElement, server: McpServerInfo) {
        val body = document.createElement("div") as HTMLElement
        body.style.cssText = "font-size:11px;opacity:0.5;font-family:'JetBrains Mono',monospace;margin-top:6px;"
        body.textContent = "${server.command} ${server.args}"
        card.appendChild(body)
    }

    private fun appendToolsSection(card: HTMLElement, server: McpServerInfo) {
        val section = McpToolsSection.createSection(server.id, 0)
        card.appendChild(section)
    }

    private fun appendCardActions(card: HTMLElement, server: McpServerInfo) {
        if (server.internal) return
        val actions = document.createElement("div") as HTMLElement
        actions.style.cssText = "display:flex;gap:8px;margin-top:8px;"
        actions.appendChild(createBtn("TEST") { testServer(server) })
        actions.appendChild(createBtn("CONFIGURE") { onConfigure?.invoke(server) })
        val delBtn = createBtn("REMOVE") { deleteServer(server.id) }
        delBtn.style.cssText += ";color:var(--danger);border-color:rgba(255,110,132,0.25);"
        actions.appendChild(delBtn)
        card.appendChild(actions)
    }

    private fun createBtn(label: String, handler: () -> Unit): HTMLElement {
        val btn = document.createElement("button") as HTMLElement
        btn.className = "chat-action-btn"; btn.textContent = label
        btn.addEventListener("click", { handler() })
        return btn
    }

    private fun testServer(server: McpServerInfo) {
        val cardId = "mcp-card-${server.id}"
        BlockingOverlay.show(cardId, "Testing...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/integrations/mcp/${server.id}/test", mapOf<String, String>())
                val body = resp.bodyAsText()
                if (resp.status == HttpStatusCode.OK && body.contains("\"success\":true")) {
                    ToastService.show("✓ ${server.name}: Connection OK", "success")
                    load()
                } else {
                    val error = extractError(body)
                    ToastService.show("✗ ${server.name}: $error", "error")
                    load()
                }
            } catch (e: Exception) {
                ToastService.show("✗ ${server.name}: ${e.message}", "error")
            } finally {
                BlockingOverlay.remove(cardId)
            }
        }
    }

    private fun extractError(body: String): String {
        val idx = body.indexOf("\"error\"")
        if (idx < 0) return "Test failed"
        val start = body.indexOf('"', idx + 8) + 1
        val end = body.indexOf('"', start)
        return if (start > 0 && end > start) body.substring(start, end) else "Test failed"
    }

    private fun deleteServer(id: String) {
        if (!kotlinx.browser.window.confirm("Remove this MCP server?")) return
        val cardId = "mcp-card-$id"
        BlockingOverlay.show(cardId, "Removing...")
        scope.launch {
            try {
                ApiClient.delete("/api/integrations/mcp/$id")
                servers.removeAll { it.id == id }
                render()
            } catch (e: Exception) {
                console.log("[McpServerCards] Delete failed: ${e.message}")
            } finally {
                BlockingOverlay.remove(cardId)
            }
        }
    }

    private fun mapLegacyStatus(status: String): String = when (status) {
        "ACTIVE" -> "RUNNING"
        else -> status
    }
}
