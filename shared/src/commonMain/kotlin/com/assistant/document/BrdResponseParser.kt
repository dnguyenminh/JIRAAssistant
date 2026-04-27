package com.assistant.document

import com.assistant.document.models.DocumentSection

/**
 * Parses AI Markdown response for BRD documents.
 *
 * Validates all 7 BRD sections (Carleton ITS template) are present,
 * fills missing sections with default "⚠️ Insufficient data" text,
 * and extracts source citations.
 * Also provides serialize() for round-trip Markdown conversion.
 *
 * Requirements: 2.7, 2.8, 9.3, 9.4
 */
object BrdResponseParser {

    private const val INSUFFICIENT_DATA =
        "⚠️ Insufficient data — This section requires manual input. Available data from analysis: none"

    private val SOURCE_PATTERN = Regex("""\[Source:\s*([^\]]+)]""")

    /**
     * Parse AI Markdown into exactly 7 DocumentSection objects.
     * Missing or blank sections are filled with default text (Req 2.8).
     */
    fun parse(markdown: String): List<DocumentSection> {
        val parsed = parseMarkdownSections(markdown)
        return BrdPromptBuilder.BRD_SECTIONS.map { heading ->
            val found = findSectionCaseInsensitive(heading, parsed)
            if (found != null && found.content.isNotBlank()) {
                DocumentSection(heading, found.content, found.sourceRefs)
            } else {
                DocumentSection(heading, INSUFFICIENT_DATA)
            }
        }
    }

    /**
     * Serialize sections back to Markdown (Req 9.4 round-trip).
     */
    fun serialize(sections: List<DocumentSection>): String {
        return sections.joinToString("\n\n") { section ->
            "## ${section.heading}\n\n${section.content}"
        }
    }
}
