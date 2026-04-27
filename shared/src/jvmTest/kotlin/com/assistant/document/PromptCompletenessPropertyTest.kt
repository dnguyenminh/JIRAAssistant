package com.assistant.document

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Property 3: BRD prompt completeness.
 * Property 4: FSD prompt completeness.
 *
 * For any valid GenerationContext, prompts contain all required
 * section headings, anti-hallucination instructions, and source citation format.
 *
 * **Validates: Requirements 2.1, 2.2, 3.1, 3.2, 9.1, 9.2**
 */
class PromptCompletenessPropertyTest {

    @Test
    fun `Property 3 - BRD prompt contains all 18 headings and instructions`() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.generationContext()) { ctx ->
            val prompt = BrdPromptBuilder.buildPrompt(ctx)
            assertBrdHeadingsPresent(prompt)
            assertAntiHallucinationPresent(prompt)
            assertSourceCitationPresent(prompt)
        }
    }

    @Test
    fun `Property 4 - FSD prompt contains all 10 headings and instructions`() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.generationContext()) { ctx ->
            val prompt = FsdPromptBuilder.buildPrompt(ctx)
            assertFsdHeadingsPresent(prompt)
            assertAntiHallucinationPresent(prompt)
            assertSourceCitationPresent(prompt)
        }
    }

    private fun assertBrdHeadingsPresent(prompt: String) {
        BrdPromptBuilder.BRD_SECTIONS.forEach { heading ->
            assertTrue(
                prompt.contains(heading),
                "BRD prompt missing section: $heading"
            )
        }
    }

    private fun assertFsdHeadingsPresent(prompt: String) {
        FsdPromptBuilder.FSD_SECTIONS.forEach { heading ->
            assertTrue(
                prompt.contains(heading),
                "FSD prompt missing section: $heading"
            )
        }
    }

    private fun assertAntiHallucinationPresent(prompt: String) {
        val lower = prompt.lowercase()
        val hasInstruction = lower.contains("do not fabricate") ||
            lower.contains("do not invent") ||
            lower.contains("only data from context")
        assertTrue(hasInstruction, "Prompt missing anti-hallucination instruction")
    }

    private fun assertSourceCitationPresent(prompt: String) {
        assertTrue(
            prompt.contains("[Source:"),
            "Prompt missing source citation format instruction"
        )
    }
}
