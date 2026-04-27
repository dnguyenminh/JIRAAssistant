package com.assistant.server.chat

import com.assistant.ai.deepanalysis.models.TechnicalDetails
import com.assistant.kb.KBRecord

/**
 * Builds deep analysis context for AI chat prompts from KBRecord data.
 * Requirements: 24.1-24.4
 */
object ChatDeepAnalysisContext {

    /** Check if a KBRecord has deep analysis data available. */
    fun hasDeepAnalysis(record: KBRecord): Boolean =
        record.extractedRequirements.isNotEmpty() ||
            record.technicalDetails.hasContent() ||
            record.businessSummary.isNotBlank()

    /** Build full deep analysis context string for a single KBRecord. Req: 24.1 */
    fun buildContext(record: KBRecord): String {
        if (!hasDeepAnalysis(record)) return buildSuggestAnalyzeHint(record.ticketId)
        val parts = mutableListOf<String>()
        parts.add("[Deep Analysis: ${record.ticketId}]")
        appendBusinessContext(parts, record)
        appendRequirements(parts, record)
        appendTechnicalDetails(parts, record)
        appendAcceptanceCriteria(parts, record)
        appendDependencies(parts, record)
        return parts.joinToString("\n")
    }

    /** Suggest "Analyze Ticket" when no deep analysis data exists. Req: 24.4 */
    fun buildSuggestAnalyzeHint(ticketId: String): String =
        "[$ticketId] No deep analysis available. Suggest action: \"Analyze Ticket\" to get detailed insights."

    /** Append business summary, As-Is/To-Be state. Req: 24.1 */
    private fun appendBusinessContext(parts: MutableList<String>, r: KBRecord) {
        if (r.businessSummary.isNotBlank()) parts.add("Business Summary: ${r.businessSummary}")
        if (r.asIsState.isNotBlank()) parts.add("As-Is State: ${r.asIsState}")
        if (r.toBeState.isNotBlank()) parts.add("To-Be State: ${r.toBeState}")
    }

    /** Append extracted requirements list. Req: 24.2 */
    private fun appendRequirements(parts: MutableList<String>, r: KBRecord) {
        if (r.extractedRequirements.isEmpty()) return
        parts.add("Extracted Requirements:")
        r.extractedRequirements.forEachIndexed { i, req ->
            parts.add("  ${i + 1}. $req")
        }
    }

    /** Append technical details (API specs, DB changes, integrations). Req: 24.3 */
    private fun appendTechnicalDetails(parts: MutableList<String>, r: KBRecord) {
        val td = r.technicalDetails
        if (!td.hasContent()) return
        parts.add("Technical Details:")
        appendApiSpecs(parts, td)
        appendDbChanges(parts, td)
        appendIntegrations(parts, td)
    }

    private fun appendApiSpecs(parts: MutableList<String>, td: TechnicalDetails) {
        if (td.apiSpecifications.isEmpty()) return
        parts.add("  API Specifications:")
        td.apiSpecifications.forEach { api ->
            parts.add("    - ${api.method} ${api.path}: ${api.description}")
        }
    }

    private fun appendDbChanges(parts: MutableList<String>, td: TechnicalDetails) {
        if (td.databaseChanges.isEmpty()) return
        parts.add("  Database Changes:")
        td.databaseChanges.forEach { db ->
            parts.add("    - ${db.operationType} ${db.tableName}: ${db.description}")
        }
    }

    private fun appendIntegrations(parts: MutableList<String>, td: TechnicalDetails) {
        if (td.externalIntegrations.isEmpty()) return
        parts.add("  External Integrations:")
        td.externalIntegrations.forEach { ext ->
            parts.add("    - ${ext.serviceName} (${ext.protocol}): ${ext.description}")
        }
    }

    /** Append acceptance criteria. Req: 24.1 */
    private fun appendAcceptanceCriteria(parts: MutableList<String>, r: KBRecord) {
        if (r.acceptanceCriteria.isEmpty()) return
        parts.add("Acceptance Criteria:")
        r.acceptanceCriteria.forEach { ac ->
            parts.add("  - [${ac.id}] ${ac.description} (Testability: ${ac.testabilityAssessment})")
        }
    }

    /** Append dependency info. Req: 24.1 */
    private fun appendDependencies(parts: MutableList<String>, r: KBRecord) {
        val deps = r.dependencies
        val hasContent = deps.blockingIssues.isNotEmpty() ||
            deps.relatedIssues.isNotEmpty() ||
            deps.externalDependencies.isNotEmpty()
        if (!hasContent) return
        parts.add("Dependencies:")
        deps.blockingIssues.forEach { parts.add("  - BLOCKING: ${it.key} — ${it.summary}") }
        deps.relatedIssues.forEach { parts.add("  - RELATED: ${it.key} — ${it.summary}") }
        deps.externalDependencies.forEach { parts.add("  - EXTERNAL: $it") }
    }

    /** Extension to check if TechnicalDetails has any content. */
    private fun TechnicalDetails.hasContent(): Boolean =
        apiSpecifications.isNotEmpty() ||
            databaseChanges.isNotEmpty() ||
            externalIntegrations.isNotEmpty()
}
