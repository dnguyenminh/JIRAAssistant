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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.HTMLElement

/**
 * Unified START/STOP button for MCP server cards.
 * Internal servers: toggle via /api/settings/feature.
 * External servers: start/stop via /api/integrations/mcp/{id}/start|stop.
 */
object LocalServerStartStop {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true }
    private const val SETTING_KEY = "internal_mcp_enabled"
    private var internalEnabled = true

    fun createButton(server: McpServerInfo, card: HTMLElement): HTMLElement? {
        if (!isAdministrator()) return null
        val btn = document.createElement("button") as HTMLElement
        btn.className = "chat-action-btn mcp-startstop-btn"
        btn.id = "mcp-startstop-${server.id}"
        if (server.internal) {
            updateInternalBtnState(btn)
            btn.addEventListener("click", { onInternalClick(server, btn, card) })
            loadInternalState(server.id)
        } else {
            McpStartStopControl.updateButtonState(btn, server.status)
            btn.addEventListener("click", {
                val running = btn.getAttribute("data-running") == "true"
                if (running) stopExternal(server, btn)
                else startExternal(server, btn)
            })
        }
        return btn
    }

    private fun updateInternalBtnState(btn: HTMLElement) {
        btn.setAttribute("data-running", internalEnabled.toString())
        if (internalEnabled) {
            btn.textContent = "STOP"
            btn.style.cssText = "font-size:10px;padding:4px 12px;color:#ff4444;border-color:rgba(255,68,68,0.3);"
        } else {
            btn.textContent = "START"
            btn.style.cssText = "font-size:10px;padding:4px 12px;color:#00ff88;border-color:rgba(0,255,136,0.3);"
        }
    }

    private fun onInternalClick(server: McpServerInfo, btn: HTMLElement, card: HTMLElement) {
        val newState = !internalEnabled
        val action = if (newState) "Starting..." else "Stopping..."
        val cardId = "mcp-card-${server.id}"
        BlockingOverlay.show(cardId, action)
        scope.launch {
            try {
                val body = mapOf("key" to SETTING_KEY, "value" to newState.toString())
                val resp = ApiClient.put("/api/settings/feature", body)
                if (resp.status.isSuccess()) {
                    internalEnabled = newState
                    updateInternalBtnState(btn)
                    refreshDot(card)
                    val label = if (internalEnabled) "started" else "stopped"
                    ToastService.show("✓ ${server.name} $label", "success")
                } else {
                    ToastService.show("✗ Failed to save setting", "error")
                }
            } catch (e: Exception) {
                ToastService.show("✗ ${e.message}", "error")
            } finally {
                BlockingOverlay.remove(cardId)
            }
        }
    }

    private fun refreshDot(card: HTMLElement) {
        val dot = card.querySelector(".mcp-status-dot") as? HTMLElement ?: return
        val state = if (internalEnabled) "RUNNING" else "STOPPED"
        val color = McpStatusPoller.stateColor(state)
        dot.style.cssText = "width:8px;height:8px;border-radius:50%;display:inline-block;" +
            "background:$color;box-shadow:0 0 8px $color;"
        dot.title = state
    }

    private fun startExternal(server: McpServerInfo, btn: HTMLElement) {
        val cardId = "mcp-card-${server.id}"
        btn.textContent = "STARTING..."
        btn.setAttribute("disabled", "true")
        BlockingOverlay.show(cardId, "Starting...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/integrations/mcp/${server.id}/start")
                if (resp.status == HttpStatusCode.OK) {
                    ToastService.show("MCP: ${server.name} started", "success")
                    McpStartStopControl.updateButtonState(btn, "RUNNING")
                } else {
                    ToastService.show("Failed to start ${server.name}", "error")
                    McpStartStopControl.updateButtonState(btn, "STOPPED")
                }
            } catch (e: Exception) {
                ToastService.show("Failed to start ${server.name}", "error")
                McpStartStopControl.updateButtonState(btn, "STOPPED")
            } finally {
                BlockingOverlay.remove(cardId)
                btn.removeAttribute("disabled")
            }
        }
    }

    private fun stopExternal(server: McpServerInfo, btn: HTMLElement) {
        val cardId = "mcp-card-${server.id}"
        btn.textContent = "STOPPING..."
        btn.setAttribute("disabled", "true")
        BlockingOverlay.show(cardId, "Stopping...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/integrations/mcp/${server.id}/stop")
                if (resp.status == HttpStatusCode.OK) {
                    ToastService.show("MCP: ${server.name} stopped", "success")
                    McpStartStopControl.updateButtonState(btn, "STOPPED")
                } else {
                    ToastService.show("Failed to stop ${server.name}", "error")
                    McpStartStopControl.updateButtonState(btn, "RUNNING")
                }
            } catch (e: Exception) {
                ToastService.show("Failed to stop ${server.name}", "error")
                McpStartStopControl.updateButtonState(btn, "RUNNING")
            } finally {
                BlockingOverlay.remove(cardId)
                btn.removeAttribute("disabled")
            }
        }
    }

    private fun loadInternalState(serverId: String) {
        scope.launch {
            try {
                val resp = ApiClient.get("/api/settings/feature?key=$SETTING_KEY")
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    parseState(body)
                    val btn = document.getElementById("mcp-startstop-$serverId") as? HTMLElement
                    if (btn != null) updateInternalBtnState(btn)
                    val card = document.getElementById("mcp-card-$serverId") as? HTMLElement
                    if (card != null) refreshDot(card)
                }
            } catch (e: Exception) {
                console.log("[LocalServerStartStop] loadState failed: ${e.message}")
            }
        }
    }

    private fun parseState(body: String) {
        try {
            val obj = json.parseToJsonElement(body).jsonObject
            val value = obj["value"]?.jsonPrimitive?.content
            if (value != null) internalEnabled = value != "false"
        } catch (_: Exception) { /* default enabled = true */ }
    }

    private fun isAdministrator(): Boolean {
        val role = ApiClient.getUserRole()
        return role?.name == "ADMINISTRATOR"
    }
}
