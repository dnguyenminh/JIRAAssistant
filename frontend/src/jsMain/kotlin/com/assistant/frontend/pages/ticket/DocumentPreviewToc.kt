package com.assistant.frontend.pages.ticket

import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTemplateElement

/**
 * Table of Contents sidebar for Document Preview.
 * Extracts H2 headings from rendered HTML and creates
 * clickable TOC entries via template clone.
 * Requirements: 7.3
 */
internal object DocumentPreviewToc {

    private var sectionIdCounter = 0

    /**
     * Render TOC sidebar from H2 headings found in the content area.
     * Clones tmpl-toc-item template for each heading.
     */
    fun render(contentArea: Element) {
        val tocList = getTocList() ?: return
        tocList.innerHTML = ""
        sectionIdCounter = 0

        val headings = contentArea.querySelectorAll("h2")
        for (i in 0 until headings.length) {
            val heading = headings.item(i) as? HTMLElement ?: continue
            val entry = createTocEntry(heading, contentArea)
            if (entry != null) tocList.appendChild(entry)
        }
    }

    private fun createTocEntry(heading: HTMLElement, contentArea: Element): HTMLElement? {
        val tmpl = document.getElementById("tmpl-toc-item") as? HTMLTemplateElement
            ?: return null
        val sectionId = assignSectionId(heading)
        val el = tmpl.content.firstElementChild?.cloneNode(true) as? HTMLElement
            ?: return null

        el.setAttribute("data-section-id", sectionId)
        val textSpan = el.querySelector(".toc-entry-text") as? HTMLElement
        textSpan?.textContent = heading.textContent ?: ""

        el.onclick = { scrollToSection(sectionId, contentArea) }
        return el
    }

    private fun assignSectionId(heading: HTMLElement): String {
        val id = "doc-section-${sectionIdCounter++}"
        heading.id = id
        return id
    }

    private fun scrollToSection(sectionId: String, @Suppress("UNUSED_PARAMETER") contentArea: Element) {
        val target = document.getElementById(sectionId) as? HTMLElement ?: return
        val options = js("({behavior:'smooth',block:'start'})")
        target.asDynamic().scrollIntoView(options)
    }

    private fun getTocList(): HTMLElement? =
        document.getElementById("doc-toc-list") as? HTMLElement
}
