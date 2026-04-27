package com.assistant.server.document.curation

import com.assistant.server.document.curation.models.AsIsSection
import com.assistant.server.document.curation.models.BudgetResult
import com.assistant.server.document.curation.models.ClassifiedTicketData
import com.assistant.server.document.curation.models.CuratedContext

/**
 * Default implementation of [BudgetEnforcer].
 *
 * Progressive truncation order (lowest priority first):
 * 1. Deeper ticket details (depth ≥ 3)
 * 2. AS-IS details → summaries only
 * 3. Attachment previews (reduce size)
 * 4. Comment summaries → decisions only
 *
 * Never truncate: root ticket KB record, TO-BE section, prompt skeleton.
 *
 * Requirements: 6.1, 6.3, 6.4, 6.5
 */
class DefaultBudgetEnforcer : BudgetEnforcer {

    override fun enforce(
        context: CuratedContext,
        maxChars: Int
    ): BudgetResult {
        val originalSize = estimateSize(context)
        if (originalSize <= maxChars) {
            return BudgetResult(
                context = context,
                truncationApplied = false,
                originalSize = originalSize,
                finalSize = originalSize
            )
        }
        val truncated = applyProgressiveTruncation(context, maxChars)
        val finalSize = estimateSize(truncated.first)
        return BudgetResult(
            context = truncated.first,
            truncationApplied = true,
            truncationAnnotation = truncated.second,
            originalSize = originalSize,
            finalSize = finalSize
        )
    }

    private fun applyProgressiveTruncation(
        context: CuratedContext,
        maxChars: Int
    ): Pair<CuratedContext, String> {
        val steps = mutableListOf<String>()
        var current = context

        // Step 1: Remove deeper ticket details
        current = truncateDeepTickets(current)
        if (estimateSize(current) <= maxChars) {
            steps.add("Removed deep ticket details (depth≥3)")
            return current to buildAnnotation(steps, context, current)
        }
        steps.add("Removed deep ticket details (depth≥3)")

        // Step 2: Reduce AS-IS to summaries only
        current = truncateAsIsToSummaries(current)
        if (estimateSize(current) <= maxChars) {
            steps.add("Reduced AS-IS section to summaries")
            return current to buildAnnotation(steps, context, current)
        }
        steps.add("Reduced AS-IS section to summaries")

        // Step 3: Reduce attachment previews
        current = truncateAttachments(current)
        if (estimateSize(current) <= maxChars) {
            steps.add("Truncated attachment previews")
            return current to buildAnnotation(steps, context, current)
        }
        steps.add("Truncated attachment previews")

        // Step 4: Reduce comments to decisions only
        current = truncateCommentsToDecisions(current)
        steps.add("Reduced comments to decisions only")

        return current to buildAnnotation(steps, context, current)
    }

    private fun truncateDeepTickets(ctx: CuratedContext): CuratedContext {
        val filtered = ctx.referenceOnlyTickets.take(5)
        return ctx.copy(referenceOnlyTickets = filtered)
    }

    private fun truncateAsIsToSummaries(ctx: CuratedContext): CuratedContext {
        val reduced = ctx.asIsSection.existingFunctionality.map { ticket ->
            ticket.copy(
                asIsState = ticket.asIsState.take(100),
                toBeState = "",
                extractedRequirements = emptyList()
            )
        }
        return ctx.copy(asIsSection = AsIsSection(reduced))
    }

    private fun truncateAttachments(ctx: CuratedContext): CuratedContext {
        val reduced = ctx.attachments.map { att ->
            att.copy(preview = att.preview.take(500))
        }
        return ctx.copy(attachments = reduced)
    }

    private fun truncateCommentsToDecisions(ctx: CuratedContext): CuratedContext {
        val reduced = ctx.commentSummaries.mapValues { (_, summary) ->
            summary.copy(
                clarifications = emptyList(),
                blockers = emptyList(),
                recentComments = emptyList()
            )
        }
        return ctx.copy(commentSummaries = reduced)
    }

    private fun buildAnnotation(
        steps: List<String>,
        original: CuratedContext,
        current: CuratedContext
    ): String {
        val origSize = estimateSize(original)
        val finalSize = estimateSize(current)
        return "[Truncation applied: ${steps.joinToString("; ")}. " +
            "Original: ${origSize} chars → Final: ${finalSize} chars]"
    }

    private fun estimateSize(ctx: CuratedContext): Int {
        var size = 0
        size += ctx.rootTicket.toString().length
        size += estimateToBeSize(ctx)
        size += estimateAsIsSize(ctx)
        size += ctx.outdatedMetadata.sumOf { it.toString().length }
        size += ctx.commentSummaries.values.sumOf { it.toString().length }
        size += ctx.attachments.sumOf { it.preview.length + 50 }
        size += ctx.referenceOnlyTickets.sumOf { it.toString().length }
        return size
    }

    private fun estimateToBeSize(ctx: CuratedContext): Int {
        val root = ctx.toBeSection.rootRequirements.sumOf { it.length }
        val linked = ctx.toBeSection.linkedRequirements.sumOf {
            estimateTicketDataSize(it)
        }
        return root + linked
    }

    private fun estimateAsIsSize(ctx: CuratedContext): Int =
        ctx.asIsSection.existingFunctionality.sumOf {
            estimateTicketDataSize(it)
        }

    private fun estimateTicketDataSize(data: ClassifiedTicketData): Int =
        data.businessSummary.length +
            data.asIsState.length +
            data.toBeState.length +
            data.extractedRequirements.sumOf { it.length } +
            (data.annotation?.length ?: 0) + 50
}
