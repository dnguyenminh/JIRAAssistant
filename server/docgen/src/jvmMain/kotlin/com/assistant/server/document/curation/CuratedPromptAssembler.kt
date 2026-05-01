package com.assistant.server.document.curation

import com.assistant.document.appendBrdTemplate
import com.assistant.document.appendDiagramInstructions
import com.assistant.document.appendInstructions
import com.assistant.document.appendOutputFormat
import com.assistant.document.appendRole
import com.assistant.server.document.curation.models.CuratedContext

/**
 * Assembles the final prompt from [CuratedContext] sections.
 *
 * Order: Role → TO-BE → AS-IS → OUTDATED metadata →
 * Comments → Attachments → MCP tools → Template + Instructions +
 * Diagram Instructions + Output Format.
 *
 * Uses the same BRD template, instructions, diagram instructions,
 * and output format as the legacy BrdPromptBuilder to ensure
 * Gemini produces a complete 7-section BRD document.
 *
 * Requirements: 3.5, 8.4
 */
object CuratedPromptAssembler {

    fun buildPrompt(
        curatedContext: CuratedContext,
        docType: String,
        mcpToolBlock: String? = null
    ): String = buildString {
        // Role + template from shared BrdPromptSections
        if (docType.equals("BRD", ignoreCase = true)) {
            appendRole()
        } else {
            appendLine("=== ROLE ===")
            appendLine("You are a Senior Technical Architect writing an FSD.")
            appendLine()
        }
        appendToBeSection(this, curatedContext)
        appendAsIsSection(this, curatedContext)
        appendOutdatedMetadata(this, curatedContext)
        appendCommentSummaries(this, curatedContext)
        appendAttachments(this, curatedContext)
        if (!mcpToolBlock.isNullOrBlank()) appendLine(mcpToolBlock)
        // Template structure + instructions from shared module
        if (docType.equals("BRD", ignoreCase = true)) {
            appendBrdTemplate()
            appendInstructions()
            appendDiagramInstructions()
            appendOutputFormat()
        } else {
            appendLine("=== INSTRUCTIONS ===")
            appendLine("Generate a complete FSD document based on the context above.")
        }
    }

    private fun appendToBeSection(sb: StringBuilder, ctx: CuratedContext) {
        sb.appendLine("=== TO-BE REQUIREMENTS (New) ===")
        sb.appendLine()
        sb.appendLine("Root ticket requirements:")
        ctx.toBeSection.rootRequirements.forEach { sb.appendLine("- $it") }
        sb.appendLine()
        if (ctx.toBeSection.linkedRequirements.isNotEmpty()) {
            sb.appendLine("Linked new/concurrent requirements:")
            ctx.toBeSection.linkedRequirements.forEach { ticket ->
                sb.appendLine("[${ticket.ticketId}] ${ticket.businessSummary}")
                ticket.extractedRequirements.forEach { sb.appendLine("  - $it") }
            }
        }
        sb.appendLine()
    }

    private fun appendAsIsSection(sb: StringBuilder, ctx: CuratedContext) {
        if (ctx.asIsSection.existingFunctionality.isEmpty()) return
        sb.appendLine("=== AS-IS CONTEXT (Existing) ===")
        sb.appendLine()
        ctx.asIsSection.existingFunctionality.forEach { ticket ->
            sb.appendLine("[${ticket.ticketId}] ${ticket.businessSummary}")
            if (ticket.asIsState.isNotBlank()) {
                sb.appendLine("  Current state: ${ticket.asIsState}")
            }
        }
        sb.appendLine()
    }

    private fun appendOutdatedMetadata(sb: StringBuilder, ctx: CuratedContext) {
        if (ctx.outdatedMetadata.isEmpty()) return
        sb.appendLine("=== OUTDATED REFERENCES ===")
        ctx.outdatedMetadata.forEach { ref ->
            sb.appendLine(
                "- ${ref.ticketId} (superseded by ${ref.supersededBy}): " +
                    ref.oneLinerSummary
            )
        }
        sb.appendLine()
    }

    private fun appendCommentSummaries(sb: StringBuilder, ctx: CuratedContext) {
        if (ctx.commentSummaries.isEmpty()) return
        sb.appendLine("=== DISCUSSION SUMMARIES ===")
        ctx.commentSummaries.forEach { (ticketId, summary) ->
            if (summary.totalChars > 0) {
                sb.appendLine("[$ticketId]")
                summary.decisions.forEach { sb.appendLine("  Decision: $it") }
                summary.blockers.forEach { sb.appendLine("  Blocker: $it") }
                summary.recentComments.forEach {
                    sb.appendLine("  ${it.author}: ${it.body.take(200)}")
                }
                if (summary.botSummary != null) {
                    sb.appendLine("  (${summary.botSummary})")
                }
            }
        }
        sb.appendLine()
    }

    private fun appendAttachments(sb: StringBuilder, ctx: CuratedContext) {
        if (ctx.attachments.isEmpty()) return
        sb.appendLine("=== ATTACHMENT PREVIEWS ===")
        ctx.attachments.forEach { att ->
            sb.appendLine("[${att.filename}] (${att.ticketId})")
            sb.appendLine(att.preview)
            sb.appendLine()
        }
    }

}
