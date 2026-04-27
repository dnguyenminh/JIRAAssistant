package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.ExtractionConfidence

/**
 * Helper functions for DeepAnalysisResponseParser:
 * - Strip markdown code fences from AI response
 * - Compute extraction_confidence from section count
 *
 * Requirements: 25.1, 25.6
 */
internal object ResponseParserHelpers {

    /**
     * Strip markdown code fences (```json ... ``` or ``` ... ```)
     * from the raw AI response, returning clean JSON.
     */
    fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        val startIdx = trimmed.indexOf("```")
        if (startIdx < 0) return trimmed
        return extractBetweenFences(trimmed, startIdx)
    }

    private fun extractBetweenFences(text: String, startIdx: Int): String {
        val afterFence = text.substring(startIdx + 3)
        val contentStart = afterFence.indexOf('\n')
        if (contentStart < 0) return afterFence.trim()
        val content = afterFence.substring(contentStart + 1)
        val endIdx = content.lastIndexOf("```")
        return if (endIdx >= 0) content.substring(0, endIdx).trim() else content.trim()
    }

    /**
     * Compute extraction_confidence based on how many sections
     * the AI response contains.
     *
     * HIGH: ≥4 sections, MEDIUM: 2-3 sections, LOW: 0-1 sections
     * Requirements: 25.6
     */
    fun computeConfidence(sectionCount: Int): ExtractionConfidence {
        return when {
            sectionCount >= 4 -> ExtractionConfidence.HIGH
            sectionCount >= 2 -> ExtractionConfidence.MEDIUM
            else -> ExtractionConfidence.LOW
        }
    }

    /**
     * Count non-empty top-level sections in the parsed response.
     * Sections: requirementSummary, evolution, complexity,
     * technicalDetails, acceptanceCriteria, dependencies
     */
    fun countSections(
        hasRequirementSummary: Boolean,
        hasEvolution: Boolean,
        hasComplexity: Boolean,
        hasTechnicalDetails: Boolean,
        hasAcceptanceCriteria: Boolean,
        hasDependencies: Boolean
    ): Int {
        return listOf(
            hasRequirementSummary,
            hasEvolution,
            hasComplexity,
            hasTechnicalDetails,
            hasAcceptanceCriteria,
            hasDependencies
        ).count { it }
    }
}
