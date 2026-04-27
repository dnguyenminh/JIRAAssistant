package com.assistant.document

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 1: Bug Condition — Heading Variation Parsing Failure
 *
 * These tests construct AI markdown with heading variations
 * (numbered, lowercase, H1, bold) and verify the parser can
 * match sections. On UNFIXED code these tests are EXPECTED TO
 * FAIL, proving the bug exists.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 1.4**
 */
class HeadingVariationBugConditionTest {

    private val insufficientData =
        "⚠️ Insufficient data"

    @Test
    fun `Bug Condition - BRD numbered headings are matched`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 50),
                Arb.brdMarkdownWithNumberedHeadings()
            ) { markdown ->
                val sections = BrdResponseParser.parse(markdown)
                val matched = sections.count {
                    !it.content.startsWith(insufficientData)
                }
                assertTrue(
                    matched > 0,
                    "Numbered headings: expected >0 matched sections, got 0"
                )
            }
        }

    @Test
    fun `Bug Condition - BRD lowercase headings are matched`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 50),
                Arb.brdMarkdownWithLowercaseHeadings()
            ) { markdown ->
                val sections = BrdResponseParser.parse(markdown)
                val matched = sections.count {
                    !it.content.startsWith(insufficientData)
                }
                assertTrue(
                    matched > 0,
                    "Lowercase headings: expected >0 matched, got 0"
                )
            }
        }

    @Test
    fun `Bug Condition - BRD H1 headings are matched`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 50),
                Arb.brdMarkdownWithH1Headings()
            ) { markdown ->
                val sections = BrdResponseParser.parse(markdown)
                val matched = sections.count {
                    !it.content.startsWith(insufficientData)
                }
                assertTrue(
                    matched > 0,
                    "H1 headings: expected >0 matched sections, got 0"
                )
            }
        }

    @Test
    fun `Bug Condition - BRD bold headings are matched`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 50),
                Arb.brdMarkdownWithBoldHeadings()
            ) { markdown ->
                val sections = BrdResponseParser.parse(markdown)
                val matched = sections.count {
                    !it.content.startsWith(insufficientData)
                }
                assertTrue(
                    matched > 0,
                    "Bold headings: expected >0 matched sections, got 0"
                )
            }
        }

    @Test
    fun `Bug Condition - FSD numbered headings are matched`() =
        runTest {
            checkAll(
                PropTestConfig(iterations = 50),
                Arb.fsdMarkdownWithNumberedHeadings()
            ) { markdown ->
                val sections = FsdResponseParser.parse(markdown)
                val matched = sections.count {
                    !it.content.startsWith(insufficientData)
                }
                assertTrue(
                    matched > 0,
                    "FSD numbered headings: expected >0 matched, got 0"
                )
            }
        }
}
