package com.assistant.frontend.pages.ticket

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.KeyboardEvent

/**
 * Slide-by-slide preview panel for Requirement Summary Slides.
 * Splits Markdown by `---` separators, renders each slide via MarkdownRenderer,
 * provides Previous/Next navigation and slide counter.
 * Requirements: 11.6
 */
internal object SlidePreviewPanel {

    private val scope = MainScope()
    private var slides: List<String> = emptyList()
    private var currentIndex = 0
    private var escapeListener: ((dynamic) -> Unit)? = null

    /** Open slide preview modal with markdown content split by --- */
    fun open(markdownContent: String) {
        slides = splitSlides(markdownContent)
        currentIndex = 0
        val modal = getModal() ?: return
        bindNavigation()
        bindCloseButton()
        bindEscapeKey()
        renderCurrentSlide()
        updateCounter()
        modal.style.display = "flex"
    }

    /** Close the slide preview modal. */
    fun close() {
        val modal = getModal() ?: return
        modal.style.display = "none"
        unbindEscapeKey()
        slides = emptyList()
        currentIndex = 0
    }

    private fun splitSlides(markdown: String): List<String> {
        if (markdown.isBlank()) return listOf("")
        return markdown.split(Regex("\\n+---\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(markdown) }
    }

    private fun renderCurrentSlide() {
        val container = getSlideContainer() ?: return
        container.textContent = "Rendering..."
        val content = slides.getOrElse(currentIndex) { "" }
        scope.launch {
            val html = MarkdownRenderer.render(content)
            container.innerHTML = html
        }
    }

    private fun updateCounter() {
        val counter = document.getElementById("slide-counter") as? HTMLElement
        counter?.textContent = "${currentIndex + 1} / ${slides.size}"
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val prevBtn = document.getElementById("btn-slide-prev") as? HTMLElement
        val nextBtn = document.getElementById("btn-slide-next") as? HTMLElement
        prevBtn?.style?.opacity = if (currentIndex <= 0) "0.3" else "1"
        nextBtn?.style?.opacity = if (currentIndex >= slides.size - 1) "0.3" else "1"
    }

    private fun goToPrevious() {
        if (currentIndex <= 0) return
        currentIndex--
        renderCurrentSlide()
        updateCounter()
    }

    private fun goToNext() {
        if (currentIndex >= slides.size - 1) return
        currentIndex++
        renderCurrentSlide()
        updateCounter()
    }

    private fun bindNavigation() {
        val prevBtn = document.getElementById("btn-slide-prev") as? HTMLElement
        val nextBtn = document.getElementById("btn-slide-next") as? HTMLElement
        prevBtn?.onclick = { goToPrevious() }
        nextBtn?.onclick = { goToNext() }
    }

    private fun bindCloseButton() {
        val btn = document.getElementById("btn-slide-close") as? HTMLElement
        btn?.onclick = { close() }
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

    private fun getModal(): HTMLElement? =
        document.getElementById("slide-preview-modal") as? HTMLElement

    private fun getSlideContainer(): HTMLElement? =
        document.getElementById("slide-current") as? HTMLElement
}
