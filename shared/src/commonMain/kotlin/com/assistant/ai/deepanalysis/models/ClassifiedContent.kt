package com.assistant.ai.deepanalysis.models

import kotlinx.serialization.Serializable

/**
 * Classified sections extracted from ticket description by SectionClassifier.
 *
 * Contains recognized sections: As-Is/To-Be states, API specifications,
 * database changes, external dependencies, and acceptance criteria.
 * Falls back to raw_description with LOW confidence when no structured
 * sections are identified.
 *
 * Requirements: 17.1-17.6
 */
@Serializable
data class ClassifiedContent(
    // As-Is / To-Be states (Req 17.1)
    val asIsState: String = "",
    val toBeState: String = "",

    // API specifications (Req 17.2) — reuses ApiSpecification from TechnicalDetails
    val apiSpecifications: List<ApiSpecification> = emptyList(),

    // Database changes (Req 17.3) — reuses DatabaseChange from TechnicalDetails
    val databaseChanges: List<DatabaseChange> = emptyList(),

    // External dependencies (Req 17.4) — reuses ExternalIntegration from TechnicalDetails
    val externalDependencies: List<ExternalIntegration> = emptyList(),

    // Acceptance criteria (Req 17.5)
    val acceptanceCriteria: List<String> = emptyList(),

    // Raw description fallback (Req 17.6)
    val rawDescription: String = "",

    // Extraction confidence — LOW when no structured sections found (Req 17.6)
    val extractionConfidence: ExtractionConfidence = ExtractionConfidence.LOW
)
