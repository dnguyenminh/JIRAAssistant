package com.assistant.server.agent.home

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkillParser] covering valid parsing, missing sections,
 * tool list extraction, and edge cases.
 *
 * **Validates: Requirements 15.1, 15.4, 15.5**
 */
class SkillParserTest {

    @Test
    @Tag("generic-agent-framework")
    fun `parse valid skill file with all sections`() {
        val content = """
            |# My Skill
            |
            |## Purpose
            |Analyze Jira tickets for completeness.
            |
            |## Available Tools
            |- fetchJiraDetails
            |- processAttachment
            |
            |## Procedure
            |1. Fetch the ticket
            |2. Analyze fields
            |
            |## Output Format
            |JSON report with findings.
            |
            |## Constraints
            |Max 10 tickets per batch.
        """.trimMargin()

        val result = SkillParser.parse("analysis.md", content)

        result.shouldNotBeNull()
        result.fileName shouldBe "analysis.md"
        result.purpose shouldBe "Analyze Jira tickets for completeness."
        result.availableTools shouldContainExactly listOf("fetchJiraDetails", "processAttachment")
        result.procedure shouldBe "1. Fetch the ticket\n2. Analyze fields"
        result.outputFormat shouldBe "JSON report with findings."
        result.constraints shouldBe "Max 10 tickets per batch."
        result.rawContent shouldBe content
    }

    @Test
    @Tag("generic-agent-framework")
    fun `parse skill file with only required sections`() {
        val content = """
            |## Purpose
            |Do something useful.
            |
            |## Procedure
            |Step 1: Do it.
        """.trimMargin()

        val result = SkillParser.parse("minimal.md", content)

        result.shouldNotBeNull()
        result.purpose shouldBe "Do something useful."
        result.procedure shouldBe "Step 1: Do it."
        result.availableTools.shouldBeEmpty()
        result.outputFormat shouldBe ""
        result.constraints shouldBe ""
    }

    @Test
    @Tag("generic-agent-framework")
    fun `returns null when Purpose section is missing`() {
        val content = """
            |## Procedure
            |Step 1: Do it.
        """.trimMargin()

        SkillParser.parse("no-purpose.md", content).shouldBeNull()
    }

    @Test
    @Tag("generic-agent-framework")
    fun `returns null when Procedure section is missing`() {
        val content = """
            |## Purpose
            |Analyze tickets.
        """.trimMargin()

        SkillParser.parse("no-procedure.md", content).shouldBeNull()
    }

    @Test
    @Tag("generic-agent-framework")
    fun `returns null for empty content`() {
        SkillParser.parse("empty.md", "").shouldBeNull()
    }

    @Test
    @Tag("generic-agent-framework")
    fun `parses Available Tools with various bullet markers`() {
        val content = """
            |## Purpose
            |Test tools parsing.
            |
            |## Available Tools
            |- toolA
            |* toolB
            |• toolC
            |  toolD
            |
            |## Procedure
            |Do things.
        """.trimMargin()

        val result = SkillParser.parse("tools.md", content)

        result.shouldNotBeNull()
        result.availableTools shouldContainExactly listOf("toolA", "toolB", "toolC", "toolD")
    }

    @Test
    @Tag("generic-agent-framework")
    fun `ignores extra sections not in the schema`() {
        val content = """
            |## Purpose
            |Main purpose.
            |
            |## Custom Section
            |This should be ignored.
            |
            |## Procedure
            |Step 1.
        """.trimMargin()

        val result = SkillParser.parse("extra.md", content)

        result.shouldNotBeNull()
        result.purpose shouldBe "Main purpose."
        result.procedure shouldBe "Step 1."
    }

    @Test
    @Tag("generic-agent-framework")
    fun `each skill file is independently loadable`() {
        val skill1 = SkillParser.parse("a.md", "## Purpose\nA\n\n## Procedure\nDo A")
        val skill2 = SkillParser.parse("b.md", "## Purpose\nB\n\n## Procedure\nDo B")

        skill1.shouldNotBeNull()
        skill2.shouldNotBeNull()
        skill1.purpose shouldBe "A"
        skill2.purpose shouldBe "B"
    }
}
