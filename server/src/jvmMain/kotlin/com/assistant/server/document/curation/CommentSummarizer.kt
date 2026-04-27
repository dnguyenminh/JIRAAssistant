package com.assistant.server.document.curation

import com.assistant.server.document.curation.models.CommentSummary
import com.assistant.server.document.models.FullComment

/**
 * Summarizes and deduplicates comments for a single ticket.
 *
 * Requirements: 4.1
 */
interface CommentSummarizer {
    /**
     * Produce a condensed summary of comments.
     *
     * @param comments All comments for the ticket (oldest first)
     * @param hasKbRecord Whether the ticket has a KB record (skip if true)
     * @return Summarized comment data (≤ 2000 chars)
     */
    fun summarize(
        comments: List<FullComment>,
        hasKbRecord: Boolean
    ): CommentSummary
}
