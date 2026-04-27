package com.assistant.document

import kotlin.test.*

/**
 * Unit tests for SlideGenerator.
 * Validates slide generation from BRD Markdown: 7 slides,
 * `---` separators, max 7 bullets per slide, empty/blank rejection.
 *
 * Requirements: 11.1, 11.2, 11.3
 */
class SlideGeneratorTest {

    private fun buildBrdMarkdown(vararg entries: Pair<String, String>): String {
        return entries.joinToString("\n\n") { (heading, content) ->
            "## $heading\n\n$content"
        }
    }

    private fun fullBrdMarkdown(): String {
        return buildBrdMarkdown(
            "Revision History" to "Version 1.0 created. [Source: NET-100]",
            "Project Overview" to "- Driver 1\n- Driver 2\n- Project overview for NET-100.",
            "Common Project Acronyms, Names, and Descriptions" to "- API: Application Programming Interface",
            "Existing Processes" to "- In scope: Feature A\n- Out scope: Feature B\n- Data flows from A to B",
            "Project Requirements" to "- Req 1\n- Req 2\n- Req 3\n- Req 4\n- Req 5",
            "Sign Off" to "- PM Team\n- Dev Team\n- QA Team",
            "Appendix" to "- Reference 1\n- Reference 2"
        )
    }

    @Test
    fun rejectsEmptyMarkdown() {
        assertFailsWith<IllegalArgumentException> {
            SlideGenerator.generate("")
        }
    }

    @Test
    fun rejectsBlankMarkdown() {
        assertFailsWith<IllegalArgumentException> {
            SlideGenerator.generate("   \n  \t  ")
        }
    }

    @Test
    fun generates7SlidesFromFullBrd() {
        val result = SlideGenerator.generate(fullBrdMarkdown())
        val slides = result.split("\n\n---\n\n")
        assertEquals(7, slides.size, "Expected 7 slides, got ${slides.size}")
    }

    @Test
    fun slidesSeparatedByTripleDash() {
        val result = SlideGenerator.generate(fullBrdMarkdown())
        assertTrue(result.contains("---"), "Missing --- separator")
        val separatorCount = Regex("""\n\n---\n\n""").findAll(result).count()
        assertEquals(6, separatorCount, "Expected 6 separators between 7 slides")
    }

    @Test
    fun eachSlideHasH2Heading() {
        val result = SlideGenerator.generate(fullBrdMarkdown())
        val slides = result.split("\n\n---\n\n")
        slides.forEach { slide ->
            assertTrue(slide.trimStart().startsWith("## "), "Slide missing ## heading: ${slide.take(50)}")
        }
    }

    @Test
    fun slideHeadingsMatchExpected() {
        val result = SlideGenerator.generate(fullBrdMarkdown())
        val slides = result.split("\n\n---\n\n")
        val headings = slides.map { it.lines().first().removePrefix("## ").trim() }
        val expected = listOf(
            "Vision/Overview", "Requirements Overview", "Data Flow",
            "Scope", "Key Stakeholders", "Risk Summary", "Timeline & Milestones"
        )
        assertEquals(expected, headings)
    }

    @Test
    fun maxSevenBulletsPerSlide() {
        val manyBullets = (1..15).joinToString("\n") { "- Requirement $it" }
        val brd = buildBrdMarkdown(
            "Revision History" to "Version 1.0.",
            "Project Overview" to manyBullets,
            "Common Project Acronyms, Names, and Descriptions" to "- Acronym 1",
            "Existing Processes" to "- Process item",
            "Project Requirements" to manyBullets,
            "Sign Off" to "- Stakeholder 1",
            "Appendix" to "- Ref 1"
        )
        val result = SlideGenerator.generate(brd)
        val slides = result.split("\n\n---\n\n")
        slides.forEach { slide ->
            val bulletCount = slide.lines().count { it.trimStart().startsWith("- ") }
            assertTrue(bulletCount <= 7, "Slide has $bulletCount bullets (max 7): ${slide.take(80)}")
        }
    }

    @Test
    fun handlesMissingSectionsGracefully() {
        val brd = buildBrdMarkdown("Project Overview" to "Basic overview.")
        val result = SlideGenerator.generate(brd)
        val slides = result.split("\n\n---\n\n")
        assertEquals(7, slides.size, "Should still produce 7 slides")
    }

    @Test
    fun insufficientDataSectionsShowWarningMessage() {
        val brd = buildBrdMarkdown(
            "Project Overview" to "⚠️ Insufficient data — This section requires manual input.",
            "Existing Processes" to "Valid scope content."
        )
        val result = SlideGenerator.generate(brd)
        assertTrue(result.contains("⚠️"), "Should show warning marker for insufficient data sections")
    }

    @Test
    fun extractsBulletPointsFromContent() {
        val brd = buildBrdMarkdown(
            "Project Overview" to "Overview text",
            "Project Requirements" to "- First req\n- Second req\n- Third req"
        )
        val result = SlideGenerator.generate(brd)
        assertTrue(result.contains("First req"), "Should extract bullet: First req")
        assertTrue(result.contains("Second req"), "Should extract bullet: Second req")
    }

    @Test
    fun fallsBackToSentenceExtractionWhenNoBullets() {
        val brd = buildBrdMarkdown(
            "Project Overview" to "This is a detailed overview of the project. It covers multiple areas.",
            "Project Requirements" to "Requirement content here."
        )
        val result = SlideGenerator.generate(brd)
        val slides = result.split("\n\n---\n\n")
        assertEquals(7, slides.size)
    }
}
