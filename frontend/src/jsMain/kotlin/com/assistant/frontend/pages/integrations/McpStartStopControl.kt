package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.components.BlockingOverlay
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement

/**
 * Start/Stop button on MCP server card (Administrator only).
 * Requirements: 6.37
 */
object McpStartStopControl {

    private val scope = MainScope()

    fun createButton(serverId: String, serverName: String, state: String): HTMLElement? {
        if (!isAdministrator()) return null
        val btn = document.createElement("button") as HTMLElement
        btn.className = "chat-action-btn mcp-startstop-btn"
        btn.id = "mcp-startstop-$serverId"
        updateButtonState(btn, state)
        btn.addEventListener("click", {
            val isRunning = btn.getAttribute("data-running") == "true"
            if (isRunning) stopServer(serverId, serverName, btn)
            else startServer(serverId, serverName, btn)
        })
        return btn
    }

    fun updateButtonState(btn: HTMLElement, state: String) {
        val running = state == "RUNNING" || state == "STARTING"
        btn.setAttribute("data-running", running.toString())
        if (running) {
            btn.textContent = "STOP"
            btn.style.cssText = "font-size:10px;padding:4px 12px;color:#ff4444;border-color:rgba(255,68,68,0.3);"
        } else {
            btn.textContent = "START"
            btn.style.cssText = "font-size:10px;padding:4px 12px;color:#00ff88;border-color:rgba(0,255,136,0.3);"
        }
    }

    private fun startServer(id: String, name: String, btn: HTMLElement) {
        val cardId = "mcp-card-$id"
        btn.textContent = "STARTING..."
        btn.setAttribute("disabled", "true")
        BlockingOverlay.show(cardId, "Starting...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/integrations/mcp/$id/start")
                if (resp.status == HttpStatusCode.OK) {
                    ToastService.show("MCP: $name started", "success")
                    updateButtonState(btn, "RUNNING")
                } else {
                    ToastService.show("Failed to start $name: ${resp.bodyAsText()}", "error")
                    updateButtonState(btn, "STOPPED")
                }
            } catch (e: Exception) {
                ToastService.show("Failed to start $name", "error")
                updateButtonState(btn, "STOPPED")
            } finally {
                BlockingOverlay.remove(cardId)
            }
            btn.removeAttribute("disabled")
        }
    }

    private fun stopServer(id: String, name: String, btn: HTMLElement) {
        val cardId = "mcp-card-$id"
        btn.textContent = "STOPPING..."
        btn.setAttribute("disabled", "true")
        BlockingOverlay.show(cardId, "Stopping...")
        scope.launch {
            try {
                val resp = ApiClient.post("/api/integrations/mcp/$id/stop")
                if (resp.status == HttpStatusCode.OK) {
                    ToastService.show("MCP: $name stopped", "success")
                    updateButtonState(btn, "STOPPED")
                } else {
                    ToastService.show("Failed to stop $name: ${resp.bodyAsText()}", "error")
                    updateButtonState(btn, "RUNNING")
                }
            } catch (e: Exception) {
                ToastService.show("Failed to stop $name", "error")
                updateButtonState(btn, "RUNNING")
            } finally {
                BlockingOverlay.remove(cardId)
            }
            btn.removeAttribute("disabled")
        }
    }

    private fun isAdministrator(): Boolean {
        val role = ApiClient.getUserRole()
        return role?.name == "ADMINISTRATOR"
    }
}
