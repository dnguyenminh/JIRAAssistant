package com.assistant.document

/**
 * Explicit data mapping instructions for FSD prompt generation.
 * Tells the AI exactly which context fields to use for each FSD section.
 *
 * Requirements: 3.1–3.9
 */

/** Append FSD data mapping instructions linking context fields to FSD sections. */
internal fun StringBuilder.appendFsdDataMappingInstructions() {
    appendLine("=== DATA MAPPING ===")
    appendIntroductionMapping()
    appendSystemOverviewMapping()
    appendFunctionalSpecsMapping()
    appendIntegrationMapping()
    appendDataMigrationMapping()
    appendLine()
}

/** Map context fields → Introduction section. */
private fun StringBuilder.appendIntroductionMapping() {
    appendLine("Section 'Introduction':")
    appendLine("  USE: businessSummary → Purpose and Project Scope")
    appendLine("  USE: dependencies → Risks and Assumptions")
    appendLine("  USE: dependencies.blockingIssues → Technical risks with mitigation")
    appendLine("  USE: dependencies.externalDependencies → External assumptions")
}

/** Map context fields → System/Solution Overview section. */
private fun StringBuilder.appendSystemOverviewMapping() {
    appendLine("Section 'System/Solution Overview':")
    appendLine("  USE: technicalDetails → Context diagram components")
    appendLine("  USE: externalIntegrations → System actors and dependencies")
    appendLine("  USE: toBeState → Target architecture description")
    appendLine("  RULE: Derive actors from API consumers and integrations")
}

/** Map context fields → Functional Specifications section. */
private fun StringBuilder.appendFunctionalSpecsMapping() {
    appendLine("Section 'Functional Specifications':")
    appendLine("  USE: extractedRequirements → Use cases (UC-NNN)")
    appendLine("  USE: acceptanceCriteria → Acceptance Criteria per use case")
    appendLine("  USE: apiSpecifications → API-driven use case flows")
    appendLine("  RULE: Each extractedRequirement becomes one UC-NNN entry")
}

/** Map context fields → Integration Requirements section. */
private fun StringBuilder.appendIntegrationMapping() {
    appendLine("Section 'Integration Requirements':")
    appendLine("  USE: apiSpecifications → Full API contracts (method, path, schema)")
    appendLine("  USE: externalIntegrations → Third-party integration specs")
    appendLine("  RULE: Each API spec becomes a complete contract with error codes")
}

/** Map context fields → Data Migration/Conversion section. */
private fun StringBuilder.appendDataMigrationMapping() {
    appendLine("Section 'Data Migration/Conversion Requirements':")
    appendLine("  USE: databaseChanges → Schema mappings (source → target)")
    appendLine("  USE: databaseChanges.columns → Field-level transformation rules")
    appendLine("  RULE: Include rollback strategy and volume estimates")
}
