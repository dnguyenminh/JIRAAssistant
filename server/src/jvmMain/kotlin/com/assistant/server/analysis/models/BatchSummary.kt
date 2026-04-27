package com.assistant.server.analysis.models

import kotlinx.serialization.Serializable

/**
 * AI-generated summary for a single batch of tickets.
 *
 * Parsed from AI JSON response with `ignoreUnknownKeys = true`.
 * All fields have default values (empty string / empty list) so that
 * missing fields in the AI response produce a usable object instead
 * of throwing a deserialization exception.
 *
 * Requirements: 3.5, 8.1, 8.2, 8.3
 */
@Serializable
data class BatchSummary(
    /** Batch index (0-based). Batch 0 always contains the root ticket. */
    val batchIndex: Int = 0,
    /** Ticket IDs analyzed in this batch. */
    val ticketIds: List<String> = emptyList(),
    /** Summary of requirements discovered in this batch. */
    val requirementsSummary: String = "",
    /** Technical insights: API specs, DB changes, architecture notes. */
    val technicalInsights: String = "",
    /** Dependencies between tickets in this batch and others. */
    val dependencySummary: String = "",
    /** Key findings and important discoveries. */
    val keyFindings: List<String> = emptyList(),
    /** Unresolved questions from this batch. */
    val openQuestions: List<String> = emptyList()
)
