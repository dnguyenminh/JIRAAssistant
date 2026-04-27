package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * A single acceptance criterion extracted from ticket analysis.
 * Requirements: 19.3
 */
@Serializable
data class AcceptanceCriterion(
    val id: String = "",
    val description: String = "",
    val testabilityAssessment: String = ""
)
