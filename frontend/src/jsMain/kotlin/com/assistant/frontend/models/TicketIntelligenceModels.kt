package com.assistant.frontend.models

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResponse(
    val ticketId: String = "",
    val context: RequirementSummary? = null,
    val evolution: List<EvolutionEntry> = emptyList(),
    val complexity: ComplexityAssessment? = null,
    val source: String = ""
)

@Serializable
data class RequirementSummary(
    val unified: String = "",
    val affectedModules: List<AffectedModule> = emptyList()
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
