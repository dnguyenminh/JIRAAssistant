package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

import com.assistant.agent.models.ToolDescriptor

/**
 * KB-first, Jira-fallback prompt instructions.
 *
 * Instructs the AI agent to check Knowledge Base before calling Jira API,
 * and to cache Jira results back into KB for future reuse.
 *
 * When no KB tools are available, returns early — caller handles direct Jira strategy.
 */
internal fun StringBuilder.appendKbCacheInstructions(
    detected: DetectedTools,
    tools: List<ToolDescriptor>
) {
    if (!detected.hasKbTools) return
    appendLine("### KB CACHE STRATEGY (per ticket)")
    appendLine("Before calling Jira for any ticket, check Knowledge Base first:")
    appendLine()
    appendKbLookupStep(detected)
    appendJiraFallbackStep(detected)
    appendKbSaveStep(detected)
}

private fun StringBuilder.appendKbLookupStep(detected: DetectedTools) {
    appendLine("**Step A — KB Lookup:**")
    if (detected.kbContextTool != null) {
        appendLine("1. Call `${detected.kbContextTool}` with query=<ticket_id>")
        appendLine("   for a token-efficient briefing on the ticket.")
    }
    val searchTool = detected.kbSearchTool
    if (searchTool != null) {
        appendLine("${if (detected.kbContextTool != null) "2" else "1"}. Call `$searchTool` with query=<ticket_id>")
        appendLine("   to find detailed cached data for the ticket.")
    }
    appendLine("3. Evaluate result: does it contain summary, description, and requirements?")
    appendLine("   - YES → use KB data, skip Jira call for this ticket.")
    appendLine("   - NO or insufficient → proceed to Step B.")
    appendLine()
}

private fun StringBuilder.appendJiraFallbackStep(detected: DetectedTools) {
    appendLine("**Step B — Jira Fallback:**")
    appendLine("If KB data is missing or insufficient:")
    if (detected.getIssueTool != null) {
        appendLine("- Call `${detected.getIssueTool}` with issue_key=<ticket_id>")
        appendLine("  to get full ticket data from Jira.")
    } else {
        appendLine("- Use any available Jira tool to fetch the ticket data.")
    }
    appendLine("- Extract: summary, description, status, linked issues, attachments.")
    appendLine()
}

private fun StringBuilder.appendKbSaveStep(detected: DetectedTools) {
    appendLine("**Step C — KB Save:**")
    appendLine("After fetching data from Jira, cache it in KB for next time:")
    val ingestTool = detected.kbIngestTool
    if (ingestTool != null) {
        appendLine("- Call `$ingestTool` with title=<ticket_id> and content=<summary + description>")
        appendLine("  so future BRD generations can reuse this data.")
    } else {
        appendLine("- If a KB write tool becomes available, save the data for reuse.")
    }
    appendLine()
}
