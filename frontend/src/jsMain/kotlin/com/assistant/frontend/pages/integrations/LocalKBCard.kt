package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement

/**
 * Renders the Local Knowledge Base tool card on Integrations page.
 * Uses START/STOP button consistent with external MCP server cards.
 * Requirements: AC 19.75, AC 19.76
 */
object LocalKBCard {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var enabled = true
    private const val CARD_ID = "local-kb-card"
    private const val SETTING_KEY = "local_kb_tool_enabled"

    fun render(container: HTMLElement) {
        val card = document.createElement("div") as HTMLElement
        card.className = "glass-card mcp-server-card local-kb-card"
        card.id = CARD_ID
        card.style.position = "relative"
        appendHeader(card)
        appendRuntimeInfo(card)
        appendDescription(card)
        appendToolsSection(card)
        container.appendChild(card)
        loadState()
    }

    private fun appendHeader(card: HTMLElement) {
        val header = document.createElement("div") as HTMLElement
        header.style.cssText = "display:flex;align-items:center;gap:10px;"
        header.appendChild(createStatusDot())
        header.appendChild(createName())
        header.appendChild(createBadge())
        header.appendChild(createStartStopBtn())
        card.appendChild(header)
    }

    private fun createStatusDot(): HTMLElement {
        val dot = document.createElement("span") as HTMLElement
        dot.id = "local-kb-status-dot"
        dot.className = "mcp-status-dot"
        updateDotColor(dot)
        return dot
    }

    private fun updateDotColor(dot: HTMLElement) {
        val color = if (enabled) McpStatusPoller.stateColor("RUNNING") else McpStatusPoller.stateColor("STOPPED")
        dot.style.cssText = "width:8px;height:8px;border-radius:50%;display:inline-block;" +
            "background:$color;box-shadow:0 0 8px $color;"
        dot.title = if (enabled) "RUNNING" else "STOPPED"
    }

    private fun createName(): HTMLElement {
        val name = document.createElement("span") as HTMLElement
        name.textContent = "Local Knowledge Base"
        name.style.cssText = "font-weight:600;font-size:14px;flex:1;"
        return name
    }

    private fun createStartStopBtn(): HTMLElement {
        val btn = document.createElement("button") as HTMLElement
        btn.className = "chat-action-btn mcp-startstop-btn"
        btn.id = "local-kb-startstop"
        updateBtnState(btn)
        btn.addEventListener("click", { onStartStopClick(btn) })
        return btn
    }

    private fun updateBtnState(btn: HTMLElement) {
        if (enabled) {
            btn.textContent = "STOP"
            btn.style.cssText = "font-size:10px;padding:4px 12px;color:#ff4444;border-color:rgba(255,68,68,0.3);"
        } else {
            btn.textContent = "START"
            btn.style.cssText = "font-size:10px;padding:4px 12px;color:#00ff88;border-color:rgba(0,255,136,0.3);"
        }
    }

    private fun createBadge(): HTMLElement {
        val badge = document.createElement("span") as HTMLElement
        badge.className = "local-kb-type-badge"
        badge.textContent = "LOCAL"
        return badge
    }

    private fun appendRuntimeInfo(card: HTMLElement) {
        val info = document.createElement("div") as HTMLElement
        info.className = "mcp-runtime-info"
        info.style.cssText = "font-size:10px;opacity:0.5;letter-spacing:0.5px;margin-top:4px;"
        info.textContent = "— · 4 tools"
        card.appendChild(info)
    }

    private fun appendDescription(card: HTMLElement) {
        val desc = document.createElement("div") as HTMLElement
        desc.style.cssText = "font-size:12px;opacity:0.6;margin-top:6px;line-height:1.5;"
        desc.textContent = "Knowledge Base cục bộ — tìm kiếm dữ liệu đã vectorize " +
            "từ Jira tickets, attachments, Confluence pages"
        card.appendChild(desc)
    }

    private fun appendToolsSection(card: HTMLElement) {
        val section = document.createElement("div") as HTMLElement
        section.className = "mcp-tools-section"
        val toggle = document.createElement("div") as HTMLElement
        toggle.textContent = "▶ Tools (4)"
        toggle.style.cssText = "cursor:pointer;font-size:11px;opacity:0.6;padding:6px 0;letter-spacing:0.5px;"
        val list = document.createElement("div") as HTMLElement
        list.style.display = "none"
        appendToolRows(list)
        toggle.addEventListener("click", {
            val visible = list.style.display != "none"
            list.style.display = if (visible) "none" else "block"
            toggle.textContent = if (visible) "▶ Tools (4)" else "▼ Tools (4)"
        })
        section.appendChild(toggle)
        section.appendChild(list)
        card.appendChild(section)
    }

    private fun appendToolRows(list: HTMLElement) {
        val tools = listOf(
            "search_knowledge" to "Semantic search trong Knowledge Base cục bộ",
            "get_ticket_info" to "Tra cứu thông tin phân tích ticket từ KB",
            "search_relationships" to "Tìm kiếm mối quan hệ/dependency giữa tickets",
            "ingest_knowledge" to "Ghi nội dung vào KB để chia sẻ dữ liệu giữa các phases"
        )
        for ((name, desc) in tools) {
            val row = document.createElement("div") as HTMLElement
            row.style.cssText = "display:flex;align-items:center;gap:6px;padding:4px 0;font-size:11px;"
            val icon = document.createElement("span") as HTMLElement
            icon.textContent = "🔧"
            val nameEl = document.createElement("span") as HTMLElement
            nameEl.textContent = name
            nameEl.style.fontWeight = "600"
            val descEl = document.createElement("span") as HTMLElement
            descEl.textContent = desc
            descEl.style.cssText = "opacity:0.5;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
            row.appendChild(icon); row.appendChild(nameEl); row.appendChild(descEl)
            list.appendChild(row)
        }
    }

    private fun onStartStopClick(btn: HTMLElement) {
        val newState = !enabled
        val action = if (newState) "Starting..." else "Stopping..."
        BlockingOverlay.show(CARD_ID, action)
        scope.launch {
            try {
                val body = mapOf("key" to SETTING_KEY, "value" to newState.toString())
                val resp = ApiClient.put("/api/settings/feature", body)
                if (resp.status.isSuccess()) {
                    enabled = newState
                    refreshUI(btn)
                    val label = if (enabled) "started" else "stopped"
                    ToastService.show("✓ Local KB $label", "success")
                } else {
                    ToastService.show("✗ Failed to save setting", "error")
                }
            } catch (e: Exception) {
                ToastService.show("✗ ${e.message}", "error")
            } finally {
                BlockingOverlay.remove(CARD_ID)
            }
        }
    }

    private fun refreshUI(btn: HTMLElement? = null) {
        val dot = document.getElementById("local-kb-status-dot") as? HTMLElement
        if (dot != null) updateDotColor(dot)
        val b = btn ?: (document.getElementById("local-kb-startstop") as? HTMLElement)
        if (b != null) updateBtnState(b)
    }

    private fun loadState() {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/settings/feature?key=$SETTING_KEY")
                if (resp.status.isSuccess()) parseEnabledState(resp.bodyAsText())
            } catch (e: Exception) {
                console.log("[LocalKBCard] loadState failed: ${e.message}")
            }
            refreshUI()
        }
    }

    private fun parseEnabledState(body: String) {
        try {
            val obj = json.parseToJsonElement(body).jsonObject
            val value = obj["value"]?.jsonPrimitive?.content
            if (value != null) enabled = value != "false"
        } catch (_: Exception) { /* default enabled = true */ }
    }
}
