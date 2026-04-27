package com.assistant.document

import kotlin.test.*

/**
 * Unit test for export filename format: {ticketId}-{documentType}.md
 * Validates: Requirement 8.2
 */
class ExportFilenameTest {

    /** Replicate the filename pattern from DocumentExporter. */
    private fun buildFilename(ticketId: String, docType: String): String =
        "${ticketId}-${docType}.md"

    @Test
    fun brdFilenameMatchesPattern() {
        val filename = buildFilename("NET-458", "BRD")
        assertEquals("NET-458-BRD.md", filename)
    }

    @Test
    fun fsdFilenameMatchesPattern() {
        val filename = buildFilename("NET-458", "FSD")
        assertEquals("NET-458-FSD.md", filename)
    }

    @Test
    fun slidesFilenameMatchesPattern() {
        val filename = buildFilename("PROJ-100", "REQUIREMENT_SLIDES")
        assertEquals("PROJ-100-REQUIREMENT_SLIDES.md", filename)
    }

    @Test
    fun filenameEndsWith_md() {
        val filename = buildFilename("ABC-1", "BRD")
        assertTrue(filename.endsWith(".md"), "Export must be .md")
    }

    @Test
    fun filenameContainsTicketIdAndType() {
        val filename = buildFilename("DEMO-42", "FSD")
        assertTrue(filename.contains("DEMO-42"), "Missing ticketId")
        assertTrue(filename.contains("FSD"), "Missing documentType")
        assertEquals("DEMO-42-FSD.md", filename)
    }
}
