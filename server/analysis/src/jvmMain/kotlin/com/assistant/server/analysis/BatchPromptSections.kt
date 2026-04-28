package com.assistant.server.analysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.BatchInfo
import com.assistant.server.document.models.TicketEdge
import com.assistant.server.document.models.TicketNode

/**
 * Extension functions that append individual sections to a
 * [BatchPromptBuilder] prompt. Kept in a separate file so that
 * both [BatchPromptBuilder] and this file stay under 200 lines.
 *
 * Requirements: 3.1, 3.2, 3.6, 3.7
 */

/** System instruction for the Map phase AI call. */
internal fun StringBuilder.appendBatchSystemInstruction() {
    appendLine("You are analyzing a batch of Jira tickets as part of a larger analysis pipeline.")
    appendLine("Summarize the tickets in this batch into a structured JSON object.")
    appendLine("Focus on requirements, technical insights, dependencies, and key findings.")
    appendLine()
}

/** Root ticket context included in every batch prompt. */
internal fun StringBuilder.appendRootContext(root: StructuredTicketContent) {
    appendLine("=== ROOT TICKET CONTEXT ===")
    appendLine("Summary: ${root.summary}")
    if (root.description.isNotBlank()) {
        appendLine("Description: ${root.description}")
    }
    appendLine()
}

/** Batch metadata line: "Batch X of Y, containing N tickets at depth levels ...". */
internal fun StringBuilder.appendBatchMetadata(batchInfo: BatchInfo) {
    val depths = batchInfo.depthLevels.joinToString(", ")
    appendLine(
        "=== BATCH METADATA === Batch ${batchInfo.batchIndex + 1} of ${batchInfo.totalBatches}, " +
            "containing ${batchInfo.tickets.size} tickets at depth levels [$depths]"
    )
    appendLine()
}

/** Full ticket data for a single ticket node. */
internal fun StringBuilder.appendTicketSection(
    node: TicketNode,
    includeComments: Boolean
) {
    val issue = node.issue
    appendLine("--- Ticket: ${node.ticketId} (depth=${node.depth}) ---")
    appendLine("Summary: ${issue.summary}")
    appendLine("Status: ${issue.status}  Priority: ${issue.priority}")
    appendTicketLabels(issue)
    appendTicketDescription(issue)
    if (includeComments) appendTicketComments(issue)
}

/** Labels line — only if non-empty. */
internal fun StringBuilder.appendTicketLabels(issue: StructuredTicketContent) {
    if (issue.labels.isNotEmpty()) {
        appendLine("Labels: ${issue.labels.joinToString(", ")}")
    }
}

/** Description — only if non-blank. */
internal fun StringBuilder.appendTicketDescription(issue: StructuredTicketContent) {
    if (issue.description.isNotBlank()) {
        appendLine("Description: ${issue.description}")
    }
}

/** Comments — only if non-empty. */
internal fun StringBuilder.appendTicketComments(issue: StructuredTicketContent) {
    if (issue.comments.isEmpty()) return
    appendLine("Comments:")
    issue.comments.forEach { c ->
        appendLine("  [${c.createdDate}] ${c.author}: ${c.content}")
    }
}

/** Relationship edges relevant to this batch. */
internal fun StringBuilder.appendRelationships(edges: List<TicketEdge>) {
    if (edges.isEmpty()) return
    appendLine("=== RELATIONSHIPS ===")
    edges.forEach { e ->
        val desc = if (e.linkDescription.isNotBlank()) " (${e.linkDescription})" else ""
        appendLine("- ${e.sourceId} → ${e.targetId}: ${e.relationshipType}$desc")
    }
    appendLine()
}

/** JSON output schema for BatchSummary. */
internal fun StringBuilder.appendBatchSummarySchema() {
    appendLine("=== OUTPUT FORMAT ===")
    appendLine("Respond with ONLY valid JSON matching this schema:")
    appendLine(BATCH_SUMMARY_SCHEMA)
    appendLine()
    appendLine("Output valid JSON only. No markdown fences, no extra text.")
}

/** The JSON schema string for BatchSummary. */
private const val BATCH_SUMMARY_SCHEMA = """{
  "batchIndex": 0,
  "ticketIds": ["TICKET-1", "TICKET-2"],
  "requirementsSummary": "string — summary of requirements discovered",
  "technicalInsights": "string — API specs, DB changes, architecture notes",
  "dependencySummary": "string — dependencies between tickets",
  "keyFindings": ["string — important discovery 1", "string — important discovery 2"],
  "openQuestions": ["string — unresolved question 1"]
}"""
