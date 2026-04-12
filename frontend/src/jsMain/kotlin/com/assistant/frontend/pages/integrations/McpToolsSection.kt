package com.assistant.frontend.pages.integrations

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.McpServerInfo
import com.assistant.frontend.models.McpToolInfoDto
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Expandable tools section on MCP server cards + View Schema modal.
 * Requirements: 6.47
 */
object McpToolsSection {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var currentServerId: String = ""
    private var autoApproveList = mutableListOf<String>()

    fun createSection(serverId: String, toolCount: Int): HTMLElement {
        val wrapper = document.createElement("div") as HTMLElement
        wrapper.className = "mcp-tools-section"
        val toggle = createToggleButton(toolCount)
        val list = document.createElement("div") as HTMLElement
        list.className = "mcp-tools-list"
        list.style.display = "none"
        toggle.addEventListener("click", {
            val visible = list.style.display != "none"
            list.style.display = if (visible) "none" else "block"
            toggle.textContent = if (visible) "▶ Tools ($toolCount)" else "▼ Tools ($toolCount)"
            if (!visible && list.children.length == 0) loadTools(serverId, list)
        })
        wrapper.appendChild(toggle)
        wrapper.appendChild(list)
        return wrapper
    }

    private fun createToggleButton(count: Int): HTMLElement {
        val btn = document.createElement("div") as HTMLElement
        btn.textContent = "▶ Tools ($count)"
        btn.style.cssText = "cursor:pointer;font-size:11px;opacity:0.6;padding:6px 0;letter-spacing:0.5px;"
        return btn
    }

    private fun loadTools(serverId: String, container: HTMLElement) {
        currentServerId = serverId
        container.innerHTML = "<div style='font-size:10px;opacity:0.4;'>Loading tools...</div>"
        scope.launch {
            loadAutoApproveList(serverId)
            try {
                val resp = ApiClient.get("/api/integrations/mcp/$serverId/tools")
                if (resp.status == HttpStatusCode.OK) {
                    val tools = json.decodeFromString<List<McpToolInfoDto>>(resp.bodyAsText())
                    renderTools(container, tools, serverId)
                } else {
                    container.innerHTML = "<div style='font-size:10px;color:var(--danger);'>Server not running</div>"
                }
            } catch (e: Exception) {
                container.innerHTML = "<div style='font-size:10px;color:var(--danger);'>Failed to load tools</div>"
            }
        }
    }

    private suspend fun loadAutoApproveList(serverId: String) {
        try {
            val resp = ApiClient.get("/api/integrations/mcp")
            if (resp.status == HttpStatusCode.OK) {
                val servers = json.decodeFromString<List<McpServerInfo>>(resp.bodyAsText())
                val server = servers.find { it.id == serverId }
                autoApproveList.clear()
                if (server != null) {
                    val parsed = try { json.decodeFromString<List<String>>(server.autoApprove) } catch (_: Exception) { emptyList() }
                    autoApproveList.addAll(parsed)
                }
            }
        } catch (_: Exception) { }
    }

    private fun renderTools(container: HTMLElement, tools: List<McpToolInfoDto>, serverId: String) {
        container.innerHTML = ""
        if (tools.isEmpty()) {
            container.innerHTML = "<div style='font-size:10px;opacity:0.4;'>No tools available</div>"
            return
        }
        for (tool in tools) container.appendChild(createToolRow(tool, serverId))
    }

    private fun createToolRow(tool: McpToolInfoDto, serverId: String): HTMLElement {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;align-items:center;gap:6px;padding:4px 0;font-size:11px;"
        val isApproved = tool.name in autoApproveList
        row.appendChild(createApproveCheckbox(tool.name, isApproved, serverId))
        val icon = document.createElement("span") as HTMLElement
        icon.textContent = "🔧"
        val name = document.createElement("span") as HTMLElement
        name.textContent = tool.name
        name.style.fontWeight = "600"
        val desc = document.createElement("span") as HTMLElement
        desc.textContent = tool.description.take(80)
        desc.style.cssText = "opacity:0.5;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;"
        val schemaBtn = document.createElement("button") as HTMLElement
        schemaBtn.className = "chat-action-btn"
        schemaBtn.textContent = "Schema"
        schemaBtn.style.cssText = "font-size:9px;padding:2px 8px;"
        schemaBtn.addEventListener("click", { McpSchemaModal.open(tool) })
        row.appendChild(icon); row.appendChild(name)
        row.appendChild(desc); row.appendChild(schemaBtn)
        return row
    }

    private fun createApproveCheckbox(toolName: String, checked: Boolean, serverId: String): HTMLElement {
        val cb = document.createElement("input") as HTMLElement
        cb.setAttribute("type", "checkbox")
        if (checked) cb.setAttribute("checked", "checked")
        cb.title = if (checked) "Auto-approved — click to reject" else "Not approved — click to auto-approve"
        cb.style.cssText = "cursor:pointer;accent-color:var(--primary);width:14px;height:14px;flex-shrink:0;"
        cb.addEventListener("change", {
            val isChecked = cb.asDynamic().checked as Boolean
            toggleAutoApprove(toolName, isChecked, serverId, cb)
        })
        return cb
    }

    private fun toggleAutoApprove(toolName: String, approve: Boolean, serverId: String, cb: HTMLElement) {
        if (approve) { if (toolName !in autoApproveList) autoApproveList.add(toolName) }
        else autoApproveList.remove(toolName)
        cb.title = if (approve) "Auto-approved — click to reject" else "Not approved — click to auto-approve"
        saveAutoApproveList(serverId)
    }

    private fun saveAutoApproveList(serverId: String) {
        scope.launch {
            try {
                val server = McpServerCards.servers.find { it.id == serverId } ?: return@launch
                val approveJson = "[" + autoApproveList.joinToString(",") { "\"$it\"" } + "]"
                val updated = server.copy(autoApprove = approveJson)
                ApiClient.put("/api/integrations/mcp/$serverId", updated)
            } catch (_: Exception) { }
        }
    }
}
