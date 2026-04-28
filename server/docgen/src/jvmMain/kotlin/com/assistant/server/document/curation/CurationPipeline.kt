package com.assistant.server.document.curation

import com.assistant.server.document.curation.models.CuratedContext
import com.assistant.server.document.models.EnrichedContext

/**
 * Orchestrates the full curation pipeline:
 * TemporalClassifier → CommentSummarizer → AttachmentCurator → BudgetEnforcer.
 *
 * Contract:
 * - Input: EnrichedContext (any size)
 * - Output: CuratedContext (≤ 80K chars when assembled)
 * - Deterministic: same input → same output
 * - Stateless: no side effects, no external calls
 *
 * Requirements: 8.1, 8.4, 8.5
 */
interface CurationPipeline {
    fun curate(context: EnrichedContext): CuratedContext
}
