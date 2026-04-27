package com.assistant.document

import com.assistant.document.models.DocumentSection
import kotlin.test.*

/**
 * Unit tests for BrdResponseParser.
 * Validates parsing produces exactly 7 sections (Carleton ITS template),
 * fills missing sections, extracts source citations, and round-trip works.
 *
 * Requirements: 2.7, 2.8, 9.2, 9.3, 9.4
 */
class BrdResponseParserTest {

    private val sectionCount = BrdPromptBuilder.BRD_SECTIONS.size

    @Test
    fun parseEmptyStringReturnsAllDefaultSections() {
        val sections = BrdResponseParser.parse("")
        assertEquals(sectionCount, sections.size)
        sections.forEach { section ->
            assertTrue(section.content.contains("⚠️ Insufficient data"), "Missing default: ${section.heading}")
        }
    }

    @Test
    fun parseFullMarkdownReturnsAllSections() {
        val markdown = BrdPromptBuilder.BRD_SECTIONS.joinToString("\n\n") { heading ->
            "## $heading\n\nContent for $heading. [Source: NET-100]"
        }
        val sections = BrdResponseParser.parse(markdown)
        assertEquals(sectionCount, sections.size)
        sections.forEachIndexed { i, section ->
            assertEquals(BrdPromptBuilder.BRD_SECTIONS[i], section.heading)
            assertTrue(section.content.contains("Content for"), "Missing content: ${section.heading}")
        }
    }

    @Test
    fun parsePartialMarkdownFillsMissingSections() {
        val markdown = """
            ## Project Overview
            
            Overview content. [Source: NET-100]
            
            ## Project Requirements
            
            Requirement details.
        """.trimIndent()
        val sections = BrdResponseParser.parse(markdown)
        assertEquals(sectionCount, sections.size)
        val overview = sections.first { it.heading == "Project Overview" }
        assertTrue(overview.content.contains("Overview content"))
        // Missing sections should have default
        val signOff = sections.first { it.heading == "Sign Off" }
        assertTrue(signOff.content.contains("⚠️ Insufficient data"))
    }

    @Test
    fun extractsSourceCitations() {
        val markdown = """
            ## Project Overview
            
            Summary [Source: NET-100] and [Source: spec.pdf] references.
            
            ## Project Requirements
            
            Description.
        """.trimIndent()
        val sections = BrdResponseParser.parse(markdown)
        val overview = sections.first { it.heading == "Project Overview" }
        assertEquals(listOf("NET-100", "spec.pdf"), overview.sourceRefs)
    }

    @Test
    fun serializeProducesValidMarkdown() {
        val sections = BrdPromptBuilder.BRD_SECTIONS.map { heading ->
            DocumentSection(heading, "Content for $heading")
        }
        val markdown = BrdResponseParser.serialize(sections)
        BrdPromptBuilder.BRD_SECTIONS.forEach { heading ->
            assertTrue(markdown.contains("## $heading"), "Missing heading in serialized: $heading")
        }
    }

    @Test
    fun roundTripPreservesAllHeadings() {
        val original = BrdPromptBuilder.BRD_SECTIONS.joinToString("\n\n") { heading ->
            "## $heading\n\nContent for $heading."
        }
        val parsed = BrdResponseParser.parse(original)
        val serialized = BrdResponseParser.serialize(parsed)
        val reparsed = BrdResponseParser.parse(serialized)

        assertEquals(sectionCount, reparsed.size)
        reparsed.forEachIndexed { i, section ->
            assertEquals(BrdPromptBuilder.BRD_SECTIONS[i], section.heading)
        }
    }

    @Test
    fun sectionOrderMatchesBrdTemplate() {
        val sections = BrdResponseParser.parse("")
        val headings = sections.map { it.heading }
        assertEquals(BrdPromptBuilder.BRD_SECTIONS, headings)
    }

    @Test
    fun parseMalformedMarkdownDoesNotCrash() {
        val malformed = "# Wrong heading level\nSome text\n##No space\n## \n##"
        val sections = BrdResponseParser.parse(malformed)
        assertEquals(sectionCount, sections.size)
    }
}
