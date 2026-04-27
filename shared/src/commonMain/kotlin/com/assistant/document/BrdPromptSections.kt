package com.assistant.document

import com.assistant.document.models.AttachmentChunkInfo
import com.assistant.document.models.GenerationContext
import com.assistant.kb.KBRecord

/**
 * StringBuilder extension functions for BRD prompt section building.
 * Follows the same pattern as PromptSectionTechnical.kt / PromptSectionTicketData.kt.
 *
 * Requirements: 2.1–2.8, 9.1–9.3, 10.1, 10.7
 */

/** Req 2.1: Role assignment for BRD generation (Carleton ITS template). */
fun StringBuilder.appendRole() {
    appendLine("=== ROLE ===")
    appendLine("You are a Senior Business Analyst with 15+ years of experience in enterprise software projects at FECredit (Vietnamese financial services).")
    appendLine("You follow the Carleton University ITS Business Requirements Document template.")
    appendLine("Your BRD must be detailed enough for developers to design and implement from.")
    appendLine("You excel at extracting actionable requirements from analysis data, identifying gaps, and marking assumptions for stakeholder confirmation.")
    appendLine()
}

/** Req 2.1, 2.3–2.6: Serialize GenerationContext fields into prompt. */
internal fun StringBuilder.appendContextData(context: GenerationContext) {
    appendLine("=== CONTEXT ===")
    appendMainTicketData(context.mainTicket)
    appendLinkedTicketsData(context.linkedTicketAnalyses)
    appendAttachmentData(context.attachmentChunks)
    appendSprintData(context)
    appendLine()
}

/** Serialize main ticket analysis fields. */
internal fun StringBuilder.appendMainTicketData(ticket: KBRecord) {
    appendLine("--- Main Ticket: ${ticket.ticketId} ---")
    appendLine("Business Summary: ${ticket.businessSummary}")
    appendLine("As-Is State: ${ticket.asIsState}")
    appendLine("To-Be State: ${ticket.toBeState}")
    appendRequirements(ticket)
    appendDependencies(ticket)
    appendAcceptanceCriteria(ticket)
    appendTechnicalDetails(ticket.technicalDetails)
    appendDiagrams(ticket.diagrams)
    if (ticket.requirementSummary.isNotBlank()) {
        appendLine("Requirement Summary: ${ticket.requirementSummary}")
    }
    appendLine()
}

/** Serialize extracted requirements from a KBRecord. */
private fun StringBuilder.appendRequirements(ticket: KBRecord) {
    if (ticket.extractedRequirements.isEmpty()) return
    appendLine("Extracted Requirements:")
    ticket.extractedRequirements.forEach { req ->
        appendLine("  - $req")
    }
}

/** Serialize dependency info from a KBRecord. */
private fun StringBuilder.appendDependencies(ticket: KBRecord) {
    val deps = ticket.dependencies
    if (deps.blockingIssues.isEmpty() && deps.relatedIssues.isEmpty()) return
    appendLine("Dependencies:")
    deps.blockingIssues.forEach { d ->
        appendLine("  - [BLOCKING] ${d.key}: ${d.summary} (risk: ${d.riskLevel})")
    }
    deps.relatedIssues.forEach { d ->
        appendLine("  - [RELATED] ${d.key}: ${d.summary}")
    }
    deps.externalDependencies.forEach { ext ->
        appendLine("  - [EXTERNAL] $ext")
    }
}

/** Serialize acceptance criteria from a KBRecord. */
private fun StringBuilder.appendAcceptanceCriteria(ticket: KBRecord) {
    if (ticket.acceptanceCriteria.isEmpty()) return
    appendLine("Acceptance Criteria:")
    ticket.acceptanceCriteria.forEach { ac ->
        appendLine("  - ${ac.id}: ${ac.description} (testability: ${ac.testabilityAssessment})")
    }
}

/** Req 1.2, 2.1: Serialize linked ticket analyses with expanded fields. */
internal fun StringBuilder.appendLinkedTicketsData(linked: List<KBRecord>) {
    if (linked.isEmpty()) return
    appendLine("--- Linked Tickets (${linked.size}) ---")
    linked.forEach { ticket ->
        appendLine("Ticket: ${ticket.ticketId}")
        appendLine("  Summary: ${ticket.businessSummary}")
        if (ticket.asIsState.isNotBlank()) appendLine("  As-Is State: ${ticket.asIsState}")
        if (ticket.toBeState.isNotBlank()) appendLine("  To-Be State: ${ticket.toBeState}")
        appendRequirements(ticket)
        appendDependencies(ticket)
        appendAcceptanceCriteria(ticket)
        appendTechnicalDetails(ticket.technicalDetails)
    }
}

/** Req 1.3: Serialize attachment chunk content. */
internal fun StringBuilder.appendAttachmentData(chunks: List<AttachmentChunkInfo>) {
    if (chunks.isEmpty()) return
    appendLine("--- Attachment Content ---")
    chunks.forEach { chunk ->
        appendLine("[${chunk.filename}]: ${chunk.content}")
    }
}

