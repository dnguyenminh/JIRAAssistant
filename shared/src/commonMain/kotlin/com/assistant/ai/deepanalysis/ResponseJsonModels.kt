package com.assistant.ai.deepanalysis

import kotlinx.serialization.Serializable

/**
 * Intermediate JSON models matching the AI response schema
 * defined in PromptJsonSchema.kt. These are deserialized from
 * the raw AI response, then mapped to AnalysisResult.
 *
 * All fields have defaults for Req 25.2 (missing optional fields).
 *
 * Requirements: 25.1, 25.2
 */

@Serializable
internal data class AIResponseRoot(
    val requirementSummary: AIRequirementSummary = AIRequirementSummary(),
    val evolution: List<AIEvolutionEntry> = emptyList(),
    val complexity: AIComplexity = AIComplexity(),
    val technicalDetails: AITechnicalDetails = AITechnicalDetails(),
    val acceptanceCriteria: List<AIAcceptanceCriterion> = emptyList(),
    val dependencies: AIDependencies = AIDependencies(),
    val analysisMetadata: AIAnalysisMetadata = AIAnalysisMetadata(),
    val diagrams: List<AIDiagram> = emptyList()
)

@Serializable
internal data class AIDiagram(
    val type: String = "",
    val title: String = "",
    val mermaidCode: String = "",
    val format: String = "mermaid",
    val drawioMetadata: AIDrawioMetadata? = null
)

@Serializable
internal data class AIDrawioMetadata(
    val template: String = "component",
    val nodes: List<AIDrawioNode> = emptyList(),
    val connections: List<AIDrawioConnection> = emptyList()
)

@Serializable
internal data class AIDrawioNode(
    val id: String = "",
    val label: String = "",
    val type: String = ""
)

@Serializable
internal data class AIDrawioConnection(
    val from: String = "",
    val to: String = "",
    val label: String = ""
)

@Serializable
internal data class AIRequirementSummary(
    val unified: String = "",
    val businessSummary: String = "",
    val asIsState: String = "",
    val toBeState: String = "",
    val extractedRequirements: List<String> = emptyList(),
    val affectedModules: List<AIAffectedModule> = emptyList()
)

@Serializable
internal data class AIAffectedModule(
    val name: String = "",
    val colorCategory: String = "PRIMARY"
)

@Serializable
internal data class AIEvolutionEntry(
    val version: String = "",
    val date: String = "",
    val description: String = "",
    val changeType: String = "UPDATE"
)

@Serializable
internal data class AIComplexity(
    val scrumPoints: Double = 0.0,
    val description: String = "",
    val kbReferences: List<AIKBReference> = emptyList()
)

@Serializable
internal data class AIKBReference(
    val ticketId: String = "",
    val similarityPercent: Double = 0.0
)

@Serializable
internal data class AITechnicalDetails(
    val apiSpecifications: List<AIApiSpec> = emptyList(),
    val databaseChanges: List<AIDbChange> = emptyList(),
    val externalIntegrations: List<AIExternalIntegration> = emptyList()
)

@Serializable
internal data class AIApiSpec(
    val method: String = "",
    val path: String = "",
    val description: String = ""
)

@Serializable
internal data class AIDbChange(
    val tableName: String = "",
    val operationType: String = "",
    val columns: List<String> = emptyList(),
    val description: String = ""
)

@Serializable
internal data class AIExternalIntegration(
    val serviceName: String = "",
    val protocol: String = "",
    val endpoint: String = "",
    val description: String = ""
)

@Serializable
internal data class AIAcceptanceCriterion(
    val id: String = "",
    val description: String = "",
    val testabilityAssessment: String = ""
)

@Serializable
internal data class AIDependencies(
    val blockingIssues: List<AIDependencyItem> = emptyList(),
    val relatedIssues: List<AIDependencyItem> = emptyList(),
    val externalDependencies: List<String> = emptyList()
)

@Serializable
internal data class AIDependencyItem(
    val key: String = "",
    val summary: String = "",
    val relationshipType: String = "",
    val riskLevel: String = ""
)

@Serializable
internal data class AIAnalysisMetadata(
    val extractionConfidence: String = ""
)
