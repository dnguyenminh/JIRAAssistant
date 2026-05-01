package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.document.BrdPromptBuilder

/**
 * Builds a direct-generation prompt for when no MCP tools are available.
 *
 * Instead of asking the AI to call tools, this prompt provides the
 * ticket ID and instructs the AI to generate the BRD directly using
 * its knowledge. Used primarily with Ollama local models.
 *
 * Section headings match [BrdPromptBuilder.BRD_SECTIONS] exactly
 * so [BrdResponseParser] can parse the output correctly.
 */
internal fun AgenticPromptBuilder.buildDirectGenerationPrompt(
    ticketId: String,
    docType: String
): String = buildString {
    appendDirectSystemRole()
    appendLine()
    appendBrdSections(docType)
    appendLine()
    appendDirectTask(ticketId, docType)
    appendLine()
    appendDirectOutputRules()
}

private fun StringBuilder.appendDirectSystemRole() {
    appendLine("# SYSTEM INSTRUCTIONS")
    appendLine()
    appendLine("You are a Senior Business Analyst AI.")
    appendLine("You follow the Carleton University ITS BRD template.")
    appendLine("Generate a detailed BRD based on the ticket ID provided.")
    appendLine()
    appendLine("CRITICAL: Do NOT output JSON. Do NOT call any tools.")
    appendLine("Output ONLY a Markdown document with ## headings.")
}

private fun StringBuilder.appendDirectTask(
    ticketId: String,
    docType: String
) {
    appendLine("## TASK")
    appendLine()
    appendLine("Generate a complete $docType for ticket: **$ticketId**")
    appendLine()
    appendLine("Since no external tools are available, generate the")
    appendLine("document using your knowledge. For each section:")
    appendLine("- Provide realistic, detailed content")
    appendLine("- Reference the ticket ID as [Source: $ticketId]")
    appendLine("- Mark inferred content with [INFERRED] tag")
    appendLine("- Mark assumptions with [ASSUMPTION] tag")
}

private fun StringBuilder.appendDirectOutputRules() {
    appendLine("## OUTPUT RULES")
    appendLine()
    appendLine("1. Output ONLY Markdown — no JSON, no tool calls")
    appendLine("2. Use ## headings for each section (exactly as listed)")
    appendLine("3. Each section MUST have 3+ lines of real content")
    appendLine("4. Write in English with Vietnamese terms where relevant")
    appendLine("5. Be detailed — aim for 2000+ words total")
    appendLine()
    appendLine("Start writing the document now. First heading: ## Revision History")
}
