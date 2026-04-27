package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * Confidence level for content extraction.
 * HIGH: ≥4 sections identified, MEDIUM: 2-3 sections, LOW: 0-1 sections.
 * Requirements: 19.5, 25.6
 */
@Serializable
enum class ExtractionConfidence {
    HIGH, MEDIUM, LOW
}

/**
 * Metadata about the analysis process.
 * Requirements: 19.5, 4.5
 */
@Serializable
data class AnalysisMetadata(
    val extractionConfidence: ExtractionConfidence = ExtractionConfidence.LOW,
    val analyzedAt: String = "",
    val aiProviderUsed: String = "",
    val promptVersion: String = "",
    /** Map-Reduce pipeline metadata. Null when single-prompt flow is used. */
    val mapReduceInfo: MapReduceInfo? = null
)
