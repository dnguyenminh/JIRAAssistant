package com.assistant.frontend.pages.ticket.drawio

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/**
 * Creates a download link for .drawio files when the viewer is unavailable.
 * Requirements: 9.1, 9.2, 9.3
 */
internal object DrawioDownloadHelper {

    fun showFallback(container: HTMLElement, xml: String, title: String) {
        container.innerHTML = ""
        val blobUrl = createBlobUrl(xml)
        val fileName = sanitizeFileName(title)
        appendMessage(container)
        appendDownloadLink(container, blobUrl, fileName)
    }

    private fun createBlobUrl(xml: String): String {
        val blob = js("new Blob([xml], {type: 'application/xml'})")
        return js("URL.createObjectURL(blob)") as String
    }

    private fun sanitizeFileName(title: String): String {
        val name = title.ifBlank { "diagram" }
            .replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
        return "$name.drawio"
    }

    private fun appendMessage(container: HTMLElement) {
        val msg = document.createElement("div") as HTMLElement
        msg.textContent = "Không thể render diagram. " +
            "Tải file .drawio để mở trong draw.io desktop hoặc app.diagrams.net"
        msg.style.cssText = "font-size:13px;opacity:0.7;margin-bottom:12px;" +
            "line-height:1.6;color:rgba(255,255,255,0.7);"
        container.appendChild(msg)
    }

    private fun appendDownloadLink(container: HTMLElement, url: String, fileName: String) {
        val link = document.createElement("a") as HTMLElement
        link.setAttribute("href", url)
        link.setAttribute("download", fileName)
        link.textContent = "\u2B07 Download $fileName"
        link.style.cssText = "display:inline-block;padding:8px 16px;" +
            "background:rgba(45,254,207,0.15);color:var(--primary);" +
            "border:1px solid rgba(45,254,207,0.3);border-radius:6px;" +
            "font-size:12px;font-weight:600;text-decoration:none;cursor:pointer;"
        container.appendChild(link)
    }
}
