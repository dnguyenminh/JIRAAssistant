package com.assistant.frontend.pages.dashboard

import com.assistant.frontend.models.ScanLogEntryDTO
import com.assistant.frontend.services.HtmlUtils
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/**
 * Renders scan log entries with append-only strategy.
 * Uses sessionStorage for renderedIds to avoid memory growth.
 * Caps visible DOM nodes at MAX_VISIBLE to limit browser memory.
 */
internal object ScanLogRenderer {

    private const val STORAGE_KEY = "scanlog_rendered_ids"
    private const val MAX_VISIBLE = 500
    private const val MAX_STORED_IDS = 2000

    fun render(entries: List<ScanLogEntryDTO>) {
        val container = el("scan-log-container") ?: return
        val seen = loadRenderedIds()
        if (entries.isEmpty() && seen.isEmpty()) {
            clearAndShowEmpty(container); return
        }
        removeEmptyPlaceholder(container)
        // Detect DOM was cleared (navigation away/back) — re-render all entries
        val hasVisibleLines = container.querySelectorAll(".console-line").length > 0
        var appended = false
        for (entry in entries) {
            if (hasVisibleLines && entry.id in seen) continue
            seen.add(entry.id)
            appendLogLine(container, entry)
            appended = true
        }
        if (appended) {
            trimOldNodes(container)
            saveRenderedIds(seen)
            container.scrollTop = container.scrollHeight.toDouble()
            ScanLogDialog.refreshIfOpen()
        }
    }

    fun reset() {
        window.sessionStorage.removeItem(STORAGE_KEY)
        val container = el("scan-log-container") ?: return
        clearLines(container)
    }

    // --- SessionStorage helpers ---

    private fun loadRenderedIds(): MutableSet<Long> {
        val raw = window.sessionStorage.getItem(STORAGE_KEY) ?: return mutableSetOf()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toMutableSet()
    }

    private fun saveRenderedIds(ids: Set<Long>) {
        val trimmed = if (ids.size > MAX_STORED_IDS) ids.toList().takeLast(MAX_STORED_IDS) else ids.toList()
        window.sessionStorage.setItem(STORAGE_KEY, trimmed.joinToString(","))
    }

    // --- DOM helpers ---

    private fun trimOldNodes(container: HTMLElement) {
        val lines = container.querySelectorAll(".console-line")
        val excess = lines.length - MAX_VISIBLE
        if (excess <= 0) return
        for (i in 0 until excess) lines.item(i)?.let { container.removeChild(it) }
    }

    private fun clearLines(container: HTMLElement) {
        val lines = container.querySelectorAll(".console-line")
        for (i in 0 until lines.length) lines.item(i)?.let { container.removeChild(it) }
    }

    private fun clearAndShowEmpty(container: HTMLElement) {
        clearLines(container)
        val line = document.createElement("div") as HTMLElement
        line.className = "console-line empty-placeholder"
        line.style.opacity = "0.3"
        line.innerHTML = "<span>No scan log entries yet.</span>"
        container.appendChild(line)
    }

    private fun removeEmptyPlaceholder(container: HTMLElement) {
        container.querySelector(".empty-placeholder")?.let { container.removeChild(it) }
    }

    private fun appendLogLine(container: HTMLElement, entry: ScanLogEntryDTO) {
        val line = document.createElement("div") as HTMLElement
        line.className = "console-line"
        val color = statusColor(entry.status)
        val time = formatTimestamp(entry.timestamp)
        val icon = if (isAttachmentEntry(entry.message)) "📎 " else ""
        line.innerHTML = buildLineHtml(time, entry, color, icon)
        container.appendChild(line)
    }

    private fun buildLineHtml(
        time: String, entry: ScanLogEntryDTO, badgeColor: String, icon: String
    ): String = """
        <span class="console-time">${HtmlUtils.escapeHtml(time)}</span>
        <span class="console-tag" style="$badgeColor">${HtmlUtils.escapeHtml(entry.status)}</span>
        <span style="color:var(--secondary);font-weight:600;">${HtmlUtils.escapeHtml(entry.ticketId)}</span>
        <span>$icon${HtmlUtils.escapeHtml(entry.message)}</span>
    """.trimIndent()

    private fun statusColor(status: String): String = when (status) {
        "COMPLETED" -> "color: var(--primary);"
        "FAILED" -> "color: var(--danger);"
        "ANALYZING" -> "color: var(--accent);"
        else -> ""
    }

    private fun isAttachmentEntry(message: String): Boolean {
        val lower = message.lowercase()
        return (lower.startsWith("processed") && lower.contains("attachments for")) ||
            lower.startsWith("attachment ") || lower.startsWith("processing attachment")
    }

    fun formatTimestamp(isoTimestamp: String): String = try {
        val dateObj = js("new Date(isoTimestamp)")
        val h = (dateObj.getHours() as Int).toString().padStart(2, '0')
        val m = (dateObj.getMinutes() as Int).toString().padStart(2, '0')
        val s = (dateObj.getSeconds() as Int).toString().padStart(2, '0')
        "$h:$m:$s"
    } catch (_: Exception) {
        isoTimestamp.takeLast(12).take(8)
    }

    private fun el(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement
}
