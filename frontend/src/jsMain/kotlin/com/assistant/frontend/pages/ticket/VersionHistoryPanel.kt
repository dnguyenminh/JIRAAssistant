package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GeneratedDocumentFull
import com.assistant.frontend.models.VersionMeta
import io.ktor.client.statement.*
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Version history dropdown + diff view in doc-preview-modal.
 * Requirements: 7.5, 7.6, 7.7, 7.8, 7.9
 */
internal object VersionHistoryPanel {

    private val scope = MainScope()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var versions: List<VersionMeta> = emptyList()
    private var currentTicketId: String? = null
    private var currentDocType: String? = null

    @Serializable
    data class DiffResponse(val diff: String)

    /** Render version history area based on available versions (Req 7.6). */
    fun render(ticketId: String, docType: String) {
        currentTicketId = ticketId
        currentDocType = docType
        val area = document.getElementById("version-history-area") as? HTMLElement ?: return
        scope.launch {
            try {
                versions = fetchVersions(ticketId, docType)
                if (versions.isNotEmpty()) {
                    area.style.display = "block"
                    bindDropdownToggle()
                } else {
                    area.style.display = "none"
                }
            } catch (e: Exception) {
                println("[VersionHistoryPanel] Error fetching versions: ${e.message}")
                area.style.display = "none"
            }
        }
    }

    fun hide() {
        (document.getElementById("version-history-area") as? HTMLElement)?.style?.display = "none"
        closeDiffView()
    }

    private suspend fun fetchVersions(ticketId: String, docType: String): List<VersionMeta> {
        val typePath = docType.lowercase()
        val resp = ApiClient.get("/api/analysis/$ticketId/documents/$typePath/versions")
        if (resp.status.value >= 400) return emptyList()
        return json.decodeFromString(resp.bodyAsText())
    }

    private fun bindDropdownToggle() {
        val btn = document.getElementById("btn-version-history") as? HTMLElement ?: return
        btn.onclick = { toggleDropdown() }
    }

    private fun toggleDropdown() {
        val dropdown = document.getElementById("version-history-dropdown") as? HTMLElement ?: return
        if (dropdown.style.display == "none") {
            renderVersionList(dropdown)
            dropdown.style.display = "block"
        } else {
            dropdown.style.display = "none"
        }
    }

    private fun renderVersionList(dropdown: HTMLElement) {
        dropdown.innerHTML = ""
        versions.forEach { v -> dropdown.appendChild(createVersionItem(v)) }
        if (versions.size >= 2) {
            dropdown.appendChild(createCompareButton())
        }
        if (versions.isEmpty()) {
            val empty = document.createElement("div") as HTMLElement
            empty.className = "version-history-empty"
            empty.textContent = "No approved versions yet"
            dropdown.appendChild(empty)
        }
    }

    private fun createVersionItem(v: VersionMeta): HTMLElement {
        val tmpl = document.getElementById("tmpl-version-item") as? HTMLTemplateElement
        val el = tmpl?.content?.firstElementChild?.cloneNode(true) as? HTMLElement
            ?: return createFallbackVersionItem(v)
        el.querySelector(".version-number")?.textContent = "Version ${v.versionNumber}"
        el.querySelector(".version-date")?.textContent = formatDate(v.generatedAt)
        el.querySelector(".version-reviewer")?.textContent = v.reviewedBy ?: ""
        el.addEventListener("click", { loadVersion(v.versionNumber) })
        return el
    }

    private fun createCompareButton(): HTMLElement {
        val btn = document.createElement("div") as HTMLElement
        btn.className = "version-compare-btn"
        btn.textContent = "📊 Compare Versions"
        btn.addEventListener("click", { showDiffView() })
        return btn
    }

    private fun loadVersion(versionNumber: Int) {
        val ticketId = currentTicketId ?: return
        val docType = currentDocType ?: return
        closeDropdown()
        scope.launch {
            try {
                val typePath = docType.lowercase()
                val url = "/api/analysis/$ticketId/documents/$typePath/versions/$versionNumber"
                val resp = ApiClient.get(url)
                if (resp.status.value >= 400) return@launch
                val doc: GeneratedDocumentFull = json.decodeFromString(resp.bodyAsText())
                DocumentPreviewPanel.open(doc)
            } catch (_: Exception) { /* silent */ }
        }
    }

    private fun showDiffView() {
        if (versions.size < 2) return
        val ticketId = currentTicketId ?: return
        val docType = currentDocType ?: return
        val v1 = versions.last().versionNumber
        val v2 = versions.first().versionNumber
        closeDropdown()
        scope.launch {
            try {
                val typePath = docType.lowercase()
                val url = "/api/analysis/$ticketId/documents/$typePath/diff?v1=$v1&v2=$v2"
                val resp = ApiClient.get(url)
                if (resp.status.value >= 400) return@launch
                val diffResp: DiffResponse = json.decodeFromString(resp.bodyAsText())
                renderDiff(diffResp.diff, v1, v2)
            } catch (_: Exception) { /* silent */ }
        }
    }

    private fun renderDiff(diff: String, v1: Int, v2: Int) {
        val diffArea = document.getElementById("doc-diff-area") as? HTMLElement ?: return
        val contentArea = document.getElementById("doc-content-area") as? HTMLElement
        contentArea?.style?.display = "none"
        diffArea.style.display = "block"
        diffArea.innerHTML = ""
        val header = document.createElement("div") as HTMLElement
        header.className = "diff-header"
        header.textContent = "Diff: Version $v1 ↔ Version $v2"
        diffArea.appendChild(header)
        val closeBtn = document.createElement("button") as HTMLElement
        closeBtn.className = "btn-diff-close"
        closeBtn.textContent = "✕ Close Diff"
        closeBtn.addEventListener("click", { closeDiffView() })
        diffArea.appendChild(closeBtn)
        val pre = document.createElement("pre") as HTMLElement
        pre.className = "diff-content"
        pre.textContent = diff
        diffArea.appendChild(pre)
    }

    fun closeDiffView() {
        val diffArea = document.getElementById("doc-diff-area") as? HTMLElement
        val contentArea = document.getElementById("doc-content-area") as? HTMLElement
        diffArea?.style?.display = "none"
        contentArea?.style?.display = "block"
    }

    private fun closeDropdown() {
        (document.getElementById("version-history-dropdown") as? HTMLElement)?.style?.display = "none"
    }

    private fun createFallbackVersionItem(v: VersionMeta): HTMLElement {
        val el = document.createElement("div") as HTMLElement
        el.className = "version-history-item"
        el.textContent = "Version ${v.versionNumber} — ${formatDate(v.generatedAt)}"
        el.addEventListener("click", { loadVersion(v.versionNumber) })
        return el
    }

    private fun formatDate(iso: String): String =
        if (iso.isBlank()) "" else iso.replace("T", " ").take(16)
}
