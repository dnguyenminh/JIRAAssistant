package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.ApiSpecification
import com.assistant.ai.deepanalysis.models.DatabaseChange
import com.assistant.ai.deepanalysis.models.ExternalIntegration

/**
 * Extraction functions for individual section types.
 * Each function extracts structured data from raw description text.
 * Requirements: 17.1-17.5
 */
internal object SectionExtractors {

    /** Req 17.1 — Extract As-Is state text under heading. */
    fun extractAsIsState(description: String): String {
        return extractSectionUnderHeading(
            description,
            SectionPatterns.AS_IS_HEADING
        )
    }

    /** Req 17.1 — Extract To-Be state text under heading. */
    fun extractToBeState(description: String): String {
        return extractSectionUnderHeading(
            description,
            SectionPatterns.TO_BE_HEADING
        )
    }

    /** Req 17.2 — Extract API specifications from description. */
    fun extractApiSpecifications(description: String): List<ApiSpecification> {
        val apis = mutableListOf<ApiSpecification>()
        val sectionText = extractSectionUnderHeading(
            description, SectionPatterns.API_HEADING
        )
        val textToScan = sectionText.ifEmpty { description }
        addApisFromText(textToScan, apis)
        return apis.distinctBy { "${it.method} ${it.path}" }
    }

    /** Req 17.3 — Extract database changes from description. */
    fun extractDatabaseChanges(description: String): List<DatabaseChange> {
        val changes = mutableListOf<DatabaseChange>()
        val sectionText = extractSectionUnderHeading(
            description, SectionPatterns.DB_HEADING
        )
        val textToScan = sectionText.ifEmpty { description }
        addDbChangesFromText(textToScan, changes)
        return changes.distinctBy { "${it.operationType} ${it.tableName}" }
    }

    /** Req 17.4 — Extract external dependencies from description. */
    fun extractExternalDependencies(
        description: String
    ): List<ExternalIntegration> {
        val deps = mutableListOf<ExternalIntegration>()
        val sectionText = extractSectionUnderHeading(
            description, SectionPatterns.INTEGRATION_HEADING
        )
        val textToScan = sectionText.ifEmpty { description }
        addExternalDepsFromText(textToScan, deps)
        return deps.distinctBy { it.endpoint.ifEmpty { it.serviceName } }
    }

    /** Req 17.5 — Extract acceptance criteria from description. */
    fun extractAcceptanceCriteria(description: String): List<String> {
        val criteria = mutableListOf<String>()
        val sectionText = extractSectionUnderHeading(
            description, SectionPatterns.AC_HEADING
        )
        if (sectionText.isNotEmpty()) {
            addCriteriaFromSection(sectionText, criteria)
        } else {
            addCriteriaFromPatterns(description, criteria)
        }
        return criteria.distinct()
    }
}
