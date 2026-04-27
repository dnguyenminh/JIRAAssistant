package com.assistant.document

/**
 * Explicit data mapping instructions for BRD prompt generation.
 * Tells the AI exactly which context fields to use for each BRD section.
 * Covers all 7 BRD sections (Carleton ITS template).
 *
 * Requirements: 2.1–2.8, 3.1–3.7
 */

/** Append data mapping instructions linking context fields to all 7 BRD sections. */
fun StringBuilder.appendDataMappingInstructions() {
    appendLine("=== DATA MAPPING ===")
    appendRevisionHistoryMapping()
    appendProjectOverviewMapping()
    appendAcronymsMapping()
    appendExistingProcessesMapping()
    appendProjectRequirementsMapping()
    appendSignOffMapping()
    appendAppendixMapping()
    appendLine()
}

/** Req 3.1: Map context fields → Revision History section. */
private fun StringBuilder.appendRevisionHistoryMapping() {
    appendLine("Section 'Revision History':")
    appendLine("  USE: ticketId → Document identifier")
    appendLine("  USE: generated timestamp → Revision date")
    appendLine("  USE: source ticket IDs → Scope of analysis")
    appendLine("  USE: sprintMetadata → Sprint context (nếu có)")
    appendLine("  RULE: Tạo bảng revision history với Version, Date, Author, Description")
}

/** Req 3.4: Map context fields → Project Overview section. */
private fun StringBuilder.appendProjectOverviewMapping() {
    appendLine("Section 'Project Overview':")
    appendLine("  USE: businessSummary → Business Justification and Objectives")
    appendLine("  USE: toBeState → Target state description and deliverables")
    appendLine("  USE: toBeState → In Scope (Deliverables) — target state deliverables")
    appendLine("  USE: linked tickets → Contributors and related initiatives")
    appendLine("  USE: dependencies.externalDependencies → Out of Scope boundaries")
    appendLine("  USE: dependencies.externalDependencies + technicalDetails → Out of Scope boundaries")
    appendLine("  USE: linked ticket assignees → Project Contributors")
}

/** Req 3.2: Map context fields → Acronyms section. */
private fun StringBuilder.appendAcronymsMapping() {
    appendLine("Section 'Common Project Acronyms, Names, and Descriptions':")
    appendLine("  USE: technicalDetails → Trích xuất thuật ngữ kỹ thuật (API names, protocols)")
    appendLine("  USE: extractedRequirements → Trích xuất viết tắt và tên hệ thống")
    appendLine("  USE: businessSummary → Trích xuất domain-specific terms")
    appendLine("  USE: attachment content → Trích xuất thuật ngữ từ documents")
    appendLine("  RULE: Tạo bảng Acronym, Full Name, Description cho mỗi thuật ngữ")
}

/** Req 3.5: Map context fields → Existing Processes section. */
private fun StringBuilder.appendExistingProcessesMapping() {
    appendLine("Section 'Existing Processes':")
    appendLine("  USE: asIsState → Expand into narrative with timing, volume, problems")
    appendLine("  USE: asIsState pain points → Problems sub-section")
    appendLine("  USE: dependencies.blockingIssues → Current system constraints")
    appendLine("  USE: rawComments → Thảo luận về current pain points cho Problems sub-section")
    appendLine("  USE: attachment content (screenshots, process docs) → Screenshots sub-section")
    appendLine("  RULE: Convert brief asIsState into detailed process narrative")
}

/** Req 3.6: Map context fields → Project Requirements section. */
private fun StringBuilder.appendProjectRequirementsMapping() {
    appendLine("Section 'Project Requirements':")
    appendLine("  USE: extractedRequirements → PREQ-NNN functional requirements")
    appendLine("  USE: acceptanceCriteria → Acceptance Criteria per requirement")
    appendLine("  USE: technicalDetails → Non-Functional Requirements (NFRs)")
    appendLine("  USE: dependencies → Known Issues/Assumptions/Risks/Dependencies")
    appendLine("  USE: technicalDetails.apiSpecifications → Functional Requirements (API-related)")
    appendLine("  USE: technicalDetails.databaseChanges → Data Requirements")
    appendLine("  USE: linked ticket acceptanceCriteria → Cross-ticket requirements consolidation")
    appendLine("  RULE: Each extractedRequirement becomes one PREQ-NNN entry")
}

/** Req 3.3: Map context fields → Sign Off section. */
private fun StringBuilder.appendSignOffMapping() {
    appendLine("Section 'Sign Off':")
    appendLine("  USE: dependencies.blockingIssues → Stakeholders cần approve")
    appendLine("  USE: linked ticket assignees → Project contributors cần sign off")
    appendLine("  USE: sprintMetadata → Timeline context")
    appendLine("  RULE: Tạo bảng Role, Name, Signature, Date cho mỗi stakeholder")
}

/** Req 3.7: Map context fields → Appendix section. */
private fun StringBuilder.appendAppendixMapping() {
    appendLine("Section 'Appendix':")
    appendLine("  USE: attachmentChunks → Document References with filenames")
    appendLine("  USE: technicalDetails.externalIntegrations → Integration references")
    appendLine("  USE: technicalDetails.apiSpecifications → Technical appendix items")
    appendLine("  USE: diagrams → Mock-ups section (Mermaid/draw.io diagrams)")
    appendLine("  USE: technicalDetails → Business Rules and Procedures")
    appendLine("  USE: all source ticket IDs → Document References")
}
