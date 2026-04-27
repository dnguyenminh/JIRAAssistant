package com.assistant.document

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property 2: Preservation — Exact Match Heading Behavior
 *
 * These tests capture baseline behavior on UNFIXED code that must
 * be preserved after the fix is applied. All tests MUST PASS on
 * unfixed code.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.7**
 */
class HeadingParsingPreservationTest {

    private val insufficientData = "⚠️ Insufficient data"
    private val brdCount = BrdPromptBuilder.BRD_SECTIONS.size
    private val fsdCount = FsdPromptBuilder.FSD_SECTIONS.size

    /**
     * Property-based test 1: BRD exact-match headings parse correctly.
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun `Preservation - BRD exact headings produce correct sections`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 80),
                Arb.brdMarkdown()
            ) { markdown ->
                val sections = BrdResponseParser.parse(markdown)
                assertEquals(brdCount, sections.size)
                assertEquals(
                    BrdPromptBuilder.BRD_SECTIONS,
                    sections.map { it.heading }
                )
                val parsed = parseMarkdownSections(markdown)
                for (section in sections) {
                    val expected = findSectionCaseInsensitive(section.heading, parsed)
                    if (expected != null) {
                        assertEquals(
                            expected.content,
                            section.content,
                            "Content mismatch for '${section.heading}'"
                        )
                    } else {
                        assertTrue(
                            section.content.startsWith(insufficientData),
                            "Missing section should fallback"
                        )
                    }
                }
            }
        }

    /**
     * Property-based test 2: Missing BRD sections get INSUFFICIENT_DATA.
     * **Validates: Requirements 3.3**
     */
    @Test
    fun `Preservation - missing BRD sections get INSUFFICIENT_DATA`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 80),
                Arb.brdMarkdown()
            ) { markdown ->
                val sections = BrdResponseParser.parse(markdown)
                assertEquals(brdCount, sections.size)
                val parsed = parseMarkdownSections(markdown)
                for (section in sections) {
                    val found = findSectionCaseInsensitive(section.heading, parsed)
                    if (found == null) {
                        assertTrue(
                            section.content.startsWith(insufficientData),
                            "Missing '${section.heading}' should fallback"
                        )
                    }
                }
            }
        }

    /**
     * Property-based test 3: Parse → serialize → parse round-trip.
     * **Validates: Requirements 3.4**
     */
    @Test
    fun `Preservation - BRD round-trip preserves structure`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 80),
                Arb.brdMarkdown()
            ) { markdown ->
                val first = BrdResponseParser.parse(markdown)
                val serialized = BrdResponseParser.serialize(first)
                val second = BrdResponseParser.parse(serialized)
                assertEquals(first.size, second.size)
                for (i in first.indices) {
                    assertEquals(first[i].heading, second[i].heading)
                    assertEquals(first[i].content, second[i].content)
                }
            }
        }

    /**
     * Property-based test 4: FSD exact-match headings parse correctly.
     * **Validates: Requirements 3.1 analog for FSD**
     */
    @Test
    fun `Preservation - FSD exact headings produce correct sections`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 80),
                Arb.fsdMarkdown()
            ) { markdown ->
                val sections = FsdResponseParser.parse(markdown)
                assertEquals(fsdCount, sections.size)
                assertEquals(
                    FsdPromptBuilder.FSD_SECTIONS,
                    sections.map { it.heading }
                )
                val parsed = parseMarkdownSections(markdown)
                for (section in sections) {
                    val expected = parsed[section.heading]
                    if (expected != null) {
                        assertEquals(
                            expected.content,
                            section.content,
                            "FSD content mismatch for '${section.heading}'"
                        )
                    } else {
                        assertTrue(
                            section.content.startsWith(insufficientData),
                            "Missing FSD section should fallback"
                        )
                    }
                }
            }
        }

    /**
     * Property-based test 5: Source citations are extracted correctly.
     * **Validates: Requirements 3.7**
     */
    @Test
    fun `Preservation - source citations extracted correctly`() =
        runTest {
            val ticketIdArb = arbitrary {
                val prefix = Arb.string(2..5, Codepoint.alphanumeric()).bind()
                val num = Arb.int(1..9999).bind()
                "$prefix-$num"
            }
            checkAll(
                PropTestConfig(iterations = 80),
                Arb.list(ticketIdArb, 1..5)
            ) { ticketIds ->
                val content = ticketIds.joinToString(" ") { id ->
                    "Some text [Source: $id] more text."
                }
                val extracted = extractSourceCitations(content)
                assertEquals(
                    ticketIds.size,
                    extracted.size,
                    "Citation count mismatch"
                )
                for (id in ticketIds) {
                    assertTrue(
                        id in extracted,
                        "Expected citation '$id' not found"
                    )
                }
            }
        }
}
