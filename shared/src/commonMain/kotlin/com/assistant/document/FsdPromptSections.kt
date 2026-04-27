package com.assistant.document

import com.assistant.document.models.GenerationContext
import com.assistant.kb.KBRecord

/**
 * StringBuilder extension functions for FSD prompt section building.
 * FSD-specific extensions: appendFsdRole, appendFsdTemplate,
 * appendFsdInstructions, appendFsdDiagramInstructions, appendFsdTechnicalExpansion.
 *
 * Reuses shared extensions from BrdPromptSections.kt for context serialization.
 *
 * Requirements: 3.1–3.9, 9.1–9.3, 10.2, 10.7
 */

/** Req 3.1: Role assignment for FSD generation (FECredit template). */
internal fun StringBuilder.appendFsdRole() {
    appendLine("=== ROLE ===")
    appendLine("You are a senior Technical Architect with 15+ years of experience in enterprise Java/Kotlin systems at FECredit (Vietnamese financial services).")
    appendLine("You follow the FECredit Functional Specification Document template.")
    appendLine("Your FSD must be detailed enough for developers to implement without ambiguity.")
    appendLine("You excel at translating business requirements into technical specifications, designing APIs, database schemas, and identifying integration points.")
    appendLine()
}

/** Req 3.1: List all FSD section headings with sub-sections (FECredit template). */
internal fun StringBuilder.appendFsdTemplate() {
    appendLine("=== TEMPLATE ===")
    val count = FsdPromptBuilder.FSD_SECTIONS.size
    appendLine("Generate the FSD with these $count sections (use ## headings):")
    FsdPromptBuilder.FSD_SECTIONS.forEachIndexed { i, section ->
        appendLine("${i + 1}. $section")
        appendFsdSubSections(section)
    }
    appendLine()
    appendFsdDataMappingInstructions()
}

/** Append sub-section instructions for a given FSD section. */
private fun StringBuilder.appendFsdSubSections(section: String) {
    val subs = FsdPromptBuilder.FSD_SUB_SECTIONS[section] ?: return
    subs.forEachIndexed { j, sub ->
        appendLine("   ${j + 1}. $sub")
    }
}

/** Req 9.1, 9.2, 9.3: Anti-hallucination + source citation + technical rules. */
internal fun StringBuilder.appendFsdInstructions() {
    appendLine("=== INSTRUCTIONS ===")
    appendFsdAntiHallucinationRules()
    appendFsdUseCaseFormatRules()
    appendFsdApiContractRules()
    appendFsdBrdTraceabilityRules()
    appendFsdSectionCompletionRules()
    appendLine()
}

/** Anti-hallucination: use ONLY context data, cite sources. */
private fun StringBuilder.appendFsdAntiHallucinationRules() {
    appendLine("- Use ONLY data from CONTEXT above. Do NOT fabricate or invent any information.")
    appendLine("- Cite sources as [Source: TICKET-ID] or [Source: filename.pdf]")
    appendLine("- Every technical decision must be traceable to a requirement or constraint.")
}

/** Use case format: UC-NNN with actors, flows, postconditions. */
private fun StringBuilder.appendFsdUseCaseFormatRules() {
    appendLine("- Number all use cases as UC-NNN (e.g., UC-001, UC-002).")
    appendLine("- Each use case MUST include: Primary Actor, Preconditions, Main Flow (numbered steps), Alternative Flows, Exception Flows, Postconditions.")
    appendLine("- Main Flow steps must be atomic and implementable.")
}

/** API contract format: method, path, schemas, error codes. */
private fun StringBuilder.appendFsdApiContractRules() {
    appendLine("- API contracts MUST include: HTTP Method, Path, Request Headers, Request Body Schema (with types), Response Body Schema (success + error), Error Codes.")
    appendLine("- A developer must be able to implement the endpoint from the spec alone.")
    appendLine("- Include authentication/authorization requirements per endpoint.")
}

/** BRD traceability: every spec references PREQ-NNN. */
private fun StringBuilder.appendFsdBrdTraceabilityRules() {
    appendLine("- Every functional specification MUST reference the BRD requirement it implements using [Implements: PREQ-NNN].")
    appendLine("- If no BRD exists yet, reference the source ticket as [Source: TICKET-ID].")
}

/** Section completion: NEVER leave empty, provide technical analysis. */
private fun StringBuilder.appendFsdSectionCompletionRules() {
    appendLine("- NEVER leave a section empty. Provide technical analysis and recommendations based on available context.")
    appendLine("- If data is limited, provide industry-standard technical recommendations for FECredit context.")
    appendLine("- \"⚠️ Insufficient data\" is ONLY a last resort when absolutely no context exists.")
}

