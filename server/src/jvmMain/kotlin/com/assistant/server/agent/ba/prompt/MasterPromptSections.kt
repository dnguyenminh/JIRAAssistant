package com.assistant.server.agent.ba.prompt

import com.assistant.agent.memory.MemoryEntry
import com.assistant.agent.memory.StructuredMemory
import com.assistant.document.BrdPromptBuilder
import com.assistant.document.appendDataMappingInstructions
/**
 * Section builders for the Master Prompt.
 * Each function produces one section with proper headers
 * and source attribution markers.
 *
 * When ticketClassifications exist in memory, buildContext()
 * delegates to MasterPromptContextBuilder for AS-IS/TO-BE/OUTDATED
 * section separation (Req 3.1-3.5).
 */
internal object MasterPromptSections {

    fun buildRoleInstruction(docType: String): String {
        val role = when (docType) {
            "BRD" -> "You are a Senior Business Analyst with 15+ years of experience in enterprise software projects at FECredit (Vietnamese financial services). " +
                "You follow the Carleton University ITS Business Requirements Document template. " +
                "Your BRD must be detailed enough for developers to design and implement from. " +
                "You excel at extracting actionable requirements from analysis data, identifying gaps, and marking assumptions for stakeholder confirmation."
            "FSD" -> "You are a senior Technical Architect"
            "SLIDES" -> "You are a senior Product Owner"
            else -> "You are a senior Business Analyst"
        }
        return "## ROLE INSTRUCTION\n$role"
    }

    fun buildContext(memory: StructuredMemory): String =
        buildClassifiedContext(memory)

    fun buildTemplateStructure(docType: String): String {
        val template = when (docType) {
            "BRD" -> buildBrdTemplate()
            "FSD" -> FSD_TEMPLATE
            "SLIDES" -> SLIDES_TEMPLATE
            else -> buildBrdTemplate()
        }
        return "## TEMPLATE STRUCTURE\n$template"
    }

    fun buildOutputFormat(): String =
        "## OUTPUT FORMAT\n" +
            "Use Markdown formatting with ## headings for each of the 7 BRD sections.\n" +
            "Use bullet points, tables, and sub-headings where appropriate.\n" +
            "NEVER leave a section empty. Provide analysis from available context.\n" +
            "Mark assumptions clearly with [ASSUMPTION] tag.\n" +
            "Cite sources as [Source: TICKET-ID] or [Source: filename.pdf].\n" +
            "Before marking any section as 'Insufficient data', check ALL data sources: main ticket, linked tickets, comments, attachments, and technical details.\n" +
            "Each section MUST have at least 3 lines of real content. If data is limited, infer from indirect data and mark with [INFERRED] tag.\n" +
            "Number all functional requirements as PREQ-NNN with Priority and Acceptance Criteria."

    fun buildDiagramInstructions(): String =
        "## DIAGRAM INSTRUCTIONS\n" +
            "Include Mermaid diagrams where applicable."

    private fun buildClassifiedContext(
        memory: StructuredMemory
    ): String {
        val kbSources = kbSourceIds(memory)
        val sb = StringBuilder("## CONTEXT\n")
        sb.append(MasterPromptContextBuilder.buildToBeSection(memory, kbSources))
        sb.append("\n\n")
        val asIs = MasterPromptContextBuilder.buildAsIsSection(memory, kbSources)
        if (asIs.isNotEmpty()) {
            sb.append(asIs)
            sb.append("\n\n")
        }
        val outdated = MasterPromptContextBuilder.buildOutdatedMetadata(memory)
        if (outdated.isNotEmpty()) {
            sb.append(outdated)
        }
        return sb.toString().trimEnd()
    }

}

/**
 * Appends entries from a single memory slot with
 * KB-first logic and source attribution.
 */
internal fun appendSlotEntries(
    sb: StringBuilder,
    memory: StructuredMemory,
    slotName: String
) {
    val entries = memory.getSlot(slotName)
    if (entries.isEmpty()) return
    val kbSources = kbSourceIds(memory)
    for (entry in entries) {
        if (shouldSkipRawEntry(slotName, entry, kbSources)) continue
        sb.append(entry.data)
        sb.append(" [Source: ${entry.source}/$slotName]\n")
    }
}

/** Ticket IDs that have KB records stored. */
internal fun kbSourceIds(memory: StructuredMemory): Set<String> =
    memory.getSlot("kbRecords").map { it.source }.toSet()

/**
 * Skip raw description/comments for tickets that have KB records.
 * KB records themselves are never skipped.
 */
internal fun shouldSkipRawEntry(
    slotName: String,
    entry: MemoryEntry,
    kbSources: Set<String>
): Boolean {
    if (slotName == "kbRecords") return false
    val rawSlots = setOf("description", "comments")
    return slotName in rawSlots && entry.source in kbSources
}

private const val FSD_TEMPLATE =
    "1. Overview\n2. Architecture\n" +
        "3. Data Model\n4. API Specifications\n" +
        "5. Non-Functional Requirements\n6. Appendix"

private const val SLIDES_TEMPLATE =
    "1. Title Slide\n2. Problem Statement\n" +
        "3. Proposed Solution\n4. Key Benefits\n" +
        "5. Timeline\n6. Next Steps\n7. Q&A"

/**
 * Build BRD template from BrdPromptBuilder.BRD_SECTIONS (single source of truth).
 * Includes sub-sections and deep sub-sections for detailed instructions.
 */
private fun buildBrdTemplate(): String = buildString {
    val sections = BrdPromptBuilder.BRD_SECTIONS
    appendLine("Generate the BRD with these ${sections.size} sections (use ## headings):")
    sections.forEachIndexed { i, section ->
        appendLine("${i + 1}. $section")
        BrdPromptBuilder.BRD_SUB_SECTIONS[section]?.forEachIndexed { j, sub ->
            appendLine("   ${j + 1}. $sub")
            BrdPromptBuilder.BRD_DEEP_SUB_SECTIONS[sub]?.forEachIndexed { k, deep ->
                appendLine("      ${k + 1}. $deep")
            }
        }
    }
    appendLine()
    appendDataMappingInstructions()
}
