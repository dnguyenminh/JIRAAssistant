package com.assistant.document

import com.assistant.document.models.DocumentSection

/**
 * Parses AI Markdown response for FSD documents.
 *
 * Validates all 10 FSD sections are present, fills missing sections
 * with default "⚠️ Insufficient data" text, and extracts source citations.
 * Also provides serialize() for round-trip Markdown conversion.
 *
 * Requirements: 3.8, 3.9, 9.3, 9.5
 */
object FsdResponseParser {

    private const val INSUFFICIENT_DATA =
        "⚠️ Insufficient data — This section requires manual input. Available data from analysis: none"

    /**
     * Parse AI Markdown into exactly 10 DocumentSection objects.
     * Missing sections are filled with default text (Req 3.9).
     * Sections with only whitespace content are also filled (Bug fix).
     */
    fun parse(markdown: String): List<DocumentSection> {
        val parsed = parseMarkdownSections(markdown)
        return FsdPromptBuilder.FSD_SECTIONS.map { heading ->
            val found = findSectionCaseInsensitive(heading, parsed)
            if (found != null && found.content.isNotBlank()) {
                DocumentSection(heading, found.content, found.sourceRefs)
            } else {
                DocumentSection(heading, INSUFFICIENT_DATA)
            }
        }
    }

    /**
     * Serialize sections back to Markdown (Req 9.5 round-trip).
     */
    fun serialize(sections: List<DocumentSection>): String {
        return sections.joinToString("\n\n") { section ->
            "## ${section.heading}\n\n${section.content}"
        }
    }
}