/** Req 10.2, 10.7: Request 4 draw.io diagrams matching FECredit FSD template. */
internal fun StringBuilder.appendFsdDiagramInstructions() {
    appendLine("=== DIAGRAM INSTRUCTIONS ===")
    appendFsdDiagramRequirements()
    appendFsdDiagramFormat()
    appendFsdDiagramExample()
    appendLine()
}

/** List the 4 required FSD diagrams and their target sections. */
private fun StringBuilder.appendFsdDiagramRequirements() {
    appendLine("Embed 4 draw.io diagrams as raw XML in ```xml code blocks:")
    appendLine("1. Context/Interface Diagram → in section System/Solution Overview")
    appendLine("2. Data Flow Diagram → in section System/Solution Overview > Data Flow")
    appendLine("3. Integration Architecture → in section Integration Requirements")
    appendLine("4. Data Migration Flow → in section Data Migration/Conversion Requirements")
}

/** Specify the exact XML format and labeling rules. */
private fun StringBuilder.appendFsdDiagramFormat() {
    appendLine("Format: each diagram MUST be a ```xml code block containing <mxGraphModel>.")
    appendLine("Use actual ticket IDs, service names, and data from CONTEXT as node labels.")
    appendLine("Do NOT use placeholder or generic text. Do NOT output JSON metadata.")
}

/** Provide a compact draw.io XML example for FSD diagrams. */
private fun StringBuilder.appendFsdDiagramExample() {
    appendLine("Example (embed inline in the relevant section):")
    appendLine("```xml")
    appendLine("""<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>""")
    appendLine("""<mxCell id="2" value="Frontend" style="rounded=1;" vertex="1" parent="1">""")
    appendLine("""<mxGeometry x="50" y="50" width="120" height="60" as="geometry"/></mxCell>""")
    appendLine("""<mxCell id="3" value="API" style="rounded=1;" vertex="1" parent="1">""")
    appendLine("""<mxGeometry x="250" y="50" width="120" height="60" as="geometry"/></mxCell>""")
    appendLine("""<mxCell id="4" value="REST" edge="1" source="2" target="3" parent="1"/>""")
    appendLine("</root></mxGraphModel>")
    appendLine("```")
}

/** Req 3.3–3.5: Expand FSD-specific technical details from context. */
internal fun StringBuilder.appendFsdTechnicalExpansion(context: GenerationContext) {
    appendApiSpecifications(context.mainTicket)
    appendDatabaseChanges(context.mainTicket)
    appendExternalIntegrations(context.mainTicket)
}

/** Req 3.3: Expand apiSpecifications for Functional Specifications section. */
private fun StringBuilder.appendApiSpecifications(ticket: KBRecord) {
    val specs = ticket.technicalDetails.apiSpecifications
    if (specs.isEmpty()) return
    appendLine("--- API Specifications (expand into Functional Specifications section) ---")
    specs.forEach { api ->
        appendLine("  ${api.method} ${api.path}: ${api.description}")
    }
    appendLine()
}

/** Req 3.4: Expand databaseChanges for Data Migration/Conversion section. */
private fun StringBuilder.appendDatabaseChanges(ticket: KBRecord) {
    val changes = ticket.technicalDetails.databaseChanges
    if (changes.isEmpty()) return
    appendLine("--- Database Changes (expand into Data Migration/Conversion section) ---")
    changes.forEach { db ->
        appendLine("  ${db.operationType} ${db.tableName}: ${db.description} (columns: ${db.columns.joinToString()})")
    }
    appendLine()
}

/** Req 3.5: Expand externalIntegrations for Integration Requirements section. */
private fun StringBuilder.appendExternalIntegrations(ticket: KBRecord) {
    val integrations = ticket.technicalDetails.externalIntegrations
    if (integrations.isEmpty()) return
    appendLine("--- External Integrations (expand into Integration Requirements section) ---")
    integrations.forEach { ext ->
        appendLine("  ${ext.serviceName} (${ext.protocol}): ${ext.endpoint} — ${ext.description}")
    }
    appendLine()
}

/** Req 3.8: Specify Markdown output format for FSD. */
internal fun StringBuilder.appendFsdOutputFormat() {
    val count = FsdPromptBuilder.FSD_SECTIONS.size
    appendLine("=== OUTPUT FORMAT ===")
    appendLine("Markdown with ## headings for each of the $count sections.")
}
