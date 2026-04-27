package com.assistant.document

/**
 * Content extraction and formatting utilities for slide generation.
 * Extracts bullet points from Markdown content and formats slides.
 * Handles tables, PREQ-NNN lists, and sentence extraction.
 *
 * Requirements: 11.2, 11.3
 */

private const val MAX_BULLETS = 7
private const val WARNING_MARKER = "⚠️"
private val BULLET_PATTERN = Regex("""^[\s]*[-*+•]\s+(.+)""")
private val NUMBERED_PATTERN = Regex("""^[\s]*\d+[.)]\s+(.+)""")
private val PREQ_PATTERN = Regex("""PREQ-\d+[:\s]+(.+)""")
private val TABLE_ROW_PATTERN = Regex("""^\|(.+)\|$""")
private val TABLE_SEPARATOR = Regex("""^[\s|:-]+$""")

/**
 * Extract bullet points from section content, max 7.
 * Tries existing bullets first, then falls back to sentence extraction.
 */
internal fun extractBullets(content: String): List<String> {
    if (content.isBlank() || content.contains(WARNING_MARKER)) {
        return listOf("$WARNING_MARKER BRD chưa có đủ nội dung cho slide này. Vui lòng review và bổ sung BRD trước khi sinh slides.")
    }
    val bullets = extractExistingBullets(content)
    if (bullets.isNotEmpty()) return bullets.take(MAX_BULLETS)
    val tableBullets = extractTableRows(content)
    if (tableBullets.isNotEmpty()) return tableBullets.take(MAX_BULLETS)
    return extractSentences(content).take(MAX_BULLETS)
}

/**
 * Extract vision-specific bullets: business problem, solution, value.
 * Prioritizes meaningful sentences over raw bullets.
 */
internal fun extractVisionBullets(content: String): List<String> {
    if (content.isBlank() || content.contains(WARNING_MARKER)) {
        return listOf("$WARNING_MARKER BRD chưa có đủ nội dung cho slide này. Vui lòng review và bổ sung BRD trước khi sinh slides.")
    }
    val bullets = extractExistingBullets(content)
    if (bullets.isNotEmpty()) return bullets.take(MAX_BULLETS)
    return extractSentences(content).take(MAX_BULLETS)
}

/**
 * Extract requirement bullets: prioritize PREQ-NNN formatted items.
 * Falls back to regular bullet extraction if no PREQ items found.
 */
internal fun extractRequirementBullets(content: String): List<String> {
    if (content.isBlank() || content.contains(WARNING_MARKER)) {
        return listOf("$WARNING_MARKER BRD chưa có đủ nội dung cho slide này. Vui lòng review và bổ sung BRD trước khi sinh slides.")
    }
    val preqItems = extractPreqItems(content)
    if (preqItems.isNotEmpty()) return preqItems.take(MAX_BULLETS)
    return extractBullets(content)
}

/**
 * Extract scope bullets: look for In-Scope/Out-of-Scope patterns.
 * Falls back to regular bullet extraction.
 */
internal fun extractScopeBullets(content: String): List<String> {
    if (content.isBlank() || content.contains(WARNING_MARKER)) {
        return listOf("$WARNING_MARKER BRD chưa có đủ nội dung cho slide này. Vui lòng review và bổ sung BRD trước khi sinh slides.")
    }
    val tableBullets = extractTableRows(content)
    if (tableBullets.isNotEmpty()) return tableBullets.take(MAX_BULLETS)
    return extractBullets(content)
}

/**
 * Extract risk bullets: look for risk indicators and blocking issues.
 * Falls back to regular bullet extraction.
 */
internal fun extractRiskBullets(content: String): List<String> {
    if (content.isBlank() || content.contains(WARNING_MARKER)) {
        return listOf("$WARNING_MARKER BRD chưa có đủ nội dung cho slide này. Vui lòng review và bổ sung BRD trước khi sinh slides.")
    }
    val bullets = extractExistingBullets(content)
    if (bullets.isNotEmpty()) return bullets.take(MAX_BULLETS)
    return extractSentences(content).take(MAX_BULLETS)
}

/**
 * Extract data-flow specific bullets from Existing Processes.
 */
internal fun extractDataFlowBullets(content: String): List<String> {
    val bullets = extractBullets(content)
    if (bullets.size == 1 && bullets[0].contains(WARNING_MARKER)) return bullets
    return bullets.take(MAX_BULLETS)
}

/**
 * Extract PREQ-NNN requirement titles from content.
 */
private fun extractPreqItems(content: String): List<String> {
    return content.lines().mapNotNull { line ->
        PREQ_PATTERN.find(line)?.groupValues?.get(1)?.trim()
    }.filter { it.isNotBlank() }
}

/**
 * Extract table rows as bullet points (skip header separator).
 */
private fun extractTableRows(content: String): List<String> {
    return content.lines()
        .filter { TABLE_ROW_PATTERN.matches(it.trim()) }
        .filter { !TABLE_SEPARATOR.matches(it.trim()) }
        .drop(1) // skip header row
        .mapNotNull { line ->
            val cells = line.trim().removeSurrounding("|").split("|")
            val meaningful = cells.map { it.trim() }.filter { it.isNotBlank() }
            if (meaningful.isNotEmpty()) meaningful.joinToString(" — ") else null
        }
        .filter { it.isNotBlank() }
}

/**
 * Extract existing bullet/numbered list items from Markdown.
 */
internal fun extractExistingBullets(content: String): List<String> {
    return content.lines().mapNotNull { line ->
        BULLET_PATTERN.find(line)?.groupValues?.get(1)?.trim()
            ?: NUMBERED_PATTERN.find(line)?.groupValues?.get(1)?.trim()
    }.filter { it.isNotBlank() }
}

/**
 * Fall back to extracting meaningful sentences as bullet points.
 * Skips headings, empty lines, and short fragments.
 */
internal fun extractSentences(content: String): List<String> {
    val cleaned = content.lines()
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("|") }
        .joinToString(" ")
        .replace(Regex("""\[Source:[^\]]*]"""), "")
        .trim()
    if (cleaned.isBlank()) {
        return listOf("$WARNING_MARKER BRD chưa có đủ nội dung cho slide này. Vui lòng review và bổ sung BRD trước khi sinh slides.")
    }
    return cleaned.split(Regex("""[.!?]+\s+"""))
        .map { it.trim() }
        .filter { it.isNotBlank() && it.length > 5 }
        .map { if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else "$it." }
}

/**
 * Format a single slide with heading and bullet points.
 */
internal fun formatSlide(title: String, bullets: List<String>): String {
    val heading = "## $title"
    val body = bullets.joinToString("\n") { "- $it" }
    return "$heading\n\n$body"
}
