package com.assistant.document

import com.assistant.document.models.GenerationContext

/**
 * Builds AI prompt for BRD generation from GenerationContext.
 *
 * Prompt includes: role, serialized context data, 7 BRD section headings
 * (Carleton University ITS Business Requirements Template),
 * anti-hallucination instructions, source citation format,
 * and draw.io diagram instructions (3 diagrams).
 *
 * Requirements: 2.1–2.8, 9.1–9.3, 10.1, 10.7
 */
object BrdPromptBuilder {

    /** 7 top-level sections matching Carleton ITS BRD template. */
    val BRD_SECTIONS = listOf(
        "Revision History",
        "Project Overview",
        "Common Project Acronyms, Names, and Descriptions",
        "Existing Processes",
        "Project Requirements",
        "Sign Off",
        "Appendix"
    )

    /** Sub-sections per top-level section for detailed prompt instructions. */
    val BRD_SUB_SECTIONS = mapOf(
        "Project Overview" to listOf(
            "Project Sponsor(s)",
            "Project Contributors",
            "In Scope (Deliverables)",
            "Out of Scope"
        ),
        "Existing Processes" to listOf(
            "Summary Process Narrative",
            "Timing",
            "Volume",
            "Screenshots",
            "Problems"
        ),
        "Project Requirements" to listOf(
            "Process Overview",
            "Functional Requirements",
            "Non-Functional Requirements",
            "Data Requirements"
        ),
        "Appendix" to listOf(
            "Mock-ups",
            "Glossary",
            "Business Rules and Procedures",
            "Document References"
        )
    )

    /**
     * Deep sub-sections (level 3) for BRD sections with nested structure.
     * Maps sub-section name → list of deep sub-section headings.
     */
    val BRD_DEEP_SUB_SECTIONS = mapOf(
        "Process Overview" to listOf(
            "Summary Process Narrative",
            "Flow Diagram",
            "Triggering Event and Pre-Conditions",
            "Timing",
            "Volume",
            "Outcome(s) and/or Post-Conditions"
        ),
        "Non-Functional Requirements" to listOf(
            "Availability",
            "Compatibility",
            "Extensibility",
            "Maintainability",
            "Scalability",
            "Security",
            "Usability",
            "Performance"
        ),
        "Data Requirements" to listOf(
            "Known Issues/Assumptions/Risks/Dependencies"
        )
    )

    /** Req 2.1: Build the full BRD generation prompt. */
    fun buildPrompt(context: GenerationContext): String {
        return buildString {
            appendRole()
            appendContextData(context)
            appendBrdTemplate()
            appendInstructions()
            appendDiagramInstructions()
            appendOutputFormat()
        }
    }
}
