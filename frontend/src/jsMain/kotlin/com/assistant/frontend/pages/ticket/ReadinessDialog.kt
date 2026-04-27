package com.assistant.frontend.pages.ticket

import com.assistant.mcp.models.McpHealthResponse
import com.assistant.mcp.models.McpServerHealth
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

/**
 * Readiness warning dialog — shows MCP server status before generation.
 * Uses CompletableDeferred to suspend until user clicks a button.
 * Requirements: 3.1–3.7, 5.2, 5.3
 */
internal object ReadinessDialog {

    private const val OVERLAY_ID = "readiness-overlay"

    private val CRITICAL_SERVERS: Map<String, Set<String>> = mapOf(
        "BRD" to setOf("knowledge_base", "database"),
        "FSD" to setOf("knowledge_base", "database"),
        "REQUIREMENT_SLIDES" to setOf("knowledge_base")
    )

    /** Show readiness dialog. Returns true = "Tiếp tục", false = "Hủy". */
    suspend fun show(response: McpHealthResponse, docType: String): Boolean {
        dismiss()
        val deferred = CompletableDeferred<Boolean>()
        val overlay = createOverlay(deferred)
        overlay.appendChild(createDialog(response, docType, deferred))
        document.body?.appendChild(overlay)
        bindEscapeKey(deferred)
        return deferred.await()
    }

    fun isCriticalForDocType(role: String, docType: String): Boolean =
        CRITICAL_SERVERS[docType]?.contains(role) == true

    fun areAllCriticalDown(servers: List<McpServerHealth>, docType: String): Boolean {
        val roles = CRITICAL_SERVERS[docType] ?: return false
        return roles.isNotEmpty() && roles.all { role -> servers.any { it.role == role && !it.ready } }
    }

    private fun dismiss() { document.getElementById(OVERLAY_ID)?.remove() }

    private fun complete(deferred: CompletableDeferred<Boolean>, value: Boolean) {
        dismiss(); if (!deferred.isCompleted) deferred.complete(value)
    }

    private fun createOverlay(deferred: CompletableDeferred<Boolean>): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.id = OVERLAY_ID; el.className = "readiness-overlay"
        el.addEventListener("click", { e ->
            if ((e.target as? HTMLElement)?.id == OVERLAY_ID) complete(deferred, false)
        })
        return el
    }

    private fun createDialog(
        response: McpHealthResponse, docType: String, deferred: CompletableDeferred<Boolean>
    ): HTMLElement {
        val d = document.createElement("div") as HTMLElement
        d.className = "readiness-dialog glass-card"
        val title = document.createElement("div") as HTMLElement
        title.className = "readiness-title"; title.textContent = "⚠️ MCP Server Status"
        d.appendChild(title)
        appendServerList(d, response.servers, docType)
        appendWarnings(d, response.servers, docType)
        appendButtons(d, deferred)
        return d
    }

    private fun appendServerList(parent: HTMLElement, servers: List<McpServerHealth>, docType: String) {
        val list = document.createElement("div") as HTMLElement
        list.className = "readiness-server-list"
        servers.forEach { list.appendChild(createServerItem(it, docType)) }
        parent.appendChild(list)
    }

    private fun createServerItem(server: McpServerHealth, docType: String): HTMLElement {
        val item = document.createElement("div") as HTMLElement
        val critical = !server.ready && isCriticalForDocType(server.role, docType)
        item.className = buildString {
            append("readiness-server-item")
            append(if (server.ready) " readiness-server-ready" else " readiness-server-down")
            if (critical) append(" readiness-critical")
        }
        val icon = document.createElement("span") as HTMLElement
        icon.className = "readiness-icon"; icon.textContent = if (server.ready) "✅" else "⚠️"
        item.appendChild(icon)
        val info = document.createElement("div") as HTMLElement
        info.className = "readiness-server-info"
        val name = document.createElement("span") as HTMLElement
        name.className = "readiness-server-name"; name.textContent = server.serverName
        info.appendChild(name)
        if (!server.ready && server.error != null) {
            val err = document.createElement("span") as HTMLElement
            err.className = "readiness-server-error"; err.textContent = server.error
            info.appendChild(err)
        }
        item.appendChild(info)
        return item
    }

    private fun appendWarnings(parent: HTMLElement, servers: List<McpServerHealth>, docType: String) {
        val warn = document.createElement("div") as HTMLElement
        warn.className = "readiness-warning"
        warn.textContent = "Sinh tài liệu khi có tool không khả dụng có thể cho kết quả thiếu hoặc chất lượng thấp."
        parent.appendChild(warn)
        if (areAllCriticalDown(servers, docType)) {
            val strong = document.createElement("div") as HTMLElement
            strong.className = "readiness-strong-warning"
            strong.textContent = "Các tool quan trọng cho $docType đều không khả dụng — tài liệu sinh ra có thể thiếu dữ liệu nghiêm trọng."
            parent.appendChild(strong)
        }
    }

    private fun appendButtons(parent: HTMLElement, deferred: CompletableDeferred<Boolean>) {
        val row = document.createElement("div") as HTMLElement
        row.className = "readiness-btn-row"
        val cancel = document.createElement("button") as HTMLElement
        cancel.className = "readiness-btn-cancel"; cancel.textContent = "Hủy"
        cancel.addEventListener("click", { complete(deferred, false) })
        val cont = document.createElement("button") as HTMLElement
        cont.className = "readiness-btn-continue"; cont.textContent = "Tiếp tục"
        cont.addEventListener("click", { complete(deferred, true) })
        row.appendChild(cancel); row.appendChild(cont)
        parent.appendChild(row)
    }

    private fun bindEscapeKey(deferred: CompletableDeferred<Boolean>) {
        val listener: (dynamic) -> Unit = { e ->
            if (e.unsafeCast<KeyboardEvent>().key == "Escape") complete(deferred, false)
        }
        document.addEventListener("keydown", listener)
        deferred.invokeOnCompletion { document.removeEventListener("keydown", listener) }
    }
}
