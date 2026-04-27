package com.assistant.server.document.curation

import com.assistant.server.document.curation.models.CommentSummary
import com.assistant.server.document.models.FullComment

/**
 * Default implementation of [CommentSummarizer].
 *
 * Rules:
 * - If hasKbRecord → skip entirely
 * - If ≤ 10 comments → include all (no summarization)
 * - If > 10 → extract decisions/clarifications/blockers + 3 recent
 * - Deduplicate bot comments → count + date range
 * - Output ≤ 2000 chars per ticket
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6
 */
class DefaultCommentSummarizer : CommentSummarizer {

    override fun summarize(
        comments: List<FullComment>,
        hasKbRecord: Boolean
    ): CommentSummary {
        if (hasKbRecord) return emptyCommentSummary()
        if (comments.isEmpty()) return emptyCommentSummary()
        if (comments.size <= CurationConfig.COMMENT_THRESHOLD) {
            return buildFullSummary(comments)
        }
        return buildCondensedSummary(comments)
    }

    private fun buildFullSummary(comments: List<FullComment>): CommentSummary {
        val totalChars = comments.sumOf { it.body.length }
        val capped = capToMaxChars(comments)
        return CommentSummary(
            recentComments = capped,
            totalChars = totalChars.coerceAtMost(MAX_CHARS)
        )
    }

    private fun buildCondensedSummary(
        comments: List<FullComment>
    ): CommentSummary {
        val (bots, substantive) = partitionBotComments(comments)
        val decisions = extractDecisions(substantive)
        val clarifications = extractClarifications(substantive)
        val blockers = extractBlockers(substantive)
        val recent = substantive.takeLast(3)
        val botSummary = buildBotSummary(bots)
        return CommentSummary(
            decisions = decisions,
            clarifications = clarifications,
            blockers = blockers,
            recentComments = recent,
            botSummary = botSummary,
            totalChars = computeTotalChars(
                decisions, clarifications, blockers, recent, botSummary
            )
        )
    }

    private fun partitionBotComments(
        comments: List<FullComment>
    ): Pair<List<FullComment>, List<FullComment>> {
        val bots = comments.filter { isBotComment(it) }
        val substantive = comments.filter { !isBotComment(it) }
        return bots to substantive
    }

    private fun isBotComment(comment: FullComment): Boolean {
        val author = comment.author.lowercase()
        val body = comment.body.lowercase()
        return BOT_AUTHORS.any { author.contains(it) } ||
            BOT_PATTERNS.any { body.startsWith(it) }
    }

    private fun extractDecisions(comments: List<FullComment>): List<String> =
        extractByKeywords(comments, DECISION_KEYWORDS)

    private fun extractClarifications(comments: List<FullComment>): List<String> =
        extractByKeywords(comments, CLARIFICATION_KEYWORDS)

    private fun extractBlockers(comments: List<FullComment>): List<String> =
        extractByKeywords(comments, BLOCKER_KEYWORDS)

    private fun extractByKeywords(
        comments: List<FullComment>,
        keywords: Set<String>
    ): List<String> {
        return comments
            .filter { c -> keywords.any { c.body.lowercase().contains(it) } }
            .map { it.body.take(200) }
            .take(5)
    }

    private fun buildBotSummary(bots: List<FullComment>): String? {
        if (bots.isEmpty()) return null
        val dates = bots.mapNotNull { it.createdDate.take(10).ifBlank { null } }
        val range = if (dates.size > 1) {
            "${dates.first()} to ${dates.last()}"
        } else dates.firstOrNull() ?: ""
        return "${bots.size} bot comments from $range"
    }

    private fun capToMaxChars(
        comments: List<FullComment>
    ): List<FullComment> {
        var remaining = MAX_CHARS
        return comments.takeWhile { c ->
            remaining -= c.body.length
            remaining >= 0
        }
    }

    private fun computeTotalChars(
        decisions: List<String>,
        clarifications: List<String>,
        blockers: List<String>,
        recent: List<FullComment>,
        botSummary: String?
    ): Int {
        val total = decisions.sumOf { it.length } +
            clarifications.sumOf { it.length } +
            blockers.sumOf { it.length } +
            recent.sumOf { it.body.length } +
            (botSummary?.length ?: 0)
        return total.coerceAtMost(MAX_CHARS)
    }

    private fun emptyCommentSummary() = CommentSummary(totalChars = 0)

    companion object {
        private const val MAX_CHARS = CurationConfig.MAX_COMMENT_CHARS_PER_TICKET

        private val BOT_AUTHORS = setOf(
            "scriptrunner", "automation", "jira-bot",
            "bitbucket", "confluence", "service desk"
        )

        private val BOT_PATTERNS = setOf(
            "status changed", "field updated", "transition:",
            "automatically generated", "reminder:"
        )

        private val DECISION_KEYWORDS = setOf(
            "decided", "decision", "agreed", "approved",
            "confirmed", "will proceed", "go with"
        )

        private val CLARIFICATION_KEYWORDS = setOf(
            "clarif", "meaning", "explain", "understand",
            "actually", "to be clear", "in other words"
        )

        private val BLOCKER_KEYWORDS = setOf(
            "block", "blocked", "waiting", "depend",
            "cannot proceed", "stuck", "impediment"
        )
    }
}
