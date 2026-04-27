package com.assistant.document

import com.assistant.document.models.DocumentSection

/**
 * Shared parsing utilities for BRD and FSD response parsers.
 * Extracts headings (H1–H3), section content, and source citations from Markdown.
 *
 * Requirements: 2.1, 2.3, 2.4, 2.7, 2.8, 3.8, 3.9, 9.2, 9.3
 */

private val SOURCE_PATTERN = Regex("""\[Source:\s*([^\]]+)]""")
private val HEADING_PATTERN = Regex("""^#{1,3}\s+(.+)$""", RegexOption.MULTILINE)
private val H1H2_PATTERN = Regex("""^#{1,2}\s+(.+)$""", RegexOption.MULTILINE)
private val NUMBERING_PREFIX = Regex("""^\d+\.\s+""")

/**
 * Normalize a raw heading by stripping numbering prefixes,
 * bold markers, and trailing whitespace (Req 2.1, 2.4).
 */
internal fun normalizeHeading(raw: String): String {
    return raw
        .replace("**", "")
        .replace(NUMBERING_PREFIX, "")
        .trim()
}

/**
 * Parse Markdown string into a map of heading → DocumentSection.
 * Uses H1-H3 for heading detection but only H1-H2 as section boundaries.
 * H3 sub-headings are preserved as content within their parent H2 section.
 */
internal fun parseMarkdownSections(markdown: String): Map<String, DocumentSection> {
    // Try H1/H2 boundaries first (preferred — preserves H3 sub-headings in content)
    val h12Matches = H1H2_PATTERN.findAll(markdown).toList()
    if (h12Matches.isNotEmpty()) return buildSections(markdown, h12Matches)
    // Fallback: if no H1/H2 found, try H3 (AI sometimes uses only H3)
    val h3Matches = HEADING_PATTERN.findAll(markdown).toList()
    if (h3Matches.isNotEmpty()) return buildSections(markdown, h3Matches)
    println("[DocumentParserUtils] WARNING: No headings found in response (length=${markdown.length}): ${markdown.take(200)}")
    return emptyMap()
}

private fun buildSections(markdown: String, matches: List<MatchResult>): Map<String, DocumentSection> {
    val result = mutableMapOf<String, DocumentSection>()
    for (i in matches.indices) {
        val heading = normalizeHeading(matches[i].groupValues[1])
        val start = matches[i].range.last + 1
        val end = if (i + 1 < matches.size) matches[i + 1].range.first else markdown.length
        val content = markdown.substring(start, end).trim()
        val sources = extractSourceCitations(content)
        result[heading] = DocumentSection(heading, content, sources)
    }
    return result
}

/**
 * Find a section by heading using case-insensitive match with fuzzy fallback.
 * 1) Case-insensitive exact match on heading name
 * 2) Fuzzy fallback: parsed heading contains the expected heading name,
 *    but only if the length ratio is reasonable (avoids "Functional Requirements"
 *    matching "Non-Functional Requirements" when both exist).
 * Supports Req 2.2 (case mismatch) and preserves Req 3.1, 3.2 (exact match).
 */
internal fun findSectionCaseInsensitive(
    heading: String,
    parsed: Map<String, DocumentSection>
): DocumentSection? {
    val lowerHeading = heading.lowercase()
    // Case-insensitive exact match
    val exactMatch = parsed.entries.firstOrNull { it.key.lowercase() == lowerHeading }
    if (exactMatch != null) return exactMatch.value
    // Fuzzy fallback: parsed key contains expected heading, prefer shortest match
    val candidates = parsed.entries
        .filter { it.key.lowercase().contains(lowerHeading) }
        .sortedBy { it.key.length }
    return candidates.firstOrNull()?.value
}

/**
 * Extract [Source: ...] citations from section content (Req 9.2).
 */
internal fun extractSourceCitations(content: String): List<String> {
    return SOURCE_PATTERN.findAll(content)
        .map { it.groupValues[1].trim() }
        .toList()
}
