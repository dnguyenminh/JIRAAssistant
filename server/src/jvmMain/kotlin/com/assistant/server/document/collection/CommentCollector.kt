package com.assistant.server.document.collection

import com.assistant.jira.JiraClient
import com.assistant.server.document.models.CommentCollectionResult
import com.assistant.server.document.models.FullComment
import com.assistant.server.document.models.TraversalConfig
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Collects ALL comments for a ticket via Jira API pagination.
 *
 * - Page size: config.commentPageSize (default 50)
 * - Cap: config.maxCommentsPerTicket (default 200) — fetches N most recent
 * - Retry: max 2 retries with exponential backoff (1s, 2s) per page
 * - Partial success: keeps fetched comments on page failure
 * - Returns comments sorted chronologically (oldest first)
 *
 * Requirements: 3.1, 3.2, 3.4, 3.5, 3.7, 9.2, 9.3, 9.4
 */
class CommentCollector(
    private val jiraClient: JiraClient,
    private val config: TraversalConfig
) {
    private val logger = LoggerFactory.getLogger(CommentCollector::class.java)

    companion object {
        private const val MAX_RETRIES = 2
        private val BACKOFF_DELAYS = longArrayOf(1_000L, 2_000L)
    }

    /**
     * Fetch all comments for [ticketId] via Jira API pagination.
     * When total > maxCommentsPerTicket, skips to the N most recent comments.
     */
    suspend fun collectAll(ticketId: String): CommentCollectionResult {
        val firstPage = fetchPageWithRetry(ticketId, startAt = 0)
            ?: return emptyResult(hasPartialFailure = true)

        val totalReported = firstPage.total
        if (totalReported == 0) return emptyResult()

        val cap = config.maxCommentsPerTicket
        val effectiveStart = computeEffectiveStart(totalReported, cap, ticketId)
        val allComments = mutableListOf<FullComment>()
        var hasPartialFailure = false

        // If capping skips the first page, re-fetch from effectiveStart
        if (effectiveStart == 0) {
            allComments.addAll(firstPage.comments.map { it.toFullComment() })
        }

        var startAt = if (effectiveStart == 0) firstPage.comments.size else effectiveStart
        val targetCount = minOf(totalReported, cap)

        allComments.addAll(
            fetchRemainingPages(ticketId, startAt, targetCount, allComments.size)
                .also { hasPartialFailure = hasPartialFailure || it.second }
                .first
        )

        val sorted = allComments.sortedBy { it.createdDate }
        return CommentCollectionResult(
            comments = sorted,
            totalReported = totalReported,
            totalFetched = sorted.size,
            hasPartialFailure = hasPartialFailure
        )
    }

    /** Calculate startAt to skip to the N most recent comments. Req 3.7 */
    private fun computeEffectiveStart(total: Int, cap: Int, ticketId: String): Int {
        if (total <= cap) return 0
        val skip = total - cap
        logger.warn("Ticket {} has {} comments, capped at {}", ticketId, total, cap)
        return skip
    }

    /**
     * Fetch remaining pages after the initial page or effective start.
     * Returns (comments, hasPartialFailure).
     */
    private suspend fun fetchRemainingPages(
        ticketId: String,
        initialStartAt: Int,
        targetCount: Int,
        alreadyFetched: Int
    ): Pair<List<FullComment>, Boolean> {
        val comments = mutableListOf<FullComment>()
        var startAt = initialStartAt
        var fetched = alreadyFetched
        var hasPartialFailure = false

        while (fetched < targetCount) {
            val page = fetchPageWithRetry(ticketId, startAt)
            if (page == null) {
                hasPartialFailure = true
                logPageFailure(ticketId, fetched, targetCount)
                break
            }
            if (page.comments.isEmpty()) break

            comments.addAll(page.comments.map { it.toFullComment() })
            fetched += page.comments.size
            startAt += page.comments.size
        }
        return Pair(comments, hasPartialFailure)
    }

    /** Fetch a single page with up to [MAX_RETRIES] retries and exponential backoff. Req 9.3 */
    private suspend fun fetchPageWithRetry(
        ticketId: String,
        startAt: Int
    ): com.assistant.jira.JiraCommentPageResponse? {
        var lastException: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return jiraClient.getIssueComments(
                    ticketId, startAt, config.commentPageSize
                )
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    val delayMs = BACKOFF_DELAYS[attempt]
                    logger.warn(
                        "Retry {}/{} for comments page (ticket={}, startAt={}): {}",
                        attempt + 1, MAX_RETRIES, ticketId, startAt, e.message
                    )
                    delay(delayMs)
                }
            }
        }
        logger.error(
            "Failed to fetch comments page after {} retries (ticket={}, startAt={}): {}",
            MAX_RETRIES, ticketId, startAt, lastException?.message
        )
        return null
    }

    private fun logPageFailure(ticketId: String, fetched: Int, target: Int) {
        logger.warn(
            "Partial comment collection for ticket {}: fetched {}/{} comments",
            ticketId, fetched, target
        )
    }

    private fun emptyResult(hasPartialFailure: Boolean = false) =
        CommentCollectionResult(
            comments = emptyList(),
            totalReported = 0,
            totalFetched = 0,
            hasPartialFailure = hasPartialFailure
        )
}

/** Convert JiraComment to FullComment domain model. */
private fun com.assistant.jira.JiraComment.toFullComment() = FullComment(
    author = author?.displayName ?: "Unknown",
    createdDate = created ?: "",
    updatedDate = updated ?: "",
    body = bodyText
)
