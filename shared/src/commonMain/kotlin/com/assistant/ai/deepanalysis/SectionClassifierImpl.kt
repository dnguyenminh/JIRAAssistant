package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.ClassifiedContent
import com.assistant.ai.deepanalysis.models.ExtractionConfidence

/**
 * Regex-based implementation of SectionClassifier.
 *
 * Classifies ticket description into structured sections using
 * pattern matching. Computes extraction confidence based on the
 * number of recognized sections.
 *
 * Requirements: 17.1-17.6
 */
class SectionClassifierImpl : SectionClassifier {

    override fun classify(description: String): ClassifiedContent {
        if (description.isBlank()) {
            return buildFallbackResult(description)
        }
        return classifyDescription(description)
    }

    private fun classifyDescription(description: String): ClassifiedContent {
        val asIs = SectionExtractors.extractAsIsState(description)
        val toBe = SectionExtractors.extractToBeState(description)
        val apis = SectionExtractors.extractApiSpecifications(description)
        val dbChanges = SectionExtractors.extractDatabaseChanges(description)
        val extDeps = SectionExtractors.extractExternalDependencies(description)
        val criteria = SectionExtractors.extractAcceptanceCriteria(description)

        val sectionCount = countSections(
            asIs, toBe, apis.size, dbChanges.size,
            extDeps.size, criteria.size
        )
        val confidence = computeConfidence(sectionCount)

        return buildResult(
            asIs, toBe, apis, dbChanges, extDeps,
            criteria, description, confidence
        )
    }

    /** Req 17.6 — Fallback when description is blank. */
    private fun buildFallbackResult(description: String): ClassifiedContent {
        return ClassifiedContent(
            rawDescription = description,
            extractionConfidence = ExtractionConfidence.LOW
        )
    }

    private fun buildResult(
        asIs: String,
        toBe: String,
        apis: List<com.assistant.ai.deepanalysis.models.ApiSpecification>,
        dbChanges: List<com.assistant.ai.deepanalysis.models.DatabaseChange>,
        extDeps: List<com.assistant.ai.deepanalysis.models.ExternalIntegration>,
        criteria: List<String>,
        rawDescription: String,
        confidence: ExtractionConfidence
    ): ClassifiedContent {
        return ClassifiedContent(
            asIsState = asIs,
            toBeState = toBe,
            apiSpecifications = apis,
            databaseChanges = dbChanges,
            externalDependencies = extDeps,
            acceptanceCriteria = criteria,
            rawDescription = rawDescription,
            extractionConfidence = confidence
        )
    }
}

/**
 * Count how many distinct section types were found.
 * Requirements: 17.6, 25.6
 */
internal fun countSections(
    asIs: String,
    toBe: String,
    apiCount: Int,
    dbCount: Int,
    extDepCount: Int,
    criteriaCount: Int
): Int {
    var count = 0
    if (asIs.isNotEmpty() || toBe.isNotEmpty()) count++
    if (apiCount > 0) count++
    if (dbCount > 0) count++
    if (extDepCount > 0) count++
    if (criteriaCount > 0) count++
    return count
}

/**
 * Compute extraction confidence from section count.
 * HIGH: ≥4 sections, MEDIUM: 2-3, LOW: 0-1.
 * Requirements: 17.6, 25.6
 */
internal fun computeConfidence(sectionCount: Int): ExtractionConfidence {
    return when {
        sectionCount >= 4 -> ExtractionConfidence.HIGH
        sectionCount >= 2 -> ExtractionConfidence.MEDIUM
        else -> ExtractionConfidence.LOW
    }
}
