package com.assistant.frontend.pages.integrations

import com.assistant.chat.ToolPermissionsBulkRequest
import com.assistant.chat.ToolPermissionsResponse
import com.assistant.chat.ToolPermissionsUpdateRequest
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.McpToolInfoDto
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Expandable tools section on MCP server cards + View Schema modal.
 * Toggle reads/writes per-user permissions via /api/chat/tool-permissions.
 * Requirements: 5.1, 5.2, 5.3, 5.4, 6.47
 */
object McpToolsSection {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private var currentServerId: String = ""
    private var permissions = mutableMapOf<String, String>()

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
            val count = if (list.children.length > 0) list.children.length else toolCount
            toggle.textContent = if (visible) "▶ Tools ($count)" else "▼ Tools ($count)"
            if (!visible && list.children.length == 0) loadTools(serverId, list, toggle)
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

    private fun loadTools(serverId: String, container: HTMLElement, toggle: HTMLElement) {
        currentServerId = serverId
        container.innerHTML = "<div style='font-size:10px;opacity:0.4;'>Loading tools...</div>"
        scope.launch {
            loadPermissions()
            fetchToolsList(serverId, container, toggle)
        }
    }

    /** Task 10.1: Load per-user permissions from GET /api/chat/tool-permissions. Req: 5.2 */
    private suspend fun loadPermissions() {
        try {
            val resp = ApiClient.get("/api/chat/tool-permissions")
            if (resp.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<ToolPermissionsResponse>(resp.bodyAsText())
                permissions.clear()
                permissions.putAll(data.permissions)
            }
        } catch (_: Exception) { }
    }

    private suspend fun fetchToolsList(serverId: String, container: HTMLElement, toggle: HTMLElement) {
        try {
            val resp = ApiClient.get("/api/integrations/mcp/$serverId/tools")
            if (resp.status == HttpStatusCode.OK) {
                val tools = json.decodeFromString<List<McpToolInfoDto>>(resp.bodyAsText())
                renderTools(container, tools, serverId)
                toggle.textContent = "▼ Tools (${tools.size})"
            } else {
                container.innerHTML = "<div style='font-size:10px;color:var(--danger);'>Server not running</div>"
            }
        } catch (e: Exception) {
            container.innerHTML = "<div style='font-size:10px;color:var(--danger);'>Failed to load tools</div>"
        }
    }

    private fun renderTools(container: HTMLElement, tools: List<McpToolInfoDto>, serverId: String) {
        container.innerHTML = ""
        if (tools.isEmpty()) {
            container.innerHTML = "<div style='font-size:10px;opacity:0.4;'>No tools available</div>"
            return
        }
        container.appendChild(createBulkActions(tools, serverId, container))
        for (tool in tools) container.appendChild(createToolRow(tool, serverId))
    }

    /** Task 10.3: Bulk actions call PUT /api/chat/tool-permissions/bulk. Req: 5.3 */
    private fun createBulkActions(
        tools: List<McpToolInfoDto>, serverId: String, container: HTMLElement
    ): HTMLElement {
        val bar = document.createElement("div") as HTMLElement
        bar.style.cssText = "display:flex;gap:8px;padding:4px 0 6px;border-bottom:1px solid rgba(255,255,255,0.06);margin-bottom:4px;"
        bar.appendChild(createBulkBtn("✅ Enable All") { bulkUpdate(serverId, "enable_all", tools, container) })
        bar.appendChild(createBulkBtn("❌ Disable All") { bulkUpdate(serverId, "disable_all", tools, container) })
        val enabledCount = tools.count { isToolEnabled(serverId, it.name) }
        val label = document.createElement("span") as HTMLElement
        label.style.cssText = "font-size:10px;opacity:0.4;margin-left:auto;align-self:center;"
        label.textContent = "$enabledCount / ${tools.size} enabled"
        bar.appendChild(label)
        return bar
    }

    private fun createBulkBtn(text: String, handler: () -> Unit): HTMLElement {
        val btn = document.createElement("button") as HTMLElement
        btn.className = "chat-action-btn"
        btn.textContent = text
        btn.style.cssText = "font-size:9px;padding:2px 10px;"
        btn.addEventListener("click", { handler() })
        return btn
    }

    private fun bulkUpdate(
        serverId: String, action: String, tools: List<McpToolInfoDto>, container: HTMLElement
    ) {
        scope.launch {
            try {
                val req = ToolPermissionsBulkRequest(serverId, action)
                val resp = ApiClient.put("/api/chat/tool-permissions/bulk", req)
                if (resp.status == HttpStatusCode.OK) {
                    ToastService.show("✓ Bulk update applied", "success")
                    loadPermissions()
                    renderTools(container, tools, serverId)
                } else ToastService.show("Bulk update failed", "error")
            } catch (e: Exception) {
                ToastService.show("Bulk error: ${e.message}", "error")
            }
        }
    }

    private fun isToolEnabled(serverId: String, toolName: String): Boolean {
        val key = "$serverId::$toolName"
        return permissions[key] != "disabled"
    }

    private fun createToolRow(tool: McpToolInfoDto, serverId: String): HTMLElement {
        val row = document.createElement("div") as HTMLElement
        row.style.cssText = "display:flex;align-items:center;gap:6px;padding:4px 0;font-size:11px;"
        val enabled = isToolEnabled(serverId, tool.name)
        row.appendChild(createPermissionCheckbox(tool.name, enabled, serverId))
        appendToolInfo(row, tool)
        return row
    }

    private fun appendToolInfo(row: HTMLElement, tool: McpToolInfoDto) {
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
    }

    /** Task 10.2 + 10.4: Toggle calls PUT /api/chat/tool-permissions. Tooltip per Req: 5.1, 5.4 */
    private fun createPermissionCheckbox(toolName: String, enabled: Boolean, serverId: String): HTMLElement {
        val cb = document.createElement("input") as HTMLElement
        cb.setAttribute("type", "checkbox")
        if (enabled) cb.setAttribute("checked", "checked")
        cb.title = tooltipText(enabled)
        cb.style.cssText = "cursor:pointer;accent-color:var(--primary);width:14px;height:14px;flex-shrink:0;"
        cb.addEventListener("change", {
            val isChecked = cb.asDynamic().checked as Boolean
            togglePermission(serverId, toolName, isChecked, cb)
        })
        return cb
    }

    /** Task 10.4: Tooltip text. Req: 5.4 */
    private fun tooltipText(enabled: Boolean): String =
        if (enabled) "Enabled — AI có thể sử dụng tool này"
        else "Disabled — AI không thể sử dụng tool này"

    /** Task 10.2: Toggle calls PUT /api/chat/tool-permissions per-user. Req: 5.1 */
    private fun togglePermission(serverId: String, toolName: String, enable: Boolean, cb: HTMLElement) {
        val key = "$serverId::$toolName"
        val state = if (enable) "enabled" else "disabled"
        permissions[key] = state
        cb.title = tooltipText(enable)
        scope.launch {
            try {
                val req = ToolPermissionsUpdateRequest(mapOf(key to state))
                val resp = ApiClient.put("/api/chat/tool-permissions", req)
                if (resp.status == HttpStatusCode.OK) {
                    ToastService.show("✓ Permission updated", "success")
                } else ToastService.show("Failed to save", "error")
            } catch (e: Exception) {
                ToastService.show("Save error: ${e.message}", "error")
            }
        }
    }
}
