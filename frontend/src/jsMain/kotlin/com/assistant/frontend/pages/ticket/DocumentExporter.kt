package com.assistant.frontend.pages.ticket

import com.assistant.frontend.models.GeneratedDocumentFull
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/**
 * Exports generated documents as Markdown file download or PDF via print.
 * Owns the export dropdown toggle, outside-click dismiss, and export actions.
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */
internal object DocumentExporter {

    private var outsideClickListener: ((Event) -> Unit)? = null

    /**
     * Bind export dropdown toggle + Markdown/PDF export handlers.
     * Call this when the document preview modal opens.
     */
    fun bindExportHandlers(doc: GeneratedDocumentFull) {
        bindDropdownToggle()
        bindExportActions(doc)
        bindOutsideClick()
    }

    /** Export document as Markdown file with metadata header. */
    fun exportMarkdown(doc: GeneratedDocumentFull) {
        val header = buildMarkdownHeader(doc)
        val content = header + doc.markdownContent
        val filename = buildFilename(doc.ticketId, doc.documentType)
        downloadBlob(content, filename, "text/markdown")
    }

    /** Export current preview as PDF via browser print dialog. */
    fun exportPdf() {
        applyPrintMode()
        window.print()
        removePrintMode()
    }

    /** Remove outside-click listener. Call when modal closes. */
    fun cleanup() {
        removeOutsideClick()
        hideDropdown()
    }

    private fun bindDropdownToggle() {
        val btn = document.getElementById("btn-doc-export") as? HTMLElement
        btn?.onclick = { e ->
            e.stopPropagation()
            toggleDropdown()
        }
    }

    private fun bindExportActions(doc: GeneratedDocumentFull) {
        val btnMd = document.getElementById("btn-export-md") as? HTMLElement
        val btnPdf = document.getElementById("btn-export-pdf") as? HTMLElement
        btnMd?.onclick = { exportMarkdown(doc); hideDropdown() }
        btnPdf?.onclick = { exportPdf(); hideDropdown() }
    }

    private fun bindOutsideClick() {
        removeOutsideClick()
        val listener: (Event) -> Unit = { e ->
            val dropdown = document.getElementById("doc-export-dropdown")
            val btn = document.getElementById("btn-doc-export")
            val target = e.target as? HTMLElement
            val isInside = dropdown?.contains(target) == true ||
                btn?.contains(target) == true
            if (!isInside) hideDropdown()
        }
        outsideClickListener = listener
        document.addEventListener("click", listener)
    }

    private fun removeOutsideClick() {
        outsideClickListener?.let {
            document.removeEventListener("click", it)
        }
        outsideClickListener = null
    }

    private fun toggleDropdown() {
        val dd = document.getElementById("doc-export-dropdown") as? HTMLElement
            ?: return
        dd.style.display = if (dd.style.display == "block") "none" else "block"
    }

    private fun hideDropdown() {
        val dd = document.getElementById("doc-export-dropdown") as? HTMLElement
        dd?.style?.display = "none"
    }

    private fun buildMarkdownHeader(doc: GeneratedDocumentFull): String {
        val lines = mutableListOf<String>()
        lines.add("# ${doc.documentType} — ${doc.ticketId}")
        lines.add("")
        lines.add("- **Type:** ${doc.documentType}")
        lines.add("- **Ticket:** ${doc.ticketId}")
        lines.add("- **Generated:** ${doc.generatedAt}")
        if (doc.sourceTicketIds.isNotEmpty()) {
            lines.add("- **Source Tickets:** ${doc.sourceTicketIds.joinToString(", ")}")
        }
        if (doc.aiProviderUsed.isNotBlank()) {
            lines.add("- **AI Provider:** ${doc.aiProviderUsed}")
        }
        lines.add("")
        lines.add("---")
        lines.add("")
        return lines.joinToString("\n")
    }

    private fun buildFilename(ticketId: String, docType: String): String =
        "${ticketId}-${docType}.md"

    private fun downloadBlob(content: String, filename: String, mime: String) {
        val blob = Blob(arrayOf(content), BlobPropertyBag(type = mime))
        val url = URL.createObjectURL(blob)
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = filename
        document.body?.appendChild(anchor)
        anchor.click()
        anchor.remove()
        URL.revokeObjectURL(url)
    }

    private fun applyPrintMode() {
        val modal = document.getElementById("doc-preview-modal")
        modal?.classList?.add("print-mode")
    }

    private fun removePrintMode() {
        val modal = document.getElementById("doc-preview-modal")
        modal?.classList?.remove("print-mode")
    }
}
