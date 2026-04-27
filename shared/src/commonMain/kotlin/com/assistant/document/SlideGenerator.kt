package com.assistant.document

import com.assistant.document.models.DocumentSection

/**
 * Generates Requirements Summary Slides from BRD Markdown content.
 *
 * Parses BRD → extracts section contents → generates 7 slides
 * (Vision, Requirements, Data Flow, Scope, Stakeholders, Risks, Timeline)
 * with `---` separators, max 7 bullet points per slide.
 * No new content — condensed from BRD only.
 *
 * Requirements: 11.1, 11.2, 11.3
 */
object SlideGenerator {

    private const val SLIDE_SEPARATOR = "\n\n---\n\n"
    private const val MAX_BULLETS = 7
    private const val WARNING_MARKER = "⚠️"

    /**
     * Generate slide-format Markdown from BRD Markdown string.
     * @throws IllegalArgumentException if brdMarkdown is empty/blank
     */
    fun generate(brdMarkdown: String): String {
        require(brdMarkdown.isNotBlank()) { "BRD markdown must not be empty or blank" }
        val sections = BrdResponseParser.parse(brdMarkdown)
        val sectionMap = sections.associateBy { it.heading }
        return buildSlides(sectionMap)
    }
}
