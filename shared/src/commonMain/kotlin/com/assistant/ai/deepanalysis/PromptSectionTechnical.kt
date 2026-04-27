package com.assistant.ai.deepanalysis

import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Prompt section builders for technical data (API specs, DB changes)
 * and analysis instructions with JSON output schema.
 *
 * Requirements: 18.2-18.6
 */

/** Req 18.3: Include API specs and request completeness assessment. */
internal fun StringBuilder.appendApiSection(
    content: StructuredTicketContent
) {
    val apis = content.classifiedContent.apiSpecifications
    if (apis.isEmpty()) return
    appendLine("=== API SPECIFICATIONS ===")
    apis.forEach { api ->
        appendLine("- ${api.method} ${api.path}: ${api.description}")
    }
    appendLine(">> Assess completeness of these API specifications.")
    appendLine()
}

/** Req 18.4: Include DB schema and request data model assessment. */
internal fun StringBuilder.appendDatabaseSection(
    content: StructuredTicketContent
) {
    val dbChanges = content.classifiedContent.databaseChanges
    if (dbChanges.isEmpty()) return
    appendLine("=== DATABASE CHANGES ===")
    dbChanges.forEach { db ->
        val cols = if (db.columns.isNotEmpty()) {
            " columns: ${db.columns.joinToString(", ")}"
        } else ""
        appendLine("- ${db.operationType} ${db.tableName}$cols: ${db.description}")
    }
    appendLine(">> Assess the data model design and potential impacts.")
    appendLine()
}

/** Req 18.2: Request analysis across 6 aspects. */
internal fun StringBuilder.appendAnalysisInstructions() {
    appendLine("=== ANALYSIS INSTRUCTIONS ===")
    appendLine("Analyze this ticket across these 6 aspects:")
    appendLine("(a) Business requirement summary")
    appendLine("(b) Acceptance criteria")
    appendLine("(c) Technical details (API, DB, integrations)")
    appendLine("(d) Dependencies and risks")
    appendLine("(e) Change history with impact analysis")
    appendLine("(f) Complexity assessment with detailed rationale")
    appendLine()
}

/** Req 18.5: Strict JSON output schema. */
internal fun StringBuilder.appendJsonOutputSchema() {
    appendLine("=== OUTPUT FORMAT ===")
    appendLine("Return ONLY valid JSON matching this schema:")
    appendLine(JSON_OUTPUT_SCHEMA)
    appendLine()
}

/** Req 18.6: Anti-hallucination instruction. */
internal fun StringBuilder.appendAntiHallucinationInstruction() {
    appendLine("=== CRITICAL INSTRUCTION ===")
    append("Analyze ONLY based on the actual ticket data provided above. ")
    append("Do NOT fabricate, invent, or assume any information ")
    appendLine("not present in the ticket content.")
    append("If a section has no relevant data, return empty strings ")
    appendLine("or empty arrays for that field.")
}
