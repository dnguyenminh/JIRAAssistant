package com.assistant.server.document.traversal

import com.assistant.server.document.models.RelationshipType
import com.assistant.server.document.models.TicketNode
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Computes relevance scores for tickets in the BFS graph.
 *
 * Score factors:
 * - Depth: closer to root = higher (1.0 / (1 + depth))
 * - Relationship type weight: ROOT=1.0, PARENT=0.9, blocking=0.8, other=0.6, SUB_TASK=0.5, TEXT_REF=0.3
 * - Recency bonus: +0.1 if updated within 30 days
 * - Status bonus: +0.1 if status is open/active (not closed/resolved/done)
 *
 * Requirements: 1.9
 */
object RelevanceScorer {

    private const val RECENCY_BONUS = 0.1
    private const val STATUS_BONUS = 0.1
    private const val RECENCY_DAYS = 30L

    private val CLOSED_STATUSES = setOf(
        "closed", "resolved", "done", "cancelled", "rejected"
    )

    /**
     * Compute relevance score for a [TicketNode].
     * @return Score in range ~0.0..1.2 (base + bonuses).
     */
    fun compute(node: TicketNode): Double {
        val depthScore = 1.0 / (1 + node.depth)
        val typeWeight = relationshipWeight(node.discoveredVia)
        var score = depthScore * typeWeight
        score += recencyBonus(node.issue.updatedDate)
        score += statusBonus(node.issue.status)
        return score
    }

    /** Weight by relationship type. */
    internal fun relationshipWeight(type: RelationshipType): Double {
        return when (type) {
            RelationshipType.ROOT -> 1.0
            RelationshipType.PARENT -> 0.9
            RelationshipType.ISSUE_LINK -> 0.7
            RelationshipType.SUB_TASK -> 0.5
            RelationshipType.TEXT_REFERENCE -> 0.3
        }
    }

    /** +0.1 if updated within last 30 days. */
    internal fun recencyBonus(updatedDate: String): Double {
        if (updatedDate.isBlank()) return 0.0
        return try {
            val updated = Instant.parse(updatedDate)
            val cutoff = Instant.now().minus(RECENCY_DAYS, ChronoUnit.DAYS)
            if (updated.isAfter(cutoff)) RECENCY_BONUS else 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    /** +0.1 if status is NOT closed/resolved/done. */
    internal fun statusBonus(status: String): Double {
        if (status.isBlank()) return 0.0
        return if (status.lowercase() in CLOSED_STATUSES) 0.0
        else STATUS_BONUS
    }
}
