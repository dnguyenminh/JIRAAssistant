package com.assistant.server.analysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.BatchSummary
import com.assistant.server.document.models.TraversalMetadata

/**
 * Builds AI prompt for the Reduce phase — combining all BatchSummaries
 * into a final AnalysisResult.
 *
 * Prompt contains:
 * - Root ticket full data
 * - All BatchSummaries sorted by batchIndex
 * - Ticket graph metadata (total tickets, max depth, relationships)
 * - Incomplete analysis warning (when <50% batches succeeded)
 * - JSON output schema matching AnalysisResult format
 *
 * Requirements: 4.1, 4.4, 4.6, 7.4
 */
class ReducePromptBuilder {

    /**
     * Build reduce prompt from batch summaries.
     *
     * @param rootTicket Root ticket content
     * @param summaries All successful BatchSummaries
     * @param graphMetadata Traversal metadata
     * @param totalBatches Total batches attempted (including failed)
     * @return Prompt string for reduce AI call
     */
    fun buildPrompt(
        rootTicket: StructuredTicketContent,
        summaries: List<BatchSummary>,
        graphMetadata: TraversalMetadata,
        totalBatches: Int
    ): String {
        val sorted = summaries.sortedBy { it.batchIndex }
        return buildString {
            appendSystemInstruction()
            appendRootTicketSection(rootTicket)
            appendGraphMetadata(graphMetadata)
            appendIncompleteWarning(sorted.size, totalBatches)
            appendBatchSummaries(sorted)
            appendOutputSchema()
            appendFinalInstruction()
        }
    }
}

/** System instruction for the reduce phase AI call. */
private fun StringBuilder.appendSystemInstruction() {
    appendLine("=== SYSTEM INSTRUCTION ===")
    append("You are synthesizing analysis results from multiple batches ")
    append("of a large Jira ticket graph. Each batch was analyzed ")
    append("independently. Your task is to combine all batch summaries ")
    append("into a single, comprehensive AnalysisResult that covers ")
    appendLine("the entire ticket graph.")
    appendLine()
}

/** Root ticket full data section. */
private fun StringBuilder.appendRootTicketSection(
    rootTicket: StructuredTicketContent
) {
    appendLine("=== ROOT TICKET ===")
    appendLine("Summary: ${rootTicket.summary}")
    appendLine("Status: ${rootTicket.status}")
    appendLine("Priority: ${rootTicket.priority}")
    appendLine("Description:")
    appendLine(rootTicket.description.ifBlank { "(no description)" })
    appendLine()
}

/** Ticket graph metadata section. */
private fun StringBuilder.appendGraphMetadata(
    metadata: TraversalMetadata
) {
    appendLine("=== TICKET GRAPH METADATA ===")
    appendLine("Total tickets discovered: ${metadata.totalDiscovered}")
    appendLine("Total tickets fetched: ${metadata.totalFetched}")
    appendLine("Max depth reached: ${metadata.maxDepthReached}")
    appendLine("Traversal time: ${metadata.traversalTimeMs}ms")
    appendLine()
}

/** WARNING annotation when less than 50% of batches succeeded. */
private fun StringBuilder.appendIncompleteWarning(
    successfulBatches: Int,
    totalBatches: Int
) {
    if (totalBatches > 0 && successfulBatches < totalBatches / 2) {
        append("WARNING: Only $successfulBatches/$totalBatches batches ")
        append("were successfully analyzed. ")
        appendLine("Analysis may be incomplete.")
        appendLine()
    }
}

/** All batch summaries sorted by batchIndex ascending. */
private fun StringBuilder.appendBatchSummaries(
    sorted: List<BatchSummary>
) {
    appendLine("=== BATCH SUMMARIES ===")
    sorted.forEach { summary -> appendSingleBatch(summary) }
    appendLine()
}

/** Format a single BatchSummary section. */
private fun StringBuilder.appendSingleBatch(summary: BatchSummary) {
    appendLine("--- Batch ${summary.batchIndex} ---")
    appendLine("Tickets: ${summary.ticketIds.joinToString(", ")}")
    appendLine("Requirements: ${summary.requirementsSummary}")
    appendLine("Technical Insights: ${summary.technicalInsights}")
    appendLine("Dependencies: ${summary.dependencySummary}")
    appendKeyFindings(summary.keyFindings)
    appendOpenQuestions(summary.openQuestions)
    appendLine()
}

/** Key findings list for a batch. */
private fun StringBuilder.appendKeyFindings(findings: List<String>) {
    if (findings.isEmpty()) return
    appendLine("Key Findings:")
    findings.forEach { appendLine("  - $it") }
}

/** Open questions list for a batch. */
private fun StringBuilder.appendOpenQuestions(questions: List<String>) {
    if (questions.isEmpty()) return
    appendLine("Open Questions:")
    questions.forEach { appendLine("  - $it") }
}

/** JSON output schema matching AnalysisResult format. */
private fun StringBuilder.appendOutputSchema() {
    appendLine("=== OUTPUT FORMAT ===")
    appendLine("Return ONLY valid JSON matching this schema:")
    appendLine(REDUCE_JSON_SCHEMA)
    appendLine()
}

/** Final instruction to output valid JSON. */
private fun StringBuilder.appendFinalInstruction() {
    appendLine("=== CRITICAL INSTRUCTION ===")
    append("Synthesize ALL batch summaries above into a single ")
    append("comprehensive analysis. Do NOT fabricate information ")
    appendLine("not present in the batch summaries or root ticket.")
    append("If a section has no relevant data, return empty strings ")
    appendLine("or empty arrays for that field.")
    append("Return ONLY valid JSON. No markdown fences, ")
    appendLine("no extra text outside the JSON object.")
}
