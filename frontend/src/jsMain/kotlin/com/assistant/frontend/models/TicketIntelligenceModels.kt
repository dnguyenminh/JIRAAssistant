package com.assistant.frontend.models

import com.assistant.ai.deepanalysis.models.AcceptanceCriterion
import com.assistant.ai.deepanalysis.models.AnalysisMetadata
import com.assistant.ai.deepanalysis.models.DependencyInfo
import com.assistant.ai.deepanalysis.models.DiagramData
import com.assistant.ai.deepanalysis.models.TechnicalDetails
import kotlinx.serialization.Serializable

/**
 * Frontend model for AI ticket analysis response.
 * Extended with deep analysis fields (Requirements 19.1-19.6).
 * New fields are optional with defaults for backward compatibility.
 */
@Serializable
data class AnalysisResponse(
    val ticketId: String = "",
    val context: RequirementSummary? = null,
    val evolution: List<EvolutionEntry> = emptyList(),
    val complexity: ComplexityAssessment? = null,
    val source: String = "",
    // Deep analysis fields — Requirements 19.2, 19.3, 19.4, 19.5, 19.6
    val technicalDetails: TechnicalDetails = TechnicalDetails(),
    val acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    val dependencies: DependencyInfo = DependencyInfo(),
    val analysisMetadata: AnalysisMetadata = AnalysisMetadata(),
    // Diagrams — Requirements 28.1, 28.2
    val diagrams: List<DiagramData> = emptyList()
)

/**
 * Requirement summary with deep analysis fields (Requirements 19.1, 19.6).
 * New fields are optional with defaults for backward compatibility.
 */
@Serializable
data class RequirementSummary(
    val unified: String = "",
    val affectedModules: List<AffectedModule> = emptyList(),
    // Deep analysis fields — Requirements 19.1, 19.6
    val businessSummary: String = "",
    val asIsState: String = "",
    val toBeState: String = "",
    val extractedRequirements: List<String> = emptyList()
)

@Serializable
data class AffectedModule(
    val name: String = "",
    val colorCategory: String = "PRIMARY"
)

@Serializable
data class EvolutionEntry(
    val version: String = "",
    val date: String = "",
    val description: String = "",
    val changeType: String = ""
)

@Serializable
data class ComplexityAssessment(
    val scrumPoints: Double = 0.0,
    val description: String = "",
    val kbReferences: List<KBReference> = emptyList()
)

@Serializable
data class KBReference(
    val ticketId: String = "",
    val similarityPercent: Double = 0.0
)

@Serializable
data class AnalysisStatus(
    val ticketId: String = "",
    val phase: String = "",
    val progressPercent: Int = 0
)
