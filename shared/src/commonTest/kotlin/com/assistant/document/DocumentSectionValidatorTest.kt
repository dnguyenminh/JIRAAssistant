package com.assistant.document

import com.assistant.document.models.DocumentSection
import kotlin.test.*

/**
 * Unit tests for DocumentSectionValidator.
 * Validates heading checks, citation detection, warning detection,
 * and full document validation.
 *
 * Requirements: 2.7, 2.8, 3.8, 3.9, 9.2, 9.3
 */
class DocumentSectionValidatorTest {

    private val brdSections = BrdPromptBuilder.BRD_SECTIONS
    private val brdCount = brdSections.size

    // --- validateHeadings ---

    @Test
    fun validateHeadingsAllMatchReturnsValid() {
        val sections = brdSections.map {
            DocumentSection(it, "Content")
        }
        val result = DocumentSectionValidator.validateHeadings(
            sections, brdSections
        )
        assertTrue(result.isValid)
        assertTrue(result.missingHeadings.isEmpty())
        assertTrue(result.extraHeadings.isEmpty())
    }

    @Test
    fun validateHeadingsMissingSectionsDetected() {
        val partial = listOf(
            DocumentSection("Revision History", "Content"),
            DocumentSection("Project Overview", "Content")
        )
        val result = DocumentSectionValidator.validateHeadings(
            partial, brdSections
        )
        assertFalse(result.isValid)
        assertEquals(brdCount - 2, result.missingHeadings.size)
        assertTrue(result.missingHeadings.contains("Project Requirements"))
    }

    @Test
    fun validateHeadingsExtraSectionsDetected() {
        val sections = brdSections.map {
            DocumentSection(it, "Content")
        } + DocumentSection("Bonus Section", "Extra")
        val result = DocumentSectionValidator.validateHeadings(
            sections, brdSections
        )
        assertFalse(result.isValid)
        assertTrue(result.extraHeadings.contains("Bonus Section"))
    }

    @Test
    fun validateHeadingsEmptySectionsListReportsMissing() {
        val result = DocumentSectionValidator.validateHeadings(
            emptyList(), brdSections
        )
        assertFalse(result.isValid)
        assertEquals(brdSections, result.missingHeadings)
    }

    @Test
    fun validateHeadingsWorksWithFsdSections() {
        val sections = FsdPromptBuilder.FSD_SECTIONS.map {
            DocumentSection(it, "Content")
        }
        val result = DocumentSectionValidator.validateHeadings(
            sections, FsdPromptBuilder.FSD_SECTIONS
        )
        assertTrue(result.isValid)
    }

    // --- hasValidCitations ---

    @Test
    fun hasValidCitationsReturnsTrueWhenPresent() {
        val section = DocumentSection(
            "Summary", "Details [Source: NET-100] here."
        )
        assertTrue(DocumentSectionValidator.hasValidCitations(section))
    }

    @Test
    fun hasValidCitationsReturnsFalseWhenAbsent() {
        val section = DocumentSection("Summary", "No citations here.")
        assertFalse(DocumentSectionValidator.hasValidCitations(section))
    }

    @Test
    fun hasValidCitationsHandlesMultipleCitations() {
        val section = DocumentSection(
            "Summary",
            "[Source: NET-1] and [Source: spec.pdf] refs."
        )
        assertTrue(DocumentSectionValidator.hasValidCitations(section))
    }

    // --- hasInsufficientDataWarning ---

    @Test
    fun hasInsufficientDataWarningDetectsEmoji() {
        val section = DocumentSection(
            "Summary",
            "⚠️ Insufficient data — This section requires manual input."
        )
        assertTrue(
            DocumentSectionValidator.hasInsufficientDataWarning(section)
        )
    }

    @Test
    fun hasInsufficientDataWarningReturnsFalseForNormal() {
        val section = DocumentSection("Summary", "Normal content.")
        assertFalse(
            DocumentSectionValidator.hasInsufficientDataWarning(section)
        )
    }

    // --- validateDocument ---

    @Test
    fun validateDocumentFullBrdWithCitationsAndNoWarnings() {
        val sections = brdSections.map {
            DocumentSection(it, "Content [Source: NET-1]")
        }
        val result = DocumentSectionValidator.validateDocument(
            sections, brdSections
        )
        assertTrue(result.headingsValid)
        assertEquals(brdCount, result.sectionsWithCitations)
        assertEquals(0, result.sectionsWithWarnings)
        assertTrue(result.missingHeadings.isEmpty())
    }

    @Test
    fun validateDocumentPartialWithWarnings() {
        val sections = listOf(
            DocumentSection("Revision History", "[Source: X] ok"),
            DocumentSection(
                "Project Overview",
                "⚠️ Insufficient data — requires manual input."
            )
        )
        val result = DocumentSectionValidator.validateDocument(
            sections, brdSections
        )
        assertFalse(result.headingsValid)
        assertEquals(1, result.sectionsWithCitations)
        assertEquals(1, result.sectionsWithWarnings)
        assertEquals(brdCount - 2, result.missingHeadings.size)
    }

    @Test
    fun validateDocumentFsdAllDefaultSections() {
        val sections = FsdPromptBuilder.FSD_SECTIONS.map {
            DocumentSection(
                it,
                "⚠️ Insufficient data — requires manual input."
            )
        }
        val result = DocumentSectionValidator.validateDocument(
            sections, FsdPromptBuilder.FSD_SECTIONS
        )
        assertTrue(result.headingsValid)
        assertEquals(0, result.sectionsWithCitations)
        assertEquals(FsdPromptBuilder.FSD_SECTIONS.size, result.sectionsWithWarnings)
    }
}
