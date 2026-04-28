package com.assistant.server.agent.ba.subprocess.pipeline.aibackend

/**
 * Phase-specific prompt sections for PhasePromptBuilder.
 * Extracted for file size compliance (SRP).
 */

internal fun StringBuilder.appendKbMemoryProtocol(ticketId: String) {
    appendLine("## KB MEMORY PROTOCOL")
    appendLine()
    appendLine("When saving data to KB, use this naming convention:")
    appendLine("- Ticket data title: `[BRD:$ticketId] {childTicketId} - {summary}`")
    appendLine("- Collection summary title: `[BRD:$ticketId] Collection Summary`")
    appendLine("- Tags: `brd-pipeline,$ticketId`")
    appendLine()
    appendLine("This format allows Phase 2 and Phase 3 to find all related data.")
}

internal fun StringBuilder.appendKbDataRetrieval(ticketId: String) {
    appendLine("## KB DATA RETRIEVAL")
    appendLine()
    appendLine("Search KB for data collected in Phase 1:")
    appendLine("- Query: `\"BRD:$ticketId\"` to find all related ticket data")
    appendLine("- Read individual ticket entries for detailed information")
    appendLine("- Look for the Collection Summary for an overview of all data")
}

internal fun StringBuilder.appendDiagramPlaceholderInstructions() {
    appendLine("## DIAGRAM PLACEHOLDERS — MANDATORY")
    appendLine()
    appendLine("You MUST insert these EXACT placeholder markers in the BRD:")
    appendLine("- `<!-- DIAGRAM:PROCESS_FLOW -->` — at the END of \"## 4. Existing Processes\" section")
    appendLine("- `<!-- DIAGRAM:ACTIVITY -->` — after Process Overview in \"## 5. Project Requirements\"")
    appendLine("- `<!-- DIAGRAM:DATA_MODEL -->` — after Data Requirements in \"## 5. Project Requirements\"")
    appendLine("- `<!-- DIAGRAM:DEPLOYMENT -->` — at the END of \"## 7. Appendix\" section")
    appendLine()
    appendLine("⚠️ CRITICAL: If you do NOT insert these placeholders, diagrams will be LOST.")
    appendLine("Copy-paste the exact markers above. Do NOT modify the format.")
}

internal fun StringBuilder.appendPhase1Task(ticketId: String) {
    appendLine("## TASK — MANDATORY DATA COLLECTION")
    appendLine()
    appendLine("Collect ALL available data for ticket **$ticketId**. You MUST complete these steps:")
    appendLine()
    appendLine("**Step 1 — Main ticket:** Call `get_ticket_analysis` for $ticketId.")
    appendLine("**Step 2 — Linked tickets:** From the analysis, identify ALL linked ticket IDs.")
    appendLine("  Call `get_ticket_analysis` for EACH linked ticket (up to 10).")
    appendLine("**Step 3 — KB search:** Call `kb_search_knowledge` with different queries:")
    appendLine("  - Query the main ticket ID")
    appendLine("  - Query key terms from the ticket summary")
    appendLine("**Step 4 — Save to KB:** If `kb_ingest_knowledge` is available, save collected data.")
    appendLine("**Step 5 — Output:** Produce a DETAILED summary including ALL data found.")
    appendLine()
    appendLine("⚠️ CRITICAL RULES:")
    appendLine("- You MUST make at least 5 tool calls before producing final output.")
    appendLine("- Do NOT stop after just 1-2 tool calls — explore linked tickets!")
    appendLine("- Do NOT write a BRD document — only collect and summarize raw data.")
    appendLine("- Your output will be passed to Phase 2 for BRD writing.")
    appendLine("- Include: ticket summaries, requirements, dependencies, technical details.")
}

/** Inject Phase 1 collected data as fallback context for Phase 2/3. */
internal fun StringBuilder.appendPhase1DataContext(phase1Output: String) {
    appendLine("## COLLECTED DATA FROM PHASE 1")
    appendLine()
    appendLine("The following data was collected during Phase 1 (data collection).")
    appendLine("Use this data to write the BRD. If KB search returns no results,")
    appendLine("rely on this data instead.")
    appendLine()
    val maxLen = 12_000
    if (phase1Output.length > maxLen) {
        appendLine(phase1Output.take(maxLen))
        appendLine("... [truncated — use KB tools for full data]")
    } else {
        appendLine(phase1Output)
    }
}

internal fun StringBuilder.appendPhase2Task(ticketId: String) {
    appendLine("## TASK")
    appendLine()
    appendLine("Write a complete BRD for ticket: **$ticketId** using data from KB.")
    appendLine("The BRD MUST include ALL 7 sections with substantive content:")
    appendLine("1. Revision History")
    appendLine("2. Project Overview")
    appendLine("3. Common Project Acronyms, Names, and Descriptions")
    appendLine("4. Existing Processes")
    appendLine("5. Project Requirements")
    appendLine("6. Sign Off")
    appendLine("7. Appendix")
    appendLine()
    appendLine("NEVER write 'Insufficient data' or 'N/A' in any section.")
    appendLine()
    appendLine("**ATTACHMENT DATA:** If the collected data contains an `=== ATTACHMENTS ===`")
    appendLine("section, reference those files in the Appendix > Document References.")
    appendLine("List each filename and summarize its relevance to the requirements.")
    appendLine()
    appendLine("**DIAGRAM PLACEHOLDERS — MANDATORY:** You MUST insert these 4 markers:")
    appendLine("- `<!-- DIAGRAM:PROCESS_FLOW -->` at end of section 4")
    appendLine("- `<!-- DIAGRAM:ACTIVITY -->` after Process Overview in section 5")
    appendLine("- `<!-- DIAGRAM:DATA_MODEL -->` after Data Requirements in section 5")
    appendLine("- `<!-- DIAGRAM:DEPLOYMENT -->` at end of section 7")
    appendLine()
    appendLine("Output in Markdown format (NOT JSON).")
}

internal fun StringBuilder.appendPhase3Task(ticketId: String) {
    appendLine("## TASK")
    appendLine()
    appendLine("Generate draw.io XML diagrams for ticket: **$ticketId** using data from KB.")
    appendLine()
    appendLine("You MUST output EACH diagram with its label comment followed by the XML.")
    appendLine("Use EXACTLY these labels (one per diagram):")
    appendLine()
    appendLine("**Diagram 1 — Process Flow:**")
    appendLine("```")
    appendLine("<!-- DIAGRAM:PROCESS_FLOW -->")
    appendLine("<mxGraphModel>...AS-IS or TO-BE process...</mxGraphModel>")
    appendLine("```")
    appendLine()
    appendLine("**Diagram 2 — Activity:**")
    appendLine("```")
    appendLine("<!-- DIAGRAM:ACTIVITY -->")
    appendLine("<mxGraphModel>...activity/workflow steps...</mxGraphModel>")
    appendLine("```")
    appendLine()
    appendLine("**Diagram 3 — Data Model (optional):**")
    appendLine("```")
    appendLine("<!-- DIAGRAM:DATA_MODEL -->")
    appendLine("<mxGraphModel>...data entities...</mxGraphModel>")
    appendLine("```")
    appendLine()
    appendLine("**Diagram 4 — Deployment (optional):**")
    appendLine("```")
    appendLine("<!-- DIAGRAM:DEPLOYMENT -->")
    appendLine("<mxGraphModel>...system architecture...</mxGraphModel>")
    appendLine("```")
    appendLine()
    appendLine("⚠️ CRITICAL: Each `<mxGraphModel>` MUST be preceded by its label comment.")
    appendLine("Do NOT combine multiple diagrams under one label.")
}
