package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Implementation of DeepAnalysisPromptBuilder.
 *
 * Constructs a detailed prompt with 6 analysis aspects, conditional
 * API/DB sections, strict JSON output schema, and anti-hallucination
 * instruction. Delegates section building to PromptSections helpers.
 *
 * Requirements: 18.1-18.6
 */
class DeepAnalysisPromptBuilderImpl : DeepAnalysisPromptBuilder {

    override fun buildPrompt(content: StructuredTicketContent): String {
        return buildString {
            appendSystemInstruction()
            appendTicketOverview(content)
            appendDescriptionSection(content)
            appendSubTasksSection(content)
            appendIssueLinksSection(content)
            appendLinkedTicketsContext(content)
            appendCommentsSection(content)
            appendChangelogSection(content)
            appendAttachmentsSection(content)
            appendApiSection(content)
            appendDatabaseSection(content)
            appendAnalysisInstructions()
            appendDiagramInstructions()
            appendJsonOutputSchema()
            appendAntiHallucinationInstruction()
        }
    }
}
