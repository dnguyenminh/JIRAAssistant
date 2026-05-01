package com.assistant.server.document.models

import kotlinx.serialization.Serializable

/**
 * A single comment fetched from Jira API with full content (no truncation).
 *
 * Used by [CommentCollector] to store each comment's complete data
 * and included in [EnrichedContext] for prompt assembly.
 *
 * Requirements: 3.3
 */
@Serializable
data class FullComment(
    /** Display name of the comment author. */
    val author: String,
    /** ISO-8601 date string when the comment was created. */
    val createdDate: String,
    /** ISO-8601 date string when the comment was last updated. Empty if never updated. */
    val updatedDate: String = "",
    /** Full comment body text — no truncation applied. */
    val body: String
)

/**
 * Result of collecting comments for a single ticket via Jira API pagination.
 *
 * Tracks both the total reported by Jira and the actual number fetched,
 * allowing callers to detect partial failures during pagination.
 *
 * This class is internal to the collection process and does not need serialization.
 *
 * Requirements: 3.3
 */
data class CommentCollectionResult(
    /** Comments sorted chronologically (oldest first). */
    val comments: List<FullComment>,
    /** Total comment count reported by Jira API. */
    val totalReported: Int,
    /** Actual number of comments successfully fetched. */
    val totalFetched: Int,
    /** True if one or more pagination pages failed after retries. */
    val hasPartialFailure: Boolean = false
)
