package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.McpProcessStatusDto
import com.assistant.frontend.models.McpServerInfo
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Polls MCP server status every 10s, updates card UI + toasts.
 * Requirements: 6.57, 6.58, 6.59
 */
object McpStatusPoller {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var pollingJob: Job? = null
    private val previousStates = mutableMapOf<String, String>()

    fun start(servers: List<McpServerInfo>) {
        stop()
        pollingJob = scope.launch {
            while (isActive) {
                for (server in servers) pollServer(server)
                delay(10_000)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        previousStates.clear()
    }

    private suspend fun pollServer(server: McpServerInfo) {
        try {
            val resp = ApiClient.get("/api/integrations/mcp/${server.id}/status")
            if (resp.status != HttpStatusCode.OK) return
            val status = json.decodeFromString<McpProcessStatusDto>(resp.bodyAsText())
            updateCardUI(server.id, status)
            checkStateTransition(server.name, server.id, status)
        } catch (_: Exception) { /* silent — next poll will retry */ }
    }

    private fun updateCardUI(serverId: String, status: McpProcessStatusDto) {
        val card = document.getElementById("mcp-card-$serverId") ?: return
        updateStatusDot(card, status.state)
        updateStatusInfo(card, status)
    }

    private fun updateStatusDot(card: org.w3c.dom.Element, state: String) {
        val dot = card.querySelector(".mcp-status-dot") as? HTMLElement ?: return
        dot.className = "mcp-status-dot"
        dot.style.background = stateColor(state)
        dot.style.boxShadow = "0 0 8px ${stateColor(state)}"
        dot.title = state
    }

    private fun updateStatusInfo(card: org.w3c.dom.Element, s: McpProcessStatusDto) {
        val info = card.querySelector(".mcp-runtime-info") as? HTMLElement
        if (info != null) {
            info.textContent = "${formatUptime(s.uptime)} · ${s.toolCount} tools"
        }
    }

    private fun checkStateTransition(name: String, id: String, status: McpProcessStatusDto) {
        val prev = previousStates[id]
        previousStates[id] = status.state
        if (prev != null && prev != status.state) {
            val msg = "MCP: $name $prev → ${status.state}"
            val type = if (status.state == "ERROR") "error" else "success"
            val full = if (status.state == "ERROR" && !status.lastError.isNullOrBlank())
                "$msg — ${status.lastError}" else msg
            ToastService.show(full, type)
        }
    }

    fun stateColor(state: String): String = when (state) {
        "RUNNING" -> "#00ff88"
        "STARTING" -> "#ffaa00"
        "ERROR" -> "#ff4444"
        else -> "#666"
    }

    fun formatUptime(seconds: Long): String {
        if (seconds < 60) return "${seconds}s"
        val m = seconds / 60
        val h = m / 60
        return if (h > 0) "${h}h ${m % 60}m" else "${m}m"
    }
}
