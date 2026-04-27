package com.assistant.document

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Property 7: BRD Markdown round-trip preserves section structure.
 * Property 8: FSD Markdown round-trip preserves section structure.
 *
 * Parse → serialize → verify all headings preserved in order.
 *
 * **Validates: Requirements 9.4, 9.5**
 */
class MarkdownRoundTripPropertyTest {

    private val brdCount = BrdPromptBuilder.BRD_SECTIONS.size
    private val fsdCount = FsdPromptBuilder.FSD_SECTIONS.size

    @Test
    fun `Property 7 - BRD parse then serialize preserves all headings in order`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.brdMarkdown(sectionCount = brdCount..brdCount)
        ) { markdown ->
            val sections = BrdResponseParser.parse(markdown)
            val serialized = BrdResponseParser.serialize(sections)
            val reparsed = BrdResponseParser.parse(serialized)

            assertEquals(brdCount, reparsed.size)
            assertEquals(
                BrdPromptBuilder.BRD_SECTIONS,
                reparsed.map { it.heading },
                "BRD round-trip headings mismatch"
            )
        }
    }

    @Test
    fun `Property 8 - FSD parse then serialize preserves all 10 headings in order`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.fsdMarkdown(sectionCount = fsdCount..fsdCount)
        ) { markdown ->
            val sections = FsdResponseParser.parse(markdown)
            val serialized = FsdResponseParser.serialize(sections)
            val reparsed = FsdResponseParser.parse(serialized)

            assertEquals(fsdCount, reparsed.size)
            assertEquals(
                FsdPromptBuilder.FSD_SECTIONS,
                reparsed.map { it.heading },
                "FSD round-trip headings mismatch"
            )
        }
    }
}
