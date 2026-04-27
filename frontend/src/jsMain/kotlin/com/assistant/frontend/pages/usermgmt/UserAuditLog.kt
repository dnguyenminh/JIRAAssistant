package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.AuditLogEntry
import com.assistant.frontend.services.HtmlUtils
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Manages the audit log console in User Management page.
 */
internal object UserAuditLog {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val auditLog = mutableListOf<AuditLogEntry>()

    fun clear() { auditLog.clear() }

    /** Fetch audit entries from backend (source of truth). */
    suspend fun loadFromBackend() {
        try {
            val response = ApiClient.get("/api/users/audit-log")
            val body = response.bodyAsText()
            val entries = json.decodeFromString<List<AuditLogEntry>>(body)
            auditLog.clear()
            auditLog.addAll(entries)
            renderConsole()
        } catch (e: Exception) {
            console.log("[UserAuditLog] Failed to load: ${e.message}")
            showFetchError()
        }
    }

    /** Optimistic local display for immediate UI feedback. */
    fun addEntry(tag: String, description: String) {
        val now = js("new Date()")
        val h = (now.getHours() as Int).toString().padStart(2, '0')
        val m = (now.getMinutes() as Int).toString().padStart(2, '0')
        val s = (now.getSeconds() as Int).toString().padStart(2, '0')

        val entry = AuditLogEntry(
            timestamp = "$h:$m:$s",
            actor = ApiClient.getUserEmail() ?: "unknown",
            action = tag,
            newValue = description
        )
        auditLog.add(0, entry)
        renderConsole()
    }

    private fun showFetchError() {
        val el = document.getElementById("um-audit-console") as? HTMLElement
            ?: return
        el.innerHTML = ""
        val line = document.createElement("div") as HTMLElement
        line.className = "console-line"
        line.style.opacity = "0.6"
        line.textContent = "Failed to load audit log"
        el.appendChild(line)
    }

    private fun renderConsole() {
        val consoleEl = document.getElementById("um-audit-console")
            as? HTMLElement ?: return
        consoleEl.innerHTML = ""

        if (auditLog.isEmpty()) {
            val placeholder = document.createElement("div") as HTMLElement
            placeholder.className = "console-line"
            placeholder.style.opacity = "0.3"
            placeholder.textContent = "Awaiting audit events..."
            consoleEl.appendChild(placeholder)
            return
        }

        for (entry in auditLog) {
            consoleEl.appendChild(createConsoleLine(entry))
        }
    }

    private fun createConsoleLine(entry: AuditLogEntry): HTMLElement {
        val tagColor = when (entry.action) {
            "IAM_SYNC" -> "var(--primary)"
            "ROLE_CHANGE" -> "var(--secondary)"
            "USER_LOGIN" -> "var(--accent)"
            else -> "var(--warning)"
        }
        val line = document.createElement("div") as HTMLElement
        line.className = "console-line"
        line.innerHTML = """
            <span class="console-time">[${HtmlUtils.escapeHtml(entry.timestamp)}]</span>
            <span class="console-tag" style="color:$tagColor;">${HtmlUtils.escapeHtml(entry.action)}</span>
            <span>${HtmlUtils.escapeHtml(entry.newValue)}</span>
        """.trimIndent()
        return line
    }
}