/** Serialize sprint metadata if available. */
internal fun StringBuilder.appendSprintData(context: GenerationContext) {
    val sprint = context.sprintMetadata ?: return
    if (sprint.sprintName.isBlank()) return
    appendLine("--- Sprint Metadata ---")
    appendLine("Sprint: ${sprint.sprintName} (${sprint.startDate} — ${sprint.endDate})")
}

/** Req 2.1: List all 7 BRD section headings with sub-sections and deep sub-sections. */
fun StringBuilder.appendBrdTemplate() {
    appendLine("=== TEMPLATE ===")
    val count = BrdPromptBuilder.BRD_SECTIONS.size
    appendLine("Generate the BRD with these $count sections (use ## headings):")
    BrdPromptBuilder.BRD_SECTIONS.forEachIndexed { i, section ->
        appendLine("${i + 1}. $section")
        appendBrdSubSections(section)
    }
    appendLine()
    appendDataMappingInstructions()
}

/** Append sub-section and deep sub-section instructions for a given BRD section. */
private fun StringBuilder.appendBrdSubSections(section: String) {
    val subs = BrdPromptBuilder.BRD_SUB_SECTIONS[section] ?: return
    subs.forEachIndexed { j, sub ->
        appendLine("   ${j + 1}. $sub")
        appendBrdDeepSubSections(sub)
    }
}

/** Append level-3 deep sub-section instructions for a given sub-section. */
private fun StringBuilder.appendBrdDeepSubSections(subSection: String) {
    val deepSubs = BrdPromptBuilder.BRD_DEEP_SUB_SECTIONS[subSection] ?: return
    deepSubs.forEachIndexed { k, deep ->
        appendLine("      ${k + 1}. $deep")
    }
}

/** Req 9.1, 9.2, 9.3: Anti-hallucination + source citation instructions. */
fun StringBuilder.appendInstructions() {
    appendLine("=== INSTRUCTIONS ===")
    appendAntiHallucinationRules()
    appendRequirementFormatRules()
    appendNfrCoverageRules()
    appendSectionCompletionRules()
    appendLine()
}

/** Anti-hallucination: use ONLY context data, cite sources. */
private fun StringBuilder.appendAntiHallucinationRules() {
    appendLine("- Use ONLY data from CONTEXT above. Do NOT fabricate or invent any information.")
    appendLine("- Cite sources as [Source: TICKET-ID] or [Source: filename.pdf]")
    appendLine("- Every claim must be traceable to a specific ticket or attachment.")
}

/** Requirement format: PREQ-NNN, priority, acceptance criteria. */
private fun StringBuilder.appendRequirementFormatRules() {
    appendLine("- Number all functional requirements as PREQ-NNN (e.g., PREQ-001, PREQ-002).")
    appendLine("- Each requirement MUST include: Priority (Must/Should/Could), Acceptance Criteria.")
    appendLine("- Acceptance Criteria must be testable — a QA engineer can verify pass/fail.")
}

/** NFR coverage: MUST cover ALL 8 categories. */
private fun StringBuilder.appendNfrCoverageRules() {
    appendLine("- Non-Functional Requirements MUST cover ALL 8 categories: Availability, Compatibility, Extensibility, Maintainability, Scalability, Security, Usability, Performance.")
    appendLine("- If data is limited for an NFR category, provide industry-standard recommendations for FECredit context.")
}

/** Section completion: NEVER leave empty, mark assumptions. Req 6.1–6.4. */
private fun StringBuilder.appendSectionCompletionRules() {
    appendLine("- NEVER leave a section empty. Provide analysis from available context.")
    appendLine("- Mark assumptions clearly with [ASSUMPTION] tag for stakeholder confirmation.")
    appendLine("- Trước khi đánh dấu một section là 'Insufficient data', hãy kiểm tra TẤT CẢ nguồn dữ liệu: main ticket analysis, linked ticket data, comments, attachment content, và technical details.")
    appendLine("- Chỉ đánh dấu 'Insufficient data' khi KHÔNG CÓ bất kỳ dữ liệu nào liên quan trong toàn bộ CONTEXT.")
    appendLine("- Nếu dữ liệu trực tiếp không có, hãy suy luận từ dữ liệu gián tiếp. Ví dụ: Revision History từ ticket metadata; Acronyms từ technical terms; Sign Off từ stakeholders trong dependencies.")
    appendLine("- Mỗi section PHẢI có ít nhất 3 dòng nội dung thực tế. Nếu dữ liệu hạn chế, phân tích và mở rộng từ dữ liệu có sẵn, đánh dấu phần suy luận bằng [INFERRED] tag.")
    appendLine("- Sử dụng comments từ linked tickets như nguồn dữ liệu bổ sung — comments chứa thảo luận về requirements, pain points, technical decisions, và stakeholder feedback.")
    appendLine("- \"⚠️ Insufficient data\" là ONLY a last resort khi absolutely no context exists.")
}

/** Req 2.7: Specify Markdown output format. */
fun StringBuilder.appendOutputFormat() {
    val count = BrdPromptBuilder.BRD_SECTIONS.size
    appendLine("=== OUTPUT FORMAT ===")
    appendLine("Markdown with ## headings for each of the $count sections.")
}
