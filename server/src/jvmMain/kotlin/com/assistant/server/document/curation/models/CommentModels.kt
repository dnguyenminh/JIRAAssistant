package com.assistant.server.document.curation.models

import com.assistant.server.document.models.FullComment
import kotlinx.serialization.Serializable

/**
 * Summarized comment data for a single ticket.
 * Output is always ≤ 2000 characters when serialized.
 *
 * Requirements: 4.1, 4.5
 */
@Serializable
data class CommentSummary(
    val decisions: List<String> = emptyList(),
    val clarifications: List<String> = emptyList(),
    val blockers: List<String> = emptyList(),
    val recentComments: List<FullComment> = emptyList(),
    val botSummary: String? = null,
    val totalChars: Int = 0
)

/**
 * Curated attachment with preview content and priority.
 *
 * Requirements: 5.1
 */
@Serializable
data class CuratedAttachment(
    val filename: String,
    val ticketId: String,
    val preview: String,
    val priority: Int,
    val isRequirementDoc: Boolean
)
