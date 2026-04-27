package com.assistant.frontend.components.chat

import com.assistant.chat.ToolPermissionsBulkRequest
import com.assistant.chat.ToolPermissionsResponse
import com.assistant.chat.ToolPermissionsUpdateRequest
import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Tool Permissions section in AI Chat Sidebar.
 * Loads per-user permissions, renders grouped by server, toggle per-tool.
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7
 */
object ChatToolPermissions {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var bodyEl: HTMLElement? = null
    private var arrowEl: HTMLElement? = null
    private var permissions = mutableMapOf<String, String>()
    private var defaults = mapOf<String, String>()
    private var isDefault = true

    fun init() {
        bodyEl = document.getElementById("tool-perms-body") as? HTMLElement
        val header = document.getElementById("tool-perms-toggle") as? HTMLElement
        arrowEl = header?.querySelector(".toggle-arrow") as? HTMLElement
        header?.addEventListener("click", { toggleSection() })
    }

    private fun toggleSection() {
        val expanded = bodyEl?.classList?.toggle("expanded") ?: false
        if (expanded) { arrowEl?.classList?.add("expanded"); load() }
        else arrowEl?.classList?.remove("expanded")
    }

    fun load() {
        val body = bodyEl ?: return
        body.innerHTML = ""
        body.appendChild(createLoadingEl())
        scope.launch { fetchAndRender() }
    }

    private suspend fun fetchAndRender() {
        try {
            val resp = ApiClient.get("/api/chat/tool-permissions")
            if (resp.status == HttpStatusCode.OK) {
                val data = json.decodeFromString<ToolPermissionsResponse>(resp.bodyAsText())
                permissions = data.permissions.toMutableMap()
                defaults = data.defaults
                isDefault = permissions.isEmpty()
                renderGroups()
            } else renderEmpty("Failed to load permissions")
        } catch (e: Exception) {
            console.log("[ChatToolPermissions] Load failed: ${e.message}")
            renderEmpty("Error loading permissions")
        }
    }

    private fun renderGroups() {
        val body = bodyEl ?: return
        body.innerHTML = ""
        val allKeys = (permissions.keys + defaults.keys).toSet()
        if (allKeys.isEmpty()) { renderEmpty("No tools available"); return }
        val grouped = allKeys.groupBy { it.substringBefore("::") }
        for ((server, keys) in grouped.toList().sortedBy { it.first }) {
            body.appendChild(createServerGroup(server, keys))
        }
    }

    private fun createServerGroup(server: String, keys: List<String>): HTMLElement {
        val tmpl = document.getElementById("tmpl-perm-group") as HTMLTemplateElement
        val el = tmpl.content.firstElementChild!!.cloneNode(true) as HTMLElement
        el.querySelector(".perm-group-name")!!.textContent = server
        updateGroupCounter(el, server, keys)
        bindBulkActions(el, server)
        val toolsContainer = el.querySelector(".perm-group-tools") as HTMLElement
        for (key in keys.sorted()) {
            toolsContainer.appendChild(createToolRow(key, server))
        }
        return el
    }

    private fun updateGroupCounter(el: HTMLElement, server: String, keys: List<String>) {
        val enabled = keys.count { getEffectiveState(it) == "enabled" }
        el.querySelector(".perm-group-counter")!!.textContent = "$enabled / ${keys.size} enabled"
    }

    private fun bindBulkActions(el: HTMLElement, server: String) {
        el.querySelector(".perm-enable-all")?.addEventListener("click", { bulkUpdate(server, "enable_all") })
        el.querySelector(".perm-disable-all")?.addEventListener("click", { bulkUpdate(server, "disable_all") })
    }

    private fun createToolRow(key: String, server: String): HTMLElement {
        val tmpl = document.getElementById("tmpl-perm-tool") as HTMLTemplateElement
        val el = tmpl.content.firstElementChild!!.cloneNode(true) as HTMLElement
        val toolName = key.substringAfter("::")
        el.querySelector(".perm-tool-name")!!.textContent = toolName
        if (isDefault) el.querySelector(".perm-tool-default")!!.textContent = "(default)"
        val state = getEffectiveState(key)
        applyToggleState(el, state == "enabled")
        el.querySelector(".perm-toggle-switch")?.addEventListener("click", {
            onToggle(key, el)
        })
        el.setAttribute("data-key", key)
        return el
    }

    private fun getEffectiveState(key: String) = permissions[key] ?: defaults[key] ?: "enabled"

    private fun applyToggleState(el: HTMLElement, on: Boolean) {
        val track = el.querySelector(".perm-toggle-track") as? HTMLElement
        val thumb = el.querySelector(".perm-toggle-thumb") as? HTMLElement
        if (on) { track?.classList?.add("active"); thumb?.classList?.add("active") }
        else { track?.classList?.remove("active"); thumb?.classList?.remove("active") }
    }

    private fun onToggle(key: String, rowEl: HTMLElement) {
        val current = getEffectiveState(key)
        val newState = if (current == "enabled") "disabled" else "enabled"
        permissions[key] = newState
        isDefault = false
        applyToggleState(rowEl, newState == "enabled")
        refreshCounters()
        scope.launch { savePermission(key, newState) }
    }

    private suspend fun savePermission(key: String, state: String) {
        try {
            val req = ToolPermissionsUpdateRequest(mapOf(key to state))
            val resp = ApiClient.put("/api/chat/tool-permissions", req)
            if (resp.status == HttpStatusCode.OK) {
                ToastService.show("✓ Permission updated", "success")
                clearDefaultLabels()
            } else ToastService.show("Failed to save", "error")
        } catch (e: Exception) {
            ToastService.show("Save error: ${e.message}", "error")
        }
    }

    private fun bulkUpdate(serverId: String, action: String) {
        scope.launch {
            try {
                val req = ToolPermissionsBulkRequest(serverId, action)
                val resp = ApiClient.put("/api/chat/tool-permissions/bulk", req)
                if (resp.status == HttpStatusCode.OK) {
                    ToastService.show("✓ Bulk update applied", "success")
                    load()
                } else ToastService.show("Bulk update failed", "error")
            } catch (e: Exception) {
                ToastService.show("Bulk error: ${e.message}", "error")
            }
        }
    }

    private fun refreshCounters() {
        val body = bodyEl ?: return
        val groups = body.querySelectorAll(".perm-group")
        for (i in 0 until groups.length) {
            val group = groups.item(i) as? HTMLElement ?: continue
            val rows = group.querySelectorAll(".perm-tool-row")
            val total = rows.length
            var enabled = 0
            for (j in 0 until total) {
                val row = rows.item(j) as? HTMLElement ?: continue
                val key = row.getAttribute("data-key") ?: continue
                if (getEffectiveState(key) == "enabled") enabled++
            }
            group.querySelector(".perm-group-counter")?.textContent = "$enabled / $total enabled"
        }
    }

    private fun clearDefaultLabels() {
        val labels = bodyEl?.querySelectorAll(".perm-tool-default") ?: return
        for (i in 0 until labels.length) (labels.item(i) as? HTMLElement)?.textContent = ""
    }

    private fun createLoadingEl(): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.className = "perm-loading"; el.textContent = "Loading permissions..."
        return el
    }

    private fun renderEmpty(msg: String) {
        val body = bodyEl ?: return; body.innerHTML = ""
        val el = document.createElement("div") as HTMLElement
        el.className = "perm-empty"; el.textContent = msg
        body.appendChild(el)
    }
}
