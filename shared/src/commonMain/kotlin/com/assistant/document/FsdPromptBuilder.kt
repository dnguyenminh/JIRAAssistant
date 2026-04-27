package com.assistant.document

import com.assistant.document.models.GenerationContext

/**
 * Builds AI prompt for FSD generation from GenerationContext.
 *
 * Prompt includes: role, serialized context data, FSD-specific technical expansion,
 * 11 FSD section headings (FECredit Functional Specification template),
 * anti-hallucination instructions, source citation format,
 * and draw.io diagram instructions (4 diagrams).
 *
 * Requirements: 3.1–3.9, 9.1–9.3, 10.2, 10.7
 */
object FsdPromptBuilder {

    /** Top-level sections matching FECredit FSD template. */
    internal val FSD_SECTIONS = listOf(
        "Introduction",
        "System/Solution Overview",
        "Functional Specifications",
        "System Configurations",
        "Non-Functional Requirements",
        "Reporting Requirements",
        "Integration Requirements",
        "Data Migration/Conversion Requirements",
        "References",
        "Open Issues",
        "Appendix"
    )

    /** Sub-sections per top-level section for detailed prompt instructions. */
    internal val FSD_SUB_SECTIONS = mapOf(
        "Introduction" to listOf(
            "Purpose of the Document",
            "Project Scope",
            "Scope of the Document",
            "Related Documents",
            "Terms/Acronyms and Definitions",
            "Risks and Assumptions"
        ),
        "System/Solution Overview" to listOf(
            "Context Diagram/Interface Diagram/Data Flow Diagram",
            "System Actors",
            "Dependencies and Change Impacts"
        ),
        "Functional Specifications" to listOf(
            "Purpose/Description",
            "Use Cases",
            "Mock-ups",
            "Functional Requirements",
            "Field Level Specifications"
        ),
        "Integration Requirements" to listOf(
            "Exception Handling/Error Reporting"
        ),
        "Data Migration/Conversion Requirements" to listOf(
            "Data Conversion Strategy",
            "Data Conversion Preparation",
            "Data Conversion Specifications"
        )
    )

    /** Req 3.1: Build the full FSD generation prompt. */
    fun buildPrompt(context: GenerationContext): String {
        return buildString {
            appendFsdRole()
            appendContextData(context)
            appendFsdTechnicalExpansion(context)
            appendFsdTemplate()
            appendFsdInstructions()
            appendFsdDiagramInstructions()
            appendFsdOutputFormat()
        }
    }
}
