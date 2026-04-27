package com.assistant.frontend.pages.ticket

import com.assistant.frontend.api.ApiClient
import com.assistant.frontend.models.GeneratedDocumentFull
import com.assistant.frontend.pages.ticket.drawio.DrawioTemplateEngine
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

/** Matches bare `&` not already part of an XML entity like `&amp;` `&lt;` `&gt;` `&quot;`. */
private val BARE_AMPERSAND = Regex("&(?!amp;|lt;|gt;|quot;|apos;|#)")

/** Matches an XML opening tag with attributes. */
private val XML_TAG_WITH_ATTRS = Regex("""<(\w+)\s+([^/>]+?)(/?>)""")

/**
 * Full-width modal panel for previewing generated BRD/FSD documents.
 * Integrates ReviewPanel (approve/reject) and VersionHistoryPanel.
 * Requirements: 7.1, 7.2, 7.3, 7.4, 10.4, 6.3, 7.5, 7.6
 */
internal object DocumentPreviewPanel {

    private val scope = MainScope()
    private var currentDocument: GeneratedDocumentFull? = null
    private var escapeListener: ((dynamic) -> Unit)? = null

    fun open(document: GeneratedDocumentFull) {
        currentDocument = document
        val modal = getModal() ?: return
        populateMetadata(document)
        renderContent(document)
        bindCloseButton()
        bindExportButton(document)
        bindEscapeKey()
        integrateReviewPanel(document)
        integrateVersionHistory(document)
        modal.style.display = "flex"
    }

    fun close() {
        val modal = getModal() ?: return
        modal.style.display = "none"
        unbindEscapeKey()
        DocumentExporter.cleanup()
        ReviewPanel.hide()
        VersionHistoryPanel.hide()
        currentDocument = null
    }

    private fun integrateReviewPanel(doc: GeneratedDocumentFull) {
        val isReader = ApiClient.getUserRole()?.name == "READER"
        val docId = extractDocId(doc)
        ReviewPanel.render(doc, docId, isReader)
    }

    private fun integrateVersionHistory(doc: GeneratedDocumentFull) {
        VersionHistoryPanel.render(doc.ticketId, doc.documentType)
    }

    private fun extractDocId(doc: GeneratedDocumentFull): String? {
        return doc.id?.toString()
    }

    private fun populateMetadata(doc: GeneratedDocumentFull) {
        setTextById("doc-meta-type", formatDocType(doc))
        setTextById("doc-meta-ticket", doc.ticketId)
        setTextById("doc-meta-timestamp", formatTimestamp(doc.generatedAt))
        setTextById("doc-meta-provider", doc.aiProviderUsed)
        setTextById("doc-meta-sources", formatSources(doc.sourceTicketIds))
    }

    private fun formatDocType(doc: GeneratedDocumentFull): String {
        val base = doc.documentType
        val version = doc.versionNumber
        return if (version != null) "$base — Version $version" else base
    }

    private fun renderContent(doc: GeneratedDocumentFull) {
        val contentArea = getContentArea() ?: return
        contentArea.innerHTML = ""
        contentArea.textContent = "Rendering..."
        VersionHistoryPanel.closeDiffView()
        scope.launch {
            val html = MarkdownRenderer.render(doc.markdownContent)
            contentArea.innerHTML = html
            renderDiagramsInContent(contentArea)
            DocumentPreviewToc.render(contentArea)
        }
    }

    private fun renderDiagramsInContent(contentArea: HTMLElement) {
        val codeBlocks = contentArea.querySelectorAll("code")
        val drawioBlocks = mutableListOf<Pair<HTMLElement, String>>()
        val jsonBlocks = mutableListOf<Pair<HTMLElement, String>>()
        for (i in 0 until codeBlocks.length) {
            val code = codeBlocks.item(i) as? HTMLElement ?: continue
            val text = code.textContent ?: continue
            if (text.contains("<mxGraphModel")) {
                drawioBlocks.add(code to text)
            } else if (DocumentPreviewDiagramHelper.isJsonDiagramMetadata(text)) {
                jsonBlocks.add(code to text)
            }
        }
        if (drawioBlocks.isEmpty() && jsonBlocks.isEmpty()) return
        DrawioDiagramRenderer.ensureViewerLoaded(
            onReady = {
                drawioBlocks.forEach { (code, xml) -> renderDrawioBlock(code, xml) }
                jsonBlocks.forEach { (code, json) -> renderJsonDiagramBlock(code, json) }
            },
            onFail = {
                drawioBlocks.forEach { (code, xml) -> renderDrawioBlock(code, xml) }
                jsonBlocks.forEach { (code, json) -> renderJsonDiagramBlock(code, json) }
            }
        )
    }

