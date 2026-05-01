package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor

/** Maximum recursive exploration depth (main ticket = depth 0). */
internal const val DEPTH_LIMIT = 3

/** Maximum total tickets to explore (including main ticket). */
internal const val MAX_TICKETS = 30

/**
 * Orchestrates the data collection strategy for BRD generation.
 *
 * Delegates to: tool detection → KB cache → recursive exploration → final write.
 */
internal fun StringBuilder.appendDataCollectionStrategy(
    ticketId: String,
    tools: List<ToolDescriptor>
) {
    val detected = detectToolCategories(tools)
    appendLine("## DATA COLLECTION STRATEGY")
    appendLine()
    appendLine("You MUST collect comprehensive data before writing.")
    appendLine("Do NOT write the BRD after just one tool call.")
    appendLine("Follow these steps IN ORDER:")
    appendLine()
    appendKbCacheInstructions(detected, tools)
    appendRecursiveExplorationInstructions(ticketId, detected, tools)
    appendFinalWriteStep()
}

private fun StringBuilder.appendRecursiveExplorationInstructions(
    ticketId: String,
    detected: DetectedTools,
    tools: List<ToolDescriptor>
) {
    appendLine("### RECURSIVE EXPLORATION")
    appendLine()
    if (!detected.hasJiraTools) {
        appendLine("No Jira tools available. Use any available tools to gather data.")
        appendLine()
        return
    }
    appendExplorationInit(ticketId, detected)
    appendExplorationPriority()
    appendDepthGuidelines()
    appendExplorationLimits()
}

private fun StringBuilder.appendExplorationInit(
    ticketId: String,
    detected: DetectedTools
) {
    val issueTool = detected.getIssueTool
    if (issueTool != null) {
        appendLine("**START NOW:** Call `$issueTool` with issue_key=\"$ticketId\".")
        appendLine()
    }
    appendLine("From the response for **$ticketId** (depth 0), extract:")
    appendLine("- Summary, description, status, priority, type")
    appendLine("- **Linked issues** (parent, subtasks, blocks, relates-to)")
    appendLine("- **Attachment names** (screenshots, specs, diagrams)")
    appendLine()
    appendLine("Then explore linked tickets recursively:")
    appendLine("- Maintain a **visited set** — skip any ticket already visited.")
    appendLine("- For each ticket, also read its attachments (specs, design docs).")
    appendLine()
}

private fun StringBuilder.appendExplorationPriority() {
    appendLine("**Exploration priority order:**")
    appendLine("1. Parent / epic ticket")
    appendLine("2. Blocking / blocked-by tickets")
    appendLine("3. Relates-to tickets")
    appendLine("4. Sub-tasks")
    appendLine("5. Ticket IDs mentioned in text fields (description, comments)")
    appendLine()
}

private fun StringBuilder.appendDepthGuidelines() {
    appendLine("**Depth-based summarization rules:**")
    appendLine("- Depth 0–1: Collect full details + attachments")
    appendLine("- Depth 2+: Collect summary only (key requirements, status)")
    appendLine()
}

private fun StringBuilder.appendExplorationLimits() {
    appendLine("**Limits:**")
    appendLine("- Maximum depth: $DEPTH_LIMIT")
    appendLine("- Maximum total tickets: $MAX_TICKETS")
    appendLine("- **Early termination:** Stop when no new unvisited tickets are found.")
    appendLine()
}

private fun StringBuilder.appendFinalWriteStep() {
    appendLine("### FINAL STEP: Write the BRD")
    appendLine()
    appendLine("ONLY after collecting data from exploration,")
    appendLine("produce the complete BRD with real data.")
    appendLine("Every section must reference actual ticket data.")
    appendLine("Do NOT write 'Insufficient data' — use what you have.")
    appendLine()
    appendLine("Start exploration now.")
}
