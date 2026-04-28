package com.assistant.server.agent.ba.subprocess.pipeline

import com.assistant.server.agent.ba.subprocess.pipeline.models.CollectedContext

/**
 * Builds focused, small prompts for each pipeline step.
 *
 * Each prompt ≤200 lines, contains NO tool descriptions or
 * tool call instructions — only role instruction + step task + data.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 5.2
 */
object StepPromptBuilder {

    private const val MAX_PROMPT_LINES = 200
    private const val MAX_DATA_CHARS = 8000

    fun buildAnalysisPrompt(context: CollectedContext, docType: String): String {
        val hint = if (docType == "BRD") "business goals and stakeholder impact"
            else "technical architecture and system interactions"
        val ticketData = truncate(context.rootTicketData.data)
        val kbData = truncate(context.kbAnalysisData.data)
        val prompt = buildString {
            appendLine("You are a Senior Business Analyst.")
            appendLine()
            appendLine("Task: Analyze the following ticket data and extract:")
            appendLine("- Business goals and objectives")
            appendLine("- Stakeholders and their roles")
            appendLine("- Project scope and boundaries")
            appendLine("- Key requirements (focus on $hint)")
            appendLine()
            appendLine("=== TICKET DATA ===")
            appendLine(ticketData)
            appendLine()
            appendLine("=== KNOWLEDGE BASE DATA ===")
            appendLine(kbData)
            appendLine()
            appendLine("Respond with your analysis in plain text. End with ---END---")
        }
        return ensureLineLimit(prompt)
    }

    fun buildRequirementsPrompt(analysisResult: String, linkedData: String): String {
        val prompt = buildString {
            appendLine("You are a Senior Business Analyst.")
            appendLine()
            appendLine("Task: Based on the analysis below, expand requirements")
            appendLine("using linked ticket data. Identify additional requirements,")
            appendLine("dependencies, and cross-references.")
            appendLine()
            appendLine("=== PREVIOUS ANALYSIS ===")
            appendLine(truncate(analysisResult))
            appendLine()
            appendLine("=== LINKED TICKETS DATA ===")
            appendLine(truncate(linkedData))
            appendLine()
            appendLine("Respond with expanded requirements in plain text. End with ---END---")
        }
        return ensureLineLimit(prompt)
    }

    fun buildWritingPrompt(accumulatedResults: String, docType: String): String {
        val templateHint = buildTemplateHint(docType)
        val prompt = buildString {
            appendLine("You are a Senior Business Analyst.")
            appendLine()
            appendLine("Task: Write a complete $docType document using the")
            appendLine("analysis and requirements below.")
            appendLine()
            appendLine("=== ACCUMULATED ANALYSIS ===")
            appendLine(truncate(accumulatedResults))
            appendLine()
            appendLine("=== DOCUMENT STRUCTURE ===")
            appendLine(templateHint)
            appendLine()
            appendLine("Respond with the complete document in markdown. End with ---END---")
        }
        return ensureLineLimit(prompt)
    }

    fun buildReviewPrompt(feedback: String, currentDocument: String): String {
        val prompt = buildString {
            appendLine("You are a Senior Business Analyst.")
            appendLine()
            appendLine("Task: Review and improve the document based on the feedback below.")
            appendLine("Address every issue mentioned in the feedback.")
            appendLine()
            appendLine("=== FEEDBACK ===")
            appendLine(truncate(feedback))
            appendLine()
            appendLine("=== CURRENT DOCUMENT ===")
            appendLine(truncate(currentDocument))
            appendLine()
            appendLine("Respond with the complete revised document in markdown. End with ---END---")
        }
        return ensureLineLimit(prompt)
    }

    // ── Helpers ─────────────────────────────────────────

    private fun buildTemplateHint(docType: String): String = when (docType) {
        "BRD" -> """
            |Use these sections: Introduction, Business Objectives,
            |Stakeholders, Scope, Requirements, Assumptions,
            |Constraints, Risks, Appendix
        """.trimMargin()
        "FSD" -> """
            |Use these sections: Introduction, System Overview,
            |Functional Specifications, System Configurations,
            |Non-Functional Requirements, Integration Requirements,
            |Data Migration, References, Open Issues, Appendix
        """.trimMargin()
        else -> "Use standard document sections appropriate for $docType."
    }

    private fun truncate(text: String, maxChars: Int = MAX_DATA_CHARS): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) +
            "\n... [truncated, ${text.length - maxChars} chars omitted]"
    }

    private fun ensureLineLimit(prompt: String): String {
        val lines = prompt.lines()
        if (lines.size <= MAX_PROMPT_LINES) return prompt
        return lines.take(MAX_PROMPT_LINES - 1).joinToString("\n") +
            "\n... [truncated to $MAX_PROMPT_LINES lines]"
    }
}
