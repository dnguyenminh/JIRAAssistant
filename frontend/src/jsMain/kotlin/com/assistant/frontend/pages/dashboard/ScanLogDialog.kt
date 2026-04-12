package com.assistant.frontend.pages.dashboard

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.ScanLogEntryDTO
import com.assistant.frontend.pages.DashboardPage
import com.assistant.frontend.services.HtmlUtils
import com.assistant.frontend.services.ToastService
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement

/**
 * Progressive scan log dialog — infinite scroll from DB.
 * Loads PAGE_SIZE entries at a time, prepends older on scroll-to-top.
 */
internal object ScanLogDialog {

    private const val PAGE_SIZE = 100
    private var isOpen = false
    private var currentOffset = 0
    private var totalCount = 0
    private var isLoading = false
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class LogResponse(
        val projectKey: String = "",
        val entries: List<ScanLogEntryDTO> = emptyList(),
        val totalCount: Int = 0
    )

    fun bind() {
        el("btn-expand-scan-log")?.addEventListener("click", { open() })
        el("btn-close-scan-log-dialog")?.addEventListener("click", { close() })
        el("btn-clear-scan-log")?.addEventListener("click", { clearLog() })
        el("scan-log-dialog")?.addEventListener("click", { e ->
            if ((e.target as? HTMLElement)?.id == "scan-log-dialog") close()
        })
    }

    fun open() {
        isOpen = true
        currentOffset = 0
        totalCount = 0
        el("scan-log-dialog")?.style?.display = "flex"
        clearBody()
        // Show current inline log entries immediately (from DOM)
        copyInlineLogToDialog()
        // Then load full history from API in background
        loadPage(scrollToBottom = true)
        bindScrollListener()
    }

    private fun copyInlineLogToDialog() {
        val body = el("scan-log-dialog-body") ?: return
        val container = el("scan-log-container") ?: return
        val lines = container.querySelectorAll(".console-line")
        for (i in 0 until lines.length) {
            val clone = lines.item(i)?.cloneNode(true) ?: continue
            body.appendChild(clone)
        }
        if (lines.length > 0) body.scrollTop = body.scrollHeight.toDouble()
    }

    fun close() {
        el("scan-log-dialog")?.style?.display = "none"
        isOpen = false
    }

    fun refreshIfOpen() {
        if (!isOpen) return
        // Append only newest entries (offset=0, small batch)
        appendNewest()
    }

    private fun appendNewest() {
        val projectKey = ApiClient.getProjectKey() ?: return
        DashboardPage.scope.launch {
            try {
                val resp = ApiClient.get("/api/projects/$projectKey/scan/log?limit=20&offset=0")
                if (ApiClient.handleUnauthorized(resp)) return@launch
                val data = json.decodeFromString<LogResponse>(resp.bodyAsText())
                totalCount = data.totalCount
                updateHeader()
                val body = el("scan-log-dialog-body") ?: return@launch
                val existingIds = collectExistingIds(body)
                var appended = false
                for (entry in data.entries.reversed()) {
                    if (entry.id.toString() in existingIds) continue
                    appendLine(body, entry)
                    appended = true
                }
                if (appended) body.scrollTop = body.scrollHeight.toDouble()
            } catch (_: Exception) { /* silent refresh */ }
        }
    }

    private fun loadPage(scrollToBottom: Boolean = false) {
        if (isLoading) return
        val projectKey = ApiClient.getProjectKey() ?: return
        isLoading = true
        showLoadingIndicator()
        DashboardPage.scope.launch {
            try {
                val url = "/api/projects/$projectKey/scan/log?limit=$PAGE_SIZE&offset=$currentOffset"
                val resp = ApiClient.get(url)
                if (ApiClient.handleUnauthorized(resp)) return@launch
                val data = json.decodeFromString<LogResponse>(resp.bodyAsText())
                totalCount = data.totalCount
                updateHeader()
                if (data.entries.isEmpty()) {
                    removeLoadingIndicator()
                    if (currentOffset == 0) showEmpty()
                    return@launch
                }
                renderPage(data.entries, scrollToBottom)
                currentOffset += data.entries.size
            } catch (e: Exception) {
                showError("Failed to load: ${e.message}")
            } finally {
                isLoading = false
                removeLoadingIndicator()
            }
        }
    }

