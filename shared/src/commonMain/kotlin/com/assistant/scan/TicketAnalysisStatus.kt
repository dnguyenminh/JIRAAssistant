package com.assistant.scan

import kotlinx.serialization.Serializable

@Serializable
enum class TicketAnalysisState {
    NOT_ANALYZED, SCANNED, ANALYZED, HAS_UPDATES, ANALYZING
}

@Serializable
data class TicketAnalysisStatus(
    val ticketId: String,
    val ticketSummary: String,
    val analysisState: TicketAnalysisState,
    val lastAnalyzedAt: String? = null,
    val ticketUpdatedAt: String? = null
)
