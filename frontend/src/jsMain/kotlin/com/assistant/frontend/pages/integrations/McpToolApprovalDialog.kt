package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.McpToolCallRequestDto
import com.assistant.frontend.models.McpToolCallResponseDto
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.w3c.dom.HTMLElement

/**
 * Confirmation dialog when tool call requires approval.
 * Requirements: 6.51
 */
object McpToolApprovalDialog {

    private val scope = MainScope()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    var onApproved: ((McpToolCallResponseDto) -> Unit)? = null
    var onDenied: (() -> Unit)? = null

    fun show(
        toolName: String,
        serverName: String,
        serverId: String,
        arguments: JsonObject
    ) {
        close()
        val overlay = createOverlay()
        val modal = createModal(toolName, serverName, serverId, arguments)
        overlay.appendChild(modal)
        document.body?.appendChild(overlay)
    }

    fun close() {
        document.getElementById("mcp-approval-overlay")?.remove()
    }

    private fun createOverlay(): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.id = "mcp-approval-overlay"
        el.style.cssText = "position:fixed;inset:0;background:rgba(0,0,0,0.6);backdrop-filter:blur(8px);z-index:2600;display:flex;align-items:center;justify-content:center;"
        return el
    }

    private fun createModal(
        toolName: String, serverName: String,
        serverId: String, arguments: JsonObject
    ): HTMLElement {
        val box = document.createElement("div") as HTMLElement
        box.className = "glass-card"
        box.style.cssText = "max-width:480px;width:90%;padding:32px;"
        appendTitle(box, toolName, serverName)
        appendArgs(box, arguments)
        appendButtons(box, serverId, toolName, arguments)
        return box
    }

    private fun appendTitle(box: HTMLElement, tool: String, server: String) {
        val h = document.createElement("div") as HTMLElement
        h.style.cssText = "font-size:15px;font-weight:600;margin-bottom:8px;"
        h.textContent = "🔧 Tool Approval Required"
        val sub = document.createElement("div") as HTMLElement
        sub.style.cssText = "font-size:12px;opacity:0.6;margin-bottom:16px;"
        sub.textContent = "$tool on $server"
        box.appendChild(h); box.appendChild(sub)
    }

    private fun appendArgs(box: HTMLElement, arguments: JsonObject) {
        val pre = document.createElement("pre") as HTMLElement
        pre.style.cssText = "background:rgba(0,0,0,0.3);border-radius:8px;padding:12px;font-size:11px;max-height:200px;overflow:auto;margin-bottom:16px;"
        val code = document.createElement("code") as HTMLElement
        code.textContent = json.encodeToString(JsonObject.serializer(), arguments)
        pre.appendChild(code)
        box.appendChild(pre)
    }

    private fun appendButtons(
        box: HTMLElement, serverId: String,
        toolName: String, arguments: JsonObject
    ) {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;gap:8px;"
        val approve = document.createElement("button") as HTMLElement
        approve.className = "btn-vibrant"
        approve.textContent = "Approve"
        approve.style.cssText = "flex:1;padding:12px;font-size:12px;"
        approve.addEventListener("click", { executeApproved(serverId, toolName, arguments) })
        val deny = document.createElement("button") as HTMLElement
        deny.className = "chat-action-btn"
        deny.textContent = "Deny"
        deny.style.cssText = "flex:1;padding:12px;font-size:12px;color:var(--danger);border-color:rgba(255,110,132,0.25);"
        deny.addEventListener("click", { close(); onDenied?.invoke() })
        row.appendChild(approve); row.appendChild(deny)
        box.appendChild(row)
    }

    private fun executeApproved(
        serverId: String, toolName: String, arguments: JsonObject
    ) {
        scope.launch {
            try {
                val req = McpToolCallRequestDto(serverId, toolName, arguments, approved = true)
                val resp = ApiClient.post("/api/integrations/mcp/tools/call", req)
                close()
                if (resp.status == HttpStatusCode.OK) {
                    val result = json.decodeFromString<McpToolCallResponseDto>(resp.bodyAsText())
                    onApproved?.invoke(result)
                }
            } catch (_: Exception) { close() }
        }
    }
}
