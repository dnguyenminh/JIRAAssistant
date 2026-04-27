package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.ApiSpecification
import com.assistant.ai.deepanalysis.models.DatabaseChange
import com.assistant.ai.deepanalysis.models.ExternalIntegration

/**
 * Low-level helper functions for section extraction.
 * Requirements: 17.1-17.5
 */

/** Extract text between a heading match and the next heading. */
internal fun extractSectionUnderHeading(
    description: String,
    headingPattern: Regex
): String {
    val match = headingPattern.find(description) ?: return ""
    val startIdx = match.range.last + 1
    if (startIdx >= description.length) return ""
    val remaining = description.substring(startIdx)
    val nextHeading = Regex("""^#+\s""", RegexOption.MULTILINE)
    val nextMatch = nextHeading.find(remaining)
    val sectionText = if (nextMatch != null) {
        remaining.substring(0, nextMatch.range.first)
    } else {
        remaining
    }
    return sectionText.trim()
}

/** Parse HTTP method lines into ApiSpecification list. */
internal fun addApisFromText(
    text: String,
    apis: MutableList<ApiSpecification>
) {
    SectionPatterns.HTTP_METHOD_LINE.findAll(text).forEach { match ->
        val method = match.groupValues[1].uppercase()
        val path = match.groupValues[2]
        val desc = extractLineContext(text, match.range)
        apis.add(ApiSpecification(method, path, desc))
    }
}

/** Parse SQL statements into DatabaseChange list. */
internal fun addDbChangesFromText(
    text: String,
    changes: MutableList<DatabaseChange>
) {
    SectionPatterns.SQL_STATEMENT.findAll(text).forEach { match ->
        val operation = match.groupValues[1].uppercase()
        val tableName = match.groupValues[2]
        val columns = extractColumnsNear(text, match.range)
        changes.add(DatabaseChange(tableName, operation, columns))
    }
}

/** Extract column definitions near a SQL statement. */
internal fun extractColumnsNear(
    text: String,
    sqlRange: IntRange
): List<String> {
    val searchEnd = minOf(text.length, sqlRange.last + 500)
    val nearby = text.substring(sqlRange.last, searchEnd)
    return SectionPatterns.COLUMN_DEF.findAll(nearby)
        .map { "${it.groupValues[1]} ${it.groupValues[2]}" }
        .toList()
        .take(20)
}

/** Parse protocol URLs into ExternalIntegration list. */
internal fun addExternalDepsFromText(
    text: String,
    deps: MutableList<ExternalIntegration>
) {
    SectionPatterns.PROTOCOL_PATTERN.findAll(text).forEach { match ->
        val protocol = match.groupValues[1]
        val endpoint = match.value
        val serviceName = extractServiceName(match.groupValues[2])
        deps.add(ExternalIntegration(serviceName, protocol, endpoint))
    }
}

/** Derive service name from URL host. */
internal fun extractServiceName(urlPart: String): String {
    return urlPart.split("/").firstOrNull()
        ?.split(":")?.firstOrNull()
        ?: urlPart
}

/** Extract acceptance criteria items from a section. */
internal fun addCriteriaFromSection(
    sectionText: String,
    criteria: MutableList<String>
) {
    val lines = sectionText.lines()
    lines.filter { it.trim().startsWith("-") || it.trim().startsWith("*") || it.trim().startsWith("•") }
        .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
        .filter { it.isNotBlank() }
        .forEach { criteria.add(it) }
    if (criteria.isEmpty()) {
        addCriteriaFromPatterns(sectionText, criteria)
    }
}

/** Extract acceptance criteria from GIVEN/WHEN/THEN patterns. */
internal fun addCriteriaFromPatterns(
    text: String,
    criteria: MutableList<String>
) {
    SectionPatterns.AC_ITEM.findAll(text).forEach { match ->
        val item = match.value.trim()
            .removePrefix("-").removePrefix("*").removePrefix("•").trim()
        if (item.isNotBlank()) criteria.add(item)
    }
}

/** Get surrounding line context for a match. */
internal fun extractLineContext(
    text: String,
    matchRange: IntRange
): String {
    val lineStart = text.lastIndexOf('\n', matchRange.first) + 1
    val lineEnd = text.indexOf('\n', matchRange.last)
        .let { if (it == -1) text.length else it }
    return text.substring(lineStart, lineEnd).trim()
}
