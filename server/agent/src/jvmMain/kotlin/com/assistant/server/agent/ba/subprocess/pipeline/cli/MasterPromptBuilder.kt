package com.assistant.server.agent.ba.subprocess.pipeline.cli

import com.assistant.agent.models.ToolDescriptor

/**
 * Builds the single master prompt sent to the CLI at session start.
 *
 * The prompt contains role instructions, strategy/reasoning, available tools,
 * tool result format, template structure (BRD or FSD), output constraints,
 * task description, and optional custom instructions.
 */
object MasterPromptBuilder {

    private const val END_DELIMITER = "---END---"

    /**
     * Builds a complete master prompt for the given ticket and document type.
     *
     * @param ticketId      Root ticket identifier (e.g. "ICL2-15")
     * @param docType       Document type: "BRD" or "FSD"
     * @param availableTools Tools the AI can invoke during the session
     * @param customInstructions Optional extra instructions appended at the end
     */
    fun build(
        ticketId: String,
        docType: String,
        availableTools: List<ToolDescriptor>,
        customInstructions: String? = null
    ): String = buildString {
        appendLine(buildRoleSection())
        appendLine()
        appendLine(buildStrategySection(ticketId))
        appendLine()
        appendLine(buildToolsSection(availableTools))
        appendLine()
        appendLine(buildToolResultFormat())
        appendLine()
        appendLine(buildTemplateSection(docType))
        appendLine()
        appendLine(buildOutputConstraints())
        appendLine()
        appendLine(buildTaskSection(ticketId))
        if (!customInstructions.isNullOrBlank()) {
            appendLine()
            appendLine(buildCustomSection(customInstructions))
        }
    }.trimEnd()

    private fun buildRoleSection(): String = buildString {
        appendLine("## ROLE INSTRUCTION")
        append("You are a Senior Business Analyst (15+ years experience)")
        append(" specializing in FinTech, BPM (Flowable),")
        append(" and Enterprise Integration.")
        append(" You follow the Carleton University ITS BRD template.")
    }

    private fun buildStrategySection(ticketId: String): String = buildString {
        appendLine("## STRATEGY & REASONING (X10THINK)")
        appendLine("1. **Explore & Fetch:** Use `knowledge_base_get_ticket_info` for root ticket $ticketId. Identify all linked tickets.")
        appendLine("2. **Deep Dive:** Use `knowledge_base_search_relationships` to find dependencies.")
        append("3. **Gap Analysis:** If data is missing, fallback to `jira_get_issue` or infer based on the project's tech stack.")
    }

    private fun buildToolsSection(tools: List<ToolDescriptor>): String = buildString {
        appendLine("## AVAILABLE TOOLS (JSON ONLY)")
        appendLine("Format: `{\"toolCall\":{\"name\":\"<toolName>\",\"arguments\":{...}}}`")
        appendLine()
        tools.forEachIndexed { index, tool ->
            val params = formatToolParams(tool.parameterNames)
            appendLine("${index + 1}. **${tool.name}** - ${tool.description}")
            if (params.isNotEmpty()) appendLine("   Parameters: $params")
        }
    }

    private fun formatToolParams(names: List<String>): String =
        if (names.isEmpty()) "" else names.joinToString(", ")

    private fun buildToolResultFormat(): String = buildString {
        appendLine("## TOOL RESULT FORMAT")
        append("I will provide results in this format: ")
        append("`{\"toolResult\":{\"name\":\"<toolName>\",\"success\":true/false,\"data\":\"...\",\"error\":\"...\"}}`")
    }

    private fun buildTemplateSection(docType: String): String =
        if (docType.equals("FSD", ignoreCase = true)) buildFsdTemplate()
        else buildBrdTemplate()

    private fun buildBrdTemplate(): String = buildString {
        appendLine("## TEMPLATE STRUCTURE (BRD)")
        appendLine("1. Revision History")
        appendLine("2. Project Overview")
        appendLine("3. Common Project Acronyms")
        appendLine("4. Existing Processes")
        appendLine("5. Project Requirements (5.1 Flow, 5.2 Functional PREQ-NNN, 5.3 NFR, 5.4 Data)")
        appendLine("6. Known Issues/Assumptions/Risks/Dependencies")
        append("7. Sign Off & Appendix")
    }

    private fun buildFsdTemplate(): String = buildString {
        appendLine("## TEMPLATE STRUCTURE (FSD)")
        appendLine("1. Revision History")
        appendLine("2. System Overview")
        appendLine("3. Architecture Design")
        appendLine("4. Component Specifications")
        appendLine("5. API Specifications")
        appendLine("6. Data Model")
        appendLine("7. Integration Points")
        appendLine("8. Security Design")
        appendLine("9. Error Handling")
        appendLine("10. Testing Strategy")
        append("11. Deployment & Operations")
    }

    private fun buildOutputConstraints(): String = buildString {
        appendLine("## OUTPUT CONSTRAINTS")
        appendLine("- Cite sources as [Source: TICKET-ID].")
        appendLine("- Mark inferences with [INFERRED].")
        append("- End with ONLY the delimiter: $END_DELIMITER")
    }

    private fun buildTaskSection(ticketId: String): String = buildString {
        appendLine("## TASK")
        append("Collect data for $ticketId and write complete document. Start with knowledge_base_get_ticket_info.")
    }

    private fun buildCustomSection(instructions: String): String = buildString {
        appendLine("## CUSTOM INSTRUCTIONS")
        append(instructions.trim())
    }
}
