package com.assistant.scan

import com.assistant.ai.AnalysisResult
import com.assistant.ai.deepanalysis.models.StructuredTicketContent

private const val MAX_BATCH_CONTENT_LENGTH = 3000

/**
 * Convert StructuredTicketContent to a condensed plain-text representation
 * suitable for batch prompts. Includes summary, description, sub-tasks,
 * and comments — capped at 3000 chars.
 */
internal fun formatStructuredContent(content: StructuredTicketContent): String {
    return buildString {
        appendLine("Summary: ${content.summary}")
        appendDescription(content)
        appendSubTasks(content)
        appendComments(content)
    }.take(MAX_BATCH_CONTENT_LENGTH)
}

private fun StringBuilder.appendDescription(content: StructuredTicketContent) {
    if (content.description.isNotBlank()) {
        appendLine("Description: ${content.description}")
    }
}

private fun StringBuilder.appendSubTasks(content: StructuredTicketContent) {
    if (content.subTasks.isEmpty()) return
    appendLine("Sub-tasks:")
    content.subTasks.forEach { task ->
        appendLine("  - [${task.status}] ${task.summary}")
    }
}

private fun StringBuilder.appendComments(content: StructuredTicketContent) {
    if (content.comments.isEmpty()) return
    appendLine("Comments:")
    content.comments.forEach { comment ->
        appendLine("  - ${comment.author}: ${comment.content}")
    }
}

private const val MIN_VALID_SUMMARY_LENGTH = 10

/**
 * Detect placeholder/garbage analysis results from batch AI responses.
 * Returns true when requirementSummary is "...", contains "Placeholder",
 * or is shorter than 10 characters. Req: bugfix 2.4
 */
internal fun isPlaceholderResult(result: AnalysisResult): Boolean {
    val summary = result.context.unified.trim()
    return summary == "..." ||
        summary.contains("Placeholder", ignoreCase = true) ||
        summary.length < MIN_VALID_SUMMARY_LENGTH
}
