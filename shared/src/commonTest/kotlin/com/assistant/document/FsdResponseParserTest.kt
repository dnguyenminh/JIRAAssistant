package com.assistant.document

import com.assistant.document.models.DocumentSection
import kotlin.test.*

/**
 * Unit tests for FsdResponseParser.
 * Validates parsing produces exactly 10 sections, fills missing sections,
 * extracts source citations, and round-trip serialize/parse works.
 *
 * Requirements: 3.8, 3.9, 9.2, 9.3, 9.5
 */
class FsdResponseParserTest {

    @Test
    fun parseEmptyStringReturnsAllDefaultSections() {
        val sections = FsdResponseParser.parse("")
        assertEquals(FsdPromptBuilder.FSD_SECTIONS.size, sections.size)
        sections.forEach { section ->
            assertTrue(section.content.contains("⚠️ Insufficient data"), "Missing default: ${section.heading}")
        }
    }

    @Test
    fun parseFullMarkdownReturnsAllSections() {
        val markdown = FsdPromptBuilder.FSD_SECTIONS.joinToString("\n\n") { heading ->
            "## $heading\n\nContent for $heading. [Source: NET-200]"
        }
        val sections = FsdResponseParser.parse(markdown)
        assertEquals(FsdPromptBuilder.FSD_SECTIONS.size, sections.size)
        sections.forEachIndexed { i, section ->
            assertEquals(FsdPromptBuilder.FSD_SECTIONS[i], section.heading)
            assertTrue(section.content.contains("Content for"), "Missing content: ${section.heading}")
        }
    }

    @Test
    fun parsePartialMarkdownFillsMissingSections() {
        val markdown = """
            ## Introduction
            
            System overview. [Source: NET-200]
            
            ## Integration Requirements
            
            API details here.
        """.trimIndent()
        val sections = FsdResponseParser.parse(markdown)
        assertEquals(FsdPromptBuilder.FSD_SECTIONS.size, sections.size)
        assertEquals("Introduction", sections[0].heading)
        assertTrue(sections[0].content.contains("System overview"))
        // Missing sections should have default
        val funcReqs = sections.first { it.heading == "Functional Specifications" }
        assertTrue(funcReqs.content.contains("⚠️ Insufficient data"))
    }

    @Test
    fun extractsSourceCitations() {
        val markdown = """
            ## Introduction
            
            Overview [Source: NET-200] and [Source: design.pdf] references.
            
            ## Functional Specifications
            
            Requirements content.
        """.trimIndent()
        val sections = FsdResponseParser.parse(markdown)
        val intro = sections.first { it.heading == "Introduction" }
        assertEquals(listOf("NET-200", "design.pdf"), intro.sourceRefs)
    }

    @Test
    fun serializeProducesValidMarkdown() {
        val sections = FsdPromptBuilder.FSD_SECTIONS.map { heading ->
            DocumentSection(heading, "Content for $heading")
        }
        val markdown = FsdResponseParser.serialize(sections)
        FsdPromptBuilder.FSD_SECTIONS.forEach { heading ->
            assertTrue(markdown.contains("## $heading"), "Missing heading in serialized: $heading")
        }
    }

    @Test
    fun roundTripPreservesAllHeadings() {
        val original = FsdPromptBuilder.FSD_SECTIONS.joinToString("\n\n") { heading ->
            "## $heading\n\nContent for $heading."
        }
        val parsed = FsdResponseParser.parse(original)
        val serialized = FsdResponseParser.serialize(parsed)
        val reparsed = FsdResponseParser.parse(serialized)

        assertEquals(FsdPromptBuilder.FSD_SECTIONS.size, reparsed.size)
        reparsed.forEachIndexed { i, section ->
            assertEquals(FsdPromptBuilder.FSD_SECTIONS[i], section.heading)
        }
    }

    @Test
    fun sectionOrderMatchesFsdTemplate() {
        val sections = FsdResponseParser.parse("")
        val headings = sections.map { it.heading }
        assertEquals(FsdPromptBuilder.FSD_SECTIONS, headings)
    }

    @Test
    fun parseMalformedMarkdownDoesNotCrash() {
        val malformed = "# Wrong heading level\nSome text\n##No space\n## \n##"
        val sections = FsdResponseParser.parse(malformed)
        assertEquals(FsdPromptBuilder.FSD_SECTIONS.size, sections.size)
    }
}
