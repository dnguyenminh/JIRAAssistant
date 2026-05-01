package com.assistant.server.agent.ba.prompt

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.StructuredMemory
import com.assistant.server.agent.ba.memory.hasKBRecord
import com.assistant.server.document.curation.CommentSummarizer
import com.assistant.server.document.models.FullComment
import java.time.Instant

/**
 * Applies CommentSummarizer to raw comments in memory.
 *
 * Reads raw comment entries from the "comments" slot,
 * groups by source ticket, summarizes each group, and
 * stores condensed summaries back into memory.
 *
 * Requirements: 4.1, 4.5, 4.6
 */
internal object CommentCurationStep {

    fun apply(memory: StructuredMemory, summarizer: CommentSummarizer) {
        val rawComments = memory.getSlot("comments")
        if (rawComments.isEmpty()) return
        val grouped = groupByTicket(rawComments)
        grouped.forEach { (ticketId, entries) ->
            summarizeTicketComments(memory, summarizer, ticketId, entries)
        }
    }

    private fun groupByTicket(
        entries: List<MemoryEntry>
    ): Map<String, List<MemoryEntry>> =
        entries.groupBy { it.source }

    private fun summarizeTicketComments(
        memory: StructuredMemory,
        summarizer: CommentSummarizer,
        ticketId: String,
        entries: List<MemoryEntry>
    ) {
        val hasKb = memory.hasKBRecord(ticketId)
        val comments = entries.map { parseComment(it) }
        val summary = summarizer.summarize(comments, hasKb)
        if (summary.totalChars > 0) {
            storeSummaryEntry(memory, ticketId, summary.toString())
        }
    }

    private fun parseComment(entry: MemoryEntry): FullComment {
        val parts = entry.data.split("|", limit = 3)
        return FullComment(
            author = parts.getOrElse(0) { "unknown" },
            createdDate = entry.timestamp,
            body = parts.getOrElse(2) { entry.data }
        )
    }

    private fun storeSummaryEntry(
        memory: StructuredMemory,
        ticketId: String,
        summaryText: String
    ) {
        memory.store(
            "comments",
            MemoryEntry(summaryText, ticketId, "commentSummarizer", Instant.now().toString())
        )
    }
}
