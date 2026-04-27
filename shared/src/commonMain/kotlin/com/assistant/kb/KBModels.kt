package com.assistant.kb

import com.assistant.ai.deepanalysis.models.AcceptanceCriterion
import com.assistant.ai.deepanalysis.models.AnalysisMetadata
import com.assistant.ai.deepanalysis.models.DependencyInfo
import com.assistant.ai.deepanalysis.models.TechnicalDetails
import kotlinx.serialization.Serializable

/**
 * Knowledge Base record for a ticket analysis result.
 * Deep analysis fields (20.1) are optional with defaults for backward compatibility (20.4).
 */
@Serializable
data class KBRecord(
    val ticketId: String,
    val requirementSummary: String,
    val evolutionHistory: List<EvolutionEntry>,
    val scrumPoints: Double,
    val confidenceScore: Double,
    val rationale: String,
    val similarTicketRefs: List<String>,
    val timestamp: String,
    // Deep analysis fields — Requirements 20.1, 20.4
    val technicalDetails: TechnicalDetails = TechnicalDetails(),
    val acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    val dependencies: DependencyInfo = DependencyInfo(),
    val analysisMetadata: AnalysisMetadata = AnalysisMetadata(),
    val businessSummary: String = "",
    val asIsState: String = "",
    val toBeState: String = "",
    val extractedRequirements: List<String> = emptyList(),
    // Diagrams — Requirements 28.1, 28.2
    val diagrams: List<com.assistant.ai.deepanalysis.models.DiagramData> = emptyList()
)

@Serializable
data class EvolutionEntry(
    val version: String,
    val date: String,
    val description: String,
    val changeType: String
)
