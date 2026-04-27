package com.assistant.document

import com.assistant.document.models.DocumentSection

/**
 * Validates document sections against templates.
 *
 * Checks section headings match expected template, verifies source citation
 * format [Source: ...], and detects insufficient data warnings (⚠️).
 * Read-only — never modifies sections.
 *
 * Requirements: 2.7, 2.8, 3.8, 3.9, 9.2, 9.3
 */
object DocumentSectionValidator {

    private val SOURCE_CITATION_PATTERN = Regex("""\[Source:\s*[^\]]+]""")
    private const val WARNING_MARKER = "⚠️"

    /**
     * Validates section headings match the expected template headings.
     */
    fun validateHeadings(
        sections: List<DocumentSection>,
        expectedHeadings: List<String>
    ): ValidationResult {
        val actualHeadings = sections.map { it.heading }
        val missing = expectedHeadings.filter { it !in actualHeadings }
        val extra = actualHeadings.filter { it !in expectedHeadings }
        return ValidationResult(
            isValid = missing.isEmpty() && extra.isEmpty(),
            missingHeadings = missing,
            extraHeadings = extra
        )
    }

    /**
     * Checks whether section content contains [Source: ...] citations.
     */
    fun hasValidCitations(section: DocumentSection): Boolean {
        return SOURCE_CITATION_PATTERN.containsMatchIn(section.content)
    }

    /**
     * Detects ⚠️ insufficient data warning in section content.
     */
    fun hasInsufficientDataWarning(section: DocumentSection): Boolean {
        return section.content.contains(WARNING_MARKER)
    }

    /**
     * Full document validation: headings, citations, and warnings.
     */
    fun validateDocument(
        sections: List<DocumentSection>,
        expectedHeadings: List<String>
    ): DocumentValidation {
        val headingResult = validateHeadings(sections, expectedHeadings)
        val citationCount = sections.count { hasValidCitations(it) }
        val warningCount = sections.count { hasInsufficientDataWarning(it) }
        return DocumentValidation(
            headingsValid = headingResult.isValid,
            sectionsWithCitations = citationCount,
            sectionsWithWarnings = warningCount,
            missingHeadings = headingResult.missingHeadings
        )
    }
}

/**
 * Result of heading validation against a template.
 */
data class ValidationResult(
    val isValid: Boolean,
    val missingHeadings: List<String>,
    val extraHeadings: List<String>
)

/**
 * Full document validation summary.
 */
data class DocumentValidation(
    val headingsValid: Boolean,
    val sectionsWithCitations: Int,
    val sectionsWithWarnings: Int,
    val missingHeadings: List<String>
)
