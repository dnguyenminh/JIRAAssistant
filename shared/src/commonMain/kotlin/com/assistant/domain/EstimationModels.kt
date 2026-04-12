package com.assistant.domain

import kotlinx.serialization.Serializable

/**
 * Representation of a Scrum story point estimation.
 */
@Serializable
data class ScrumEstimation(
    val suggestedPoints: Double, // 0, 0.5, 1, 2, 3, 5, 8, 13, 21, 40
    val confidenceScore: Double, // 0.0 to 1.0
    val rationale: String,
    val similarHistoricalTickets: List<SimilarTicket> = emptyList()
)

@Serializable
data class SimilarTicket(
    val ticketKey: String,
    val summary: String,
    val actualPoints: Double,
    val similarityScore: Double
)

@Serializable
data class NewRequirement(
    val summary: String,
    val description: String,
    val featureArea: String? = null
)
