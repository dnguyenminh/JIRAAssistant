package com.assistant.document

import com.assistant.ai.deepanalysis.models.ApiSpecification
import com.assistant.ai.deepanalysis.models.DatabaseChange
import com.assistant.ai.deepanalysis.models.DiagramData
import com.assistant.ai.deepanalysis.models.ExternalIntegration
import com.assistant.ai.deepanalysis.models.TechnicalDetails

/**
 * StringBuilder extension functions for serializing TechnicalDetails and DiagramData
 * into BRD prompt context. Extracted from BrdPromptSections to respect ≤200 line limit.
 *
 * Requirements: 1.2, 1.3, 1.4, 1.5
 */

/** Serialize all technicalDetails fields from KBRecord. */
internal fun StringBuilder.appendTechnicalDetails(details: TechnicalDetails) {
    appendApiSpecifications(details.apiSpecifications)
    appendDatabaseChanges(details.databaseChanges)
    appendExternalIntegrations(details.externalIntegrations)
}

/** Serialize API specifications. */
private fun StringBuilder.appendApiSpecifications(specs: List<ApiSpecification>) {
    if (specs.isEmpty()) return
    appendLine("API Specifications:")
    specs.forEach { spec ->
        appendLine("  - ${spec.method} ${spec.path}: ${spec.description}")
    }
}

/** Serialize database changes. */
private fun StringBuilder.appendDatabaseChanges(changes: List<DatabaseChange>) {
    if (changes.isEmpty()) return
    appendLine("Database Changes:")
    changes.forEach { change ->
        appendLine("  - ${change.operationType} ${change.tableName}: ${change.description}")
    }
}

/** Serialize external integrations. */
private fun StringBuilder.appendExternalIntegrations(integrations: List<ExternalIntegration>) {
    if (integrations.isEmpty()) return
    appendLine("External Integrations:")
    integrations.forEach { integration ->
        appendLine("  - ${integration.serviceName} (${integration.protocol}): ${integration.description}")
    }
}

/** Serialize diagrams from KBRecord. */
internal fun StringBuilder.appendDiagrams(diagrams: List<DiagramData>) {
    if (diagrams.isEmpty()) return
    appendLine("Diagrams:")
    diagrams.forEach { diagram ->
        appendLine("  - [${diagram.type}] ${diagram.title}")
    }
}
