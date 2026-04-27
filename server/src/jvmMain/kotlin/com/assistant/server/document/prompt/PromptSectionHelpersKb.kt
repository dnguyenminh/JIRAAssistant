package com.assistant.server.document.prompt

import com.assistant.kb.KBRecord

/**
 * KB-specific helper extensions for [PromptSectionBuilder].
 *
 * Serializes dependencies, technicalDetails, and diagrams from KBRecord
 * into the enriched prompt path. Separated from [PromptSectionHelpers]
 * to respect the 200-line file limit.
 *
 * Requirements: 4.1
 */

/** Serialize dependencies (blocking, related, external) from KBRecord. */
internal fun StringBuilder.appendKbDependencies(kb: KBRecord) {
    val deps = kb.dependencies
    if (deps.blockingIssues.isEmpty() &&
        deps.relatedIssues.isEmpty() &&
        deps.externalDependencies.isEmpty()
    ) return
    appendLine("Dependencies:")
    deps.blockingIssues.forEach {
        appendLine("  - [BLOCKING] ${it.key}: ${it.summary}")
    }
    deps.relatedIssues.forEach {
        appendLine("  - [RELATED] ${it.key}: ${it.summary}")
    }
    deps.externalDependencies.forEach {
        appendLine("  - [EXTERNAL] $it")
    }
}

/** Serialize technicalDetails (API specs, DB changes, integrations). */
internal fun StringBuilder.appendKbTechnicalDetails(kb: KBRecord) {
    val td = kb.technicalDetails
    if (td.apiSpecifications.isEmpty() &&
        td.databaseChanges.isEmpty() &&
        td.externalIntegrations.isEmpty()
    ) return
    appendLine("Technical Details:")
    td.apiSpecifications.forEach {
        appendLine("  - API: ${it.method} ${it.path} — ${it.description}")
    }
    td.databaseChanges.forEach {
        appendLine("  - DB: ${it.operationType} ${it.tableName} — ${it.description}")
    }
    td.externalIntegrations.forEach {
        appendLine("  - Integration: ${it.serviceName} (${it.protocol}) — ${it.description}")
    }
}

/** Serialize diagrams from KBRecord. */
internal fun StringBuilder.appendKbDiagrams(kb: KBRecord) {
    if (kb.diagrams.isEmpty()) return
    appendLine("Diagrams:")
    kb.diagrams.forEach {
        appendLine("  - [${it.type}] ${it.title}")
    }
}