    private fun renderJsonDiagramBlock(code: HTMLElement, jsonText: String) {
        val metadata = DocumentPreviewDiagramHelper.tryParseJsonMetadata(jsonText) ?: return
        val parent = code.parentElement ?: return
        val holder = document.createElement("div") as HTMLElement
        holder.className = "drawio-inline-diagram"
        parent.replaceWith(holder)
        DrawioTemplateEngine.merge(metadata) { xml ->
            holder.setAttribute("data-drawio-xml", xml.trim())
            tryRenderDrawio(holder)
        }
    }

    private fun renderDrawioBlock(code: HTMLElement, xml: String) {
        val parent = code.parentElement ?: return
        val holder = document.createElement("div") as HTMLElement
        holder.className = "drawio-inline-diagram"
        holder.setAttribute("data-drawio-xml", sanitizeDrawioXml(xml.trim()))
        parent.replaceWith(holder)
        tryRenderDrawio(holder)
    }

    private fun tryRenderDrawio(holder: HTMLElement) {
        val helper = window.asDynamic().__drawioRenderOne
        if (helper != null && helper != undefined) {
            try {
                helper(holder)
                // Verify render produced content; fallback if empty
                window.setTimeout({
                    if (holder.querySelector("svg") == null) showDiagramFallback(holder)
                }, 500)
            } catch (_: dynamic) {
                showDiagramFallback(holder)
            }
        } else {
            showDiagramFallback(holder)
        }
    }

    private fun showDiagramFallback(holder: HTMLElement) {
        val xml = holder.getAttribute("data-drawio-xml") ?: return
        holder.innerHTML = ""
        holder.className = "drawio-inline-diagram drawio-fallback"
        val label = document.createElement("div") as HTMLElement
        label.className = "drawio-fallback-label"
        label.textContent = "📊 Draw.io diagram (viewer unavailable)"
        holder.appendChild(label)
        val pre = document.createElement("pre") as HTMLElement
        pre.className = "drawio-fallback-xml"
        pre.textContent = xml
        holder.appendChild(pre)
    }

    /**
     * Fix common AI-generated XML issues so GraphViewer can parse it:
     * 1. Escape bare `&` → `&amp;` (AI outputs `value="A & B"`)
     * 2. Remove duplicate attributes (AI outputs `edge="1" ... edge="1"`)
     */
    private fun sanitizeDrawioXml(xml: String): String {
        var result = xml.replace(BARE_AMPERSAND) { "&amp;" }
        result = result.replace(XML_TAG_WITH_ATTRS) { match ->
            val tag = match.groupValues[1]
            val attrs = match.groupValues[2]
            val close = match.groupValues[3]
            val deduped = deduplicateAttrs(attrs)
            "<$tag $deduped$close"
        }
        return result
    }

    /** Keep first occurrence of each attribute name. */
    private fun deduplicateAttrs(attrs: String): String {
        val seen = mutableSetOf<String>()
        val attrPattern = Regex("""(\w+)="[^"]*"""")
        return buildString {
            var lastEnd = 0
            attrPattern.findAll(attrs).forEach { m ->
                val name = m.groupValues[1]
                if (name in seen) {
                    append(attrs.substring(lastEnd, m.range.first))
                } else {
                    seen.add(name)
                    append(attrs.substring(lastEnd, m.range.last + 1))
                }
                lastEnd = m.range.last + 1
            }
            if (lastEnd < attrs.length) append(attrs.substring(lastEnd))
        }.trim()
    }

    private fun bindCloseButton() {
        val btn = document.getElementById("btn-doc-close") as? HTMLElement
        btn?.onclick = { close() }
    }

    private fun bindExportButton(doc: GeneratedDocumentFull) {
        DocumentExporter.bindExportHandlers(doc)
    }

    private fun bindEscapeKey() {
        unbindEscapeKey()
        val listener: (dynamic) -> Unit = { e ->
            val ke = e.unsafeCast<KeyboardEvent>()
            if (ke.key == "Escape") close()
        }
        escapeListener = listener
        document.addEventListener("keydown", listener)
    }

    private fun unbindEscapeKey() {
        escapeListener?.let { document.removeEventListener("keydown", it) }
        escapeListener = null
    }

    private fun formatTimestamp(iso: String): String =
        if (iso.isBlank()) "" else iso.replace("T", " ").take(19)

    private fun formatSources(ids: List<String>): String =
        if (ids.isEmpty()) "" else "Sources: ${ids.joinToString(", ")}"

    private fun setTextById(id: String, text: String) {
        (document.getElementById(id) as? HTMLElement)?.textContent = text
    }

    private fun getModal(): HTMLElement? =
        document.getElementById("doc-preview-modal") as? HTMLElement

    private fun getContentArea(): HTMLElement? =
        document.getElementById("doc-content-area") as? HTMLElement
}
