package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.CascadeLogEntry
import com.assistant.ai.deepanalysis.models.CascadeLogStatus
import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Renders individual cascade log entries with color-coded status badges.
 * Each entry: [STATUS] ticketKey — message
 * Requirements: 26.9
 */
internal object CascadeLogEntryRenderer {

    /** Create a single log line DOM element. */
    fun createLogLine(entry: CascadeLogEntry): HTMLElement {
        val line = document.createElement("div") as HTMLElement
        line.classList.add("cascade-log-line")
        line.style.cssText = "display:flex;align-items:center;gap:8px;" +
            "padding:4px 0;font-family:'JetBrains Mono',monospace;" +
            "font-size:12px;line-height:1.6;"
        line.appendChild(createStatusBadge(entry.status))
        line.appendChild(createTicketKey(entry.ticketKey))
        line.appendChild(createMessage(entry.message))
        return line
    }

    private fun createStatusBadge(status: CascadeLogStatus): HTMLElement {
        val badge = document.createElement("span") as HTMLElement
        val (label, color) = statusDisplay(status)
        badge.textContent = "[$label]"
        badge.style.cssText = "color:$color;font-weight:700;" +
            "letter-spacing:0.5px;flex-shrink:0;min-width:100px;"
        return badge
    }

    private fun createTicketKey(key: String): HTMLElement {
        val el = document.createElement("span") as HTMLElement
        el.textContent = if (key.isNotBlank()) key else ""
        el.style.cssText = "color:var(--primary);font-weight:600;" +
            "flex-shrink:0;min-width:80px;"
        return el
    }

    private fun createMessage(msg: String): HTMLElement {
        val el = document.createElement("span") as HTMLElement
        el.textContent = if (msg.isNotBlank()) "— $msg" else ""
        el.style.cssText = "opacity:0.7;overflow:hidden;" +
            "text-overflow:ellipsis;white-space:nowrap;"
        return el
    }

    /** Map status enum to display label and CSS color. */
    fun statusDisplay(status: CascadeLogStatus): Pair<String, String> =
        when (status) {
            CascadeLogStatus.DISCOVERED -> "DISCOVERED" to "var(--accent)"
            CascadeLogStatus.ANALYZING -> "ANALYZING" to "#ffb432"
            CascadeLogStatus.COMPLETED -> "COMPLETED" to "var(--primary)"
            CascadeLogStatus.SKIPPED -> "SKIPPED" to "rgba(255,255,255,0.4)"
            CascadeLogStatus.FAILED -> "FAILED" to "#ff5050"
            CascadeLogStatus.CASCADE -> "CASCADE" to "#be9dff"
            CascadeLogStatus.DONE -> "DONE" to "var(--primary)"
        }

    /** Create the summary line shown when cascade is DONE. */
    fun createSummaryLine(
        completed: Int, failed: Int, total: Int
    ): HTMLElement {
        val line = document.createElement("div") as HTMLElement
        line.classList.add("cascade-summary-line")
        line.style.cssText = "display:flex;align-items:center;gap:8px;" +
            "padding:8px 12px;margin-top:8px;border-radius:6px;" +
            "background:rgba(45,254,207,0.06);" +
            "border:1px solid rgba(45,254,207,0.15);" +
            "font-family:'JetBrains Mono',monospace;font-size:12px;"
        val icon = document.createElement("span") as HTMLElement
        icon.textContent = "✓"
        icon.style.cssText = "color:var(--primary);font-weight:700;"
        line.appendChild(icon)
        val text = document.createElement("span") as HTMLElement
        text.textContent = "Cascade complete: $completed analyzed, " +
            "$failed failed, $total total"
        text.style.cssText = "color:var(--primary);opacity:0.9;"
        line.appendChild(text)
        return line
    }
}
