package com.assistant.kb

import com.assistant.ai.deepanalysis.models.AcceptanceCriterion
import com.assistant.ai.deepanalysis.models.AnalysisMetadata
import com.assistant.ai.deepanalysis.models.DependencyInfo
import com.assistant.ai.deepanalysis.models.TechnicalDetails
import kotlinx.serialization.Serializable

/**
 * Serializable container for deep analysis fields stored as JSON in KB.
 * Used to serialize/deserialize the deep_analysis_json column.
 * Requirements: 20.1, 20.4
 */
@Serializable
data class KBDeepAnalysisData(
    val technicalDetails: TechnicalDetails = TechnicalDetails(),
    val acceptanceCriteria: List<AcceptanceCriterion> = emptyList(),
    val dependencies: DependencyInfo = DependencyInfo(),
    val analysisMetadata: AnalysisMetadata = AnalysisMetadata(),
    val businessSummary: String = "",
    val asIsState: String = "",
    val toBeState: String = "",
    val extractedRequirements: List<String> = emptyList(),
    val diagrams: List<com.assistant.ai.deepanalysis.models.DiagramData> = emptyList()
)

/** Extract deep analysis data from a KBRecord into a serializable container. */
fun KBRecord.toDeepAnalysisData(): KBDeepAnalysisData = KBDeepAnalysisData(
    technicalDetails = technicalDetails,
    acceptanceCriteria = acceptanceCriteria,
    dependencies = dependencies,
    analysisMetadata = analysisMetadata,
    businessSummary = businessSummary,
    asIsState = asIsState,
    toBeState = toBeState,
    extractedRequirements = extractedRequirements,
    diagrams = diagrams
)
