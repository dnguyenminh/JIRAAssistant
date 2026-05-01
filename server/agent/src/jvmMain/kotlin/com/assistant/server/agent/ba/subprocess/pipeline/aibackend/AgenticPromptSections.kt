package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor
import com.assistant.document.BrdPromptBuilder

/**
 * Prompt section builders for [AgenticPromptBuilder].
 *
 * Extracted to keep each file under 200 lines (SRP).
 * Contains tool-calling prompt sections and shared helpers.
 */

// ── Tool-calling prompt (when tools > 0) ────────────────

internal fun AgenticPromptBuilder.buildToolCallingPrompt(
    ticketId: String,
    docType: String,
    tools: List<ToolDescriptor>
): String {
    val detected = detectToolCategories(tools)
    return buildString {
        appendSystemInstructions()
        appendLine()
        appendToolDefinitions(tools)
        appendLine()
        appendToolProtocol(tools)
        appendLine()
        appendBrdSections(docType)
        appendLine()
        appendDiagramInstructions(detected)
        appendLine()
        appendToolTask(ticketId, tools)
    }
}

// ── Shared sections ─────────────────────────────────────

internal fun StringBuilder.appendSystemInstructions() {
    appendLine("# SYSTEM INSTRUCTIONS")
    appendLine()
    appendLine("You are a Business Analyst AI Agent.")
    appendLine("Your task is to create a Business Requirements Document (BRD).")
    appendLine("You follow the Carleton University ITS BRD template.")
}

internal fun StringBuilder.appendContextHeader() {
    appendLine("# CONTEXT")
    appendLine()
    appendLine("You are a Business Analyst AI Agent creating a BRD.")
}

internal fun StringBuilder.appendToolDefinitions(tools: List<ToolDescriptor>) {
    appendLine("## AVAILABLE TOOLS")
    appendLine()
    tools.forEach { tool ->
        val desc = tool.description.lines().first().take(120)
        append("- **${tool.name}**")
        if (tool.parameterNames.isNotEmpty()) {
            append("(${tool.parameterNames.joinToString(", ")})")
        }
        appendLine(": $desc")
    }
}

internal fun StringBuilder.appendToolProtocol(tools: List<ToolDescriptor>) {
    val toolNames = tools.joinToString(", ") { it.name }
    appendLine("## TOOL PROTOCOL")
    appendLine()
    appendLine("- ONLY use the tools listed above via JSON protocol")
    appendLine("- Allowed tools: $toolNames")
    appendLine()
    appendLine("To call a tool, respond with ONLY this JSON:")
    appendLine()
    appendLine("""{"type":"tool_call","tool":"<name>","params":{"key":"value"}}""")
    appendLine()
    appendLine("Rules:")
    appendLine("1. Tool call = ONLY JSON, no text before or after")
    appendLine("2. Final document = Markdown only, not JSON")
}

/**
 * Append BRD section headings from [BrdPromptBuilder.BRD_SECTIONS].
 * Single source of truth — matches what [BrdResponseParser] expects.
 */
internal fun StringBuilder.appendBrdSections(docType: String) {
    appendLine("## $docType STRUCTURE")
    appendLine()
    appendLine("Use ## headings for each section:")
    appendLine()
    BrdPromptBuilder.BRD_SECTIONS.forEachIndexed { i, section ->
        appendLine("${i + 1}. **$section**")
    }
    appendLine()
    appendSubSectionHints()
    appendLine()
    appendProcessGuidance()
    appendLine()
    appendLine("Each section MUST have real content (3+ lines minimum).")
    appendLine()
    appendLine("**CRITICAL — MANDATORY CONTENT RULES:**")
    appendLine("- NEVER write 'Insufficient data', 'N/A', 'No data available', or similar placeholder text in ANY section.")
    appendLine("- If you lack specific data for a section, use your analysis of the ticket to infer reasonable content.")
    appendLine("- Every section must have at least 3 lines of real, substantive content.")
    appendLine()
    appendLine("**Project Requirements** MUST include these sub-sections:")
    appendLine("- Process Overview — describe the proposed business process")
    appendLine("- Functional Requirements — list specific functional requirements")
    appendLine("- Non-Functional Requirements — performance, security, scalability")
    appendLine("- Data Requirements — data entities, relationships, storage needs")
}

private fun StringBuilder.appendSubSectionHints() {
    BrdPromptBuilder.BRD_SUB_SECTIONS.forEach { (parent, subs) ->
        appendLine("   $parent includes: ${subs.joinToString(", ")}")
    }
}

private fun StringBuilder.appendProcessGuidance() {
    appendLine("**IMPORTANT — AS-IS vs TO-BE:**")
    appendLine("- **Existing Processes** = AS-IS: describe the CURRENT process BEFORE the change")
    appendLine("- **Project Requirements > Process Overview** = TO-BE: describe the NEW/PROPOSED process AFTER the change")
    appendLine("Both sections MUST have content. Compare current vs proposed.")
}

internal fun StringBuilder.appendToolTask(
    ticketId: String,
    tools: List<ToolDescriptor>
) {
    appendLine("## TASK")
    appendLine()
    appendLine("Create a BRD for Jira ticket: **$ticketId**")
    appendLine()
    appendDataCollectionStrategy(ticketId, tools)
}

internal fun StringBuilder.appendCollectedData(toolResults: List<String>) {
    appendLine("## COLLECTED DATA")
    appendLine()
    if (toolResults.isEmpty()) {
        appendLine("No data collected yet.")
    } else {
        toolResults.forEachIndexed { i, result ->
            appendLine("### Result ${i + 1}")
            appendLine("```json")
            appendLine(result)
            appendLine("```")
            appendLine()
        }
    }
}

internal fun StringBuilder.appendNextStep(
    toolResults: List<String>,
    tools: List<ToolDescriptor>
) {
    appendLine("## NEXT STEP")
    appendLine()
    if (toolResults.size >= 2 || tools.isEmpty()) {
        appendLine("Produce the complete BRD in Markdown now (NOT JSON).")
    } else {
        appendLine("Call the next tool or produce the BRD if ready.")
    }
}
