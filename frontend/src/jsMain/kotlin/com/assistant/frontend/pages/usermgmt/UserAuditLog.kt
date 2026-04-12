package com.assistant.frontend.pages.usermgmt

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.AuditLogEntry
import com.assistant.frontend.services.HtmlUtils
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Manages the audit log console in User Management page.
 */
internal object UserAuditLog {

    private val auditLog = mutableListOf<AuditLogEntry>()

    fun clear() { auditLog.clear() }

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

    private fun renderConsole() {
        val consoleEl = document.getElementById("um-audit-console") as? HTMLElement ?: return
        consoleEl.innerHTML = ""

        if (auditLog.isEmpty()) {
            consoleEl.innerHTML = "<div class=\"console-line\" style=\"opacity:0.3;\"><span>Awaiting audit events...</span></div>"
            return
        }

        for (entry in auditLog) {
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
            consoleEl.appendChild(line)
        }
    }
}
