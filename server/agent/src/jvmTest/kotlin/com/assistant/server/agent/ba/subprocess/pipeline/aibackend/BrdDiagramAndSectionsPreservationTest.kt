package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.document.BrdPromptBuilder
import com.assistant.server.agent.ba.subprocess.DocumentQualityChecker
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Preservation property tests for brd-diagram-and-sections-fix.
 *
 * These tests capture baseline behavior that MUST be preserved
 * after the fix. They should PASS on both unfixed and fixed code.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
 */
class BrdDiagramAndSectionsPreservationTest {

    // ── Preservation A: BRD Parsing ─────────────────────

    @Test
    fun `Preservation A - parser returns all 7 sections`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                arbCompleteBrdMarkdown()
            ) { markdown ->
                val sections =
                    com.assistant.document.BrdResponseParser.parse(markdown)
                assertEquals(
                    BrdPromptBuilder.BRD_SECTIONS.size,
                    sections.size,
                    "Parser should return all 7 sections"
                )
                BrdPromptBuilder.BRD_SECTIONS.forEachIndexed { i, heading ->
                    assertEquals(
                        heading, sections[i].heading,
                        "Section $i heading should be '$heading'"
                    )
                }
            }
        }
    }

    // ── Preservation B: Quality Checker ─────────────────

    @Test
    fun `Preservation B - quality checker detects markers`() {
        val brd = BrdPromptBuilder.BRD_SECTIONS.joinToString("\n\n") {
            if (it == "Project Requirements") {
                "## $it\n\n⚠️ Insufficient data — manual input."
            } else {
                "## $it\n\n${"Real content. ".repeat(10)}"
            }
        }
        val result = DocumentQualityChecker.check(brd, "BRD")
        assertFalse(
            result.passed,
            "Quality check should fail with Insufficient data"
        )
    }

    // ── Preservation C: Prompt Structure ────────────────

    @Test
    fun `Preservation C - appendBrdSections has all headings`() {
        val output = buildString { appendBrdSections("BRD") }
        BrdPromptBuilder.BRD_SECTIONS.forEach { heading ->
            assertTrue(
                output.contains(heading),
                "appendBrdSections should contain: $heading"
            )
        }
        assertTrue(
            output.contains("AS-IS"),
            "Should contain AS-IS guidance"
        )
        assertTrue(
            output.contains("TO-BE"),
            "Should contain TO-BE guidance"
        )
    }

    // ── Generator ───────────────────────────────────────

    private fun arbCompleteBrdMarkdown(): Arb<String> = arbitrary {
        val bodies = Arb.list(
            Arb.string(50..200, Codepoint.alphanumeric()),
            BrdPromptBuilder.BRD_SECTIONS.size..BrdPromptBuilder.BRD_SECTIONS.size
        ).bind()
        BrdPromptBuilder.BRD_SECTIONS.mapIndexed { i, heading ->
            "## $heading\n\n${bodies[i]}"
        }.joinToString("\n\n")
    }
}
