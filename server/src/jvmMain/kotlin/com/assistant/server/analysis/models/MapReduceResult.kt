package com.assistant.server.analysis.models

import com.assistant.ai.AnalysisResult
import com.assistant.ai.deepanalysis.models.MapReduceInfo
import kotlinx.serialization.Serializable

/**
 * Result of the Map-Reduce analysis pipeline.
 * Contains the final [AnalysisResult] plus pipeline execution metadata.
 *
 * The [analysisResult] uses the same format as the single-prompt flow,
 * ensuring backward compatibility for downstream consumers.
 *
 * Requirements: 4.5
 */
@Serializable
data class MapReduceResult(
    /** Final analysis result — same format as single-prompt flow. */
    val analysisResult: AnalysisResult,
    /** Map-Reduce pipeline execution metadata. */
    val mapReduceInfo: MapReduceInfo
)