    private fun renderPage(entries: List<ScanLogEntryDTO>, scrollToBottom: Boolean) {
        val body = el("scan-log-dialog-body") ?: return
        removeEmptyMsg(body)
        if (scrollToBottom) {
            // First load: append in chronological order (oldest first)
            for (entry in entries.reversed()) appendLine(body, entry)
            body.scrollTop = body.scrollHeight.toDouble()
        } else {
            // Older page: prepend at top, preserve scroll position
            val prevHeight = body.scrollHeight
            val frag = document.createDocumentFragment()
            for (entry in entries.reversed()) {
                frag.appendChild(createLine(entry))
            }
            body.insertBefore(frag, body.firstChild)
            body.scrollTop = (body.scrollHeight - prevHeight).toDouble()
        }
    }

    private fun bindScrollListener() {
        val body = el("scan-log-dialog-body") ?: return
        body.addEventListener("scroll", {
            if (body.scrollTop < 50.0 && !isLoading && currentOffset < totalCount) {
                loadPage(scrollToBottom = false)
            }
        })
    }

    // --- DOM helpers ---

    private fun appendLine(body: HTMLElement, entry: ScanLogEntryDTO) {
        body.appendChild(createLine(entry))
    }

    private fun createLine(entry: ScanLogEntryDTO): HTMLElement {
        val line = document.createElement("div") as HTMLElement
        line.className = "console-line"
        line.setAttribute("data-id", entry.id.toString())
        val color = statusColor(entry.status)
        val time = ScanLogRenderer.formatTimestamp(entry.timestamp)
        val icon = if (isAttachment(entry.message)) "📎 " else ""
        line.innerHTML = """
            <span class="console-time">${HtmlUtils.escapeHtml(time)}</span>
            <span class="console-tag" style="$color">${HtmlUtils.escapeHtml(entry.status)}</span>
            <span style="color:var(--secondary);font-weight:600;">${HtmlUtils.escapeHtml(entry.ticketId)}</span>
            <span>$icon${HtmlUtils.escapeHtml(entry.message)}</span>
        """.trimIndent()
        return line
    }

    private fun collectExistingIds(body: HTMLElement): Set<String> {
        val ids = mutableSetOf<String>()
        val nodes = body.querySelectorAll("[data-id]")
        for (i in 0 until nodes.length) {
            (nodes.item(i) as? HTMLElement)?.getAttribute("data-id")?.let { ids.add(it) }
        }
        return ids
    }

    private fun statusColor(s: String) = when (s) {
        "COMPLETED" -> "color: var(--primary);"
        "FAILED" -> "color: var(--danger);"
        "ANALYZING" -> "color: var(--accent);"
        else -> ""
    }

    private fun isAttachment(msg: String): Boolean {
        val l = msg.lowercase()
        return (l.startsWith("processed") && l.contains("attachments")) ||
            l.startsWith("attachment ") || l.startsWith("processing attachment")
    }

    private fun updateHeader() {
        el("scan-log-count")?.textContent = "$totalCount entries"
    }

    private fun clearBody() {
        val body = el("scan-log-dialog-body") ?: return
        body.innerHTML = ""
    }

    private fun showEmpty() {
        val body = el("scan-log-dialog-body") ?: return
        body.innerHTML = "<div class='scan-log-empty' style='opacity:0.3;text-align:center;padding:40px;'>No log entries.</div>"
    }

    private fun removeEmptyMsg(body: HTMLElement) {
        body.querySelector(".scan-log-empty")?.let { body.removeChild(it) }
    }

    private fun showLoadingIndicator() {
        val body = el("scan-log-dialog-body") ?: return
        if (body.querySelector(".scan-log-loading") != null) return
        val el = document.createElement("div") as HTMLElement
        el.className = "scan-log-loading"
        el.style.cssText = "text-align:center;padding:12px;opacity:0.4;font-size:11px;"
        el.textContent = "Loading older entries..."
        body.insertBefore(el, body.firstChild)
    }

    private fun removeLoadingIndicator() {
        val body = el("scan-log-dialog-body") ?: return
        body.querySelector(".scan-log-loading")?.let { body.removeChild(it) }
    }

    private fun showError(msg: String) {
        val body = el("scan-log-dialog-body") ?: return
        body.innerHTML = "<div style='color:var(--danger);text-align:center;padding:40px;'>$msg</div>"
    }

    private fun clearLog() {
        if (!window.confirm("Clear all scan log entries for this project?")) return
        val projectKey = ApiClient.getProjectKey() ?: return
        DashboardPage.scope.launch {
            try {
                ApiClient.delete("/api/projects/$projectKey/scan/log")
                ScanLogRenderer.reset()
                clearBody()
                showEmpty()
                totalCount = 0; currentOffset = 0
                updateHeader()
                ToastService.show("Scan log cleared", "success")
            } catch (e: Exception) {
                ToastService.show("Failed to clear: ${e.message}", "error")
            }
        }
    }

    private fun el(id: String): HTMLElement? =
        document.getElementById(id) as? HTMLElement
}
