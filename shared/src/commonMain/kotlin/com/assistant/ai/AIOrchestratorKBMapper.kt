package com.assistant.ai

import com.assistant.ai.deepanalysis.models.AnalysisMetadata
import com.assistant.ai.deepanalysis.models.DependencyInfo
import com.assistant.ai.deepanalysis.models.TechnicalDetails
import com.assistant.kb.KBRecord

/**
 * Mapping functions between AnalysisResult and KBRecord.
 * Extracted from AIOrchestratorImpl for SRP compliance.
 *
 * Requirements: 20.1-20.4 (backward compatible KB mapping)
 */

/** Convert KBRecord → AnalysisResult with deep analysis fields. */
internal fun KBRecord.toAnalysisResult(source: AnalysisSource): AnalysisResult {
    return AnalysisResult(
        ticketId = ticketId,
        context = mapKBContext(),
        evolution = mapKBEvolution(),
        complexity = mapKBComplexity(),
        source = source,
        technicalDetails = technicalDetails,
        acceptanceCriteria = acceptanceCriteria,
        dependencies = dependencies,
        analysisMetadata = analysisMetadata,
        diagrams = diagrams
    )
}

/** Convert AnalysisResult → KBRecord with deep analysis fields. */
internal fun AnalysisResult.toKBRecord(): KBRecord {
    return KBRecord(
        ticketId = ticketId,
        requirementSummary = context.unified,
        evolutionHistory = mapResultEvolution(),
        scrumPoints = complexity.scrumPoints,
        confidenceScore = 0.8,
        rationale = complexity.description,
        similarTicketRefs = complexity.kbReferences.map { it.ticketId },
        timestamp = currentTimeMillis().toString(),
        technicalDetails = technicalDetails,
        acceptanceCriteria = acceptanceCriteria,
        dependencies = dependencies,
        analysisMetadata = analysisMetadata,
        businessSummary = context.businessSummary,
        asIsState = context.asIsState,
        toBeState = context.toBeState,
        extractedRequirements = context.extractedRequirements,
        diagrams = diagrams
    )
}

private fun KBRecord.mapKBContext(): RequirementSummary {
    return RequirementSummary(
        unified = requirementSummary,
        businessSummary = businessSummary,
        asIsState = asIsState,
        toBeState = toBeState,
        extractedRequirements = extractedRequirements
    )
}

private fun KBRecord.mapKBEvolution(): List<EvolutionEntry> {
    return evolutionHistory.map { entry ->
        EvolutionEntry(
            version = entry.version,
            date = entry.date,
            description = entry.description,
            changeType = entry.changeType
        )
    }
}

private fun KBRecord.mapKBComplexity(): ComplexityAssessment {
    return ComplexityAssessment(
        scrumPoints = scrumPoints,
        description = rationale,
        kbReferences = similarTicketRefs.map { ref ->
            KBReference(ticketId = ref, similarityPercent = confidenceScore * 100)
        }
    )
}

private fun AnalysisResult.mapResultEvolution(): List<com.assistant.kb.EvolutionEntry> {
    return evolution.map { entry ->
        com.assistant.kb.EvolutionEntry(
            version = entry.version,
            date = entry.date,
            description = entry.description,
            changeType = entry.changeType
        )
    }
}
