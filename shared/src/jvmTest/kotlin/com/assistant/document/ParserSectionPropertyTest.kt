package com.assistant.document

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 5: BRD parser always produces exactly 7 sections (Carleton ITS).
 * Property 6: FSD parser always produces exactly 10 sections.
 *
 * For any Markdown string (including empty, partial, malformed),
 * parsers always output the correct number of sections.
 *
 * **Validates: Requirements 2.7, 2.8, 3.8, 3.9, 9.3**
 */
class ParserSectionPropertyTest {

    private val brdCount = BrdPromptBuilder.BRD_SECTIONS.size
    private val fsdCount = FsdPromptBuilder.FSD_SECTIONS.size
    private val insufficientMarker = "⚠️"

    @Test
    fun `Property 5 - BRD parser always returns exactly 7 sections`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.brdMarkdown()
        ) { markdown ->
            val sections = BrdResponseParser.parse(markdown)
            assertEquals(brdCount, sections.size, "Expected $brdCount BRD sections, got ${sections.size}")
            assertBrdHeadingsMatch(sections.map { it.heading })
        }
    }

    @Test
    fun `Property 5b - BRD parser fills missing sections with default`() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { randomText ->
            val sections = BrdResponseParser.parse(randomText)
            assertEquals(brdCount, sections.size)
            sections.forEach { section ->
                assertTrue(section.content.isNotBlank(), "Section '${section.heading}' has blank content")
            }
        }
    }

    @Test
    fun `Property 6 - FSD parser always returns exactly 10 sections`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.fsdMarkdown()
        ) { markdown ->
            val sections = FsdResponseParser.parse(markdown)
            assertEquals(fsdCount, sections.size, "Expected $fsdCount FSD sections, got ${sections.size}")
            assertFsdHeadingsMatch(sections.map { it.heading })
        }
    }

    @Test
    fun `Property 6b - FSD parser fills missing sections with default`() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.string(0..200)) { randomText ->
            val sections = FsdResponseParser.parse(randomText)
            assertEquals(fsdCount, sections.size)
            sections.forEach { section ->
                assertTrue(section.content.isNotBlank(), "Section '${section.heading}' has blank content")
            }
        }
    }

    private fun assertBrdHeadingsMatch(actual: List<String>) {
        assertEquals(
            BrdPromptBuilder.BRD_SECTIONS,
            actual,
            "BRD headings mismatch"
        )
    }

    private fun assertFsdHeadingsMatch(actual: List<String>) {
        assertEquals(
            FsdPromptBuilder.FSD_SECTIONS,
            actual,
            "FSD headings mismatch"
        )
    }
}
