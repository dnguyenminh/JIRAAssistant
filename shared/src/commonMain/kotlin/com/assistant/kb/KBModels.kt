package com.assistant.kb

import kotlinx.serialization.Serializable

@Serializable
data class KBRecord(
    val ticketId: String,
    val requirementSummary: String,
    val evolutionHistory: List<EvolutionEntry>,
    val scrumPoints: Double,
    val confidenceScore: Double,
    val rationale: String,
    val similarTicketRefs: List<String>,
    val timestamp: String
)

@Serializable
data class EvolutionEntry(
    val version: String,
    val date: String,
    val description: String,
    val changeType: String
)
