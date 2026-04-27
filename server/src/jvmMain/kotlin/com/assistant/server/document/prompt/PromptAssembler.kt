package com.assistant.server.document.prompt

import com.assistant.document.BrdPromptBuilder
import com.assistant.document.FsdPromptBuilder
import com.assistant.server.document.models.EnrichedContext

/**
 * Assembles prompts from [EnrichedContext] with priority-based truncation.
 *
 * Keeps role, template, and instructions from BrdPromptBuilder / FsdPromptBuilder
 * and replaces the context data section with enriched deep-collection data.
 *
 * Truncation strategy: when total content exceeds [maxPromptChars],
 * sections are removed from lowest priority first.
 *
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9
 */
object PromptAssembler {

    /**
     * Build prompt from [EnrichedContext] with priority-based truncation.
     *
     * @param context Enriched context from deep collection
     * @param maxPromptChars Maximum prompt size in characters
     * @param docType "BRD" or "FSD" — determines priority ordering
     * @return Assembled prompt string within size limit
     */
    fun buildPrompt(
        context: EnrichedContext,
        maxPromptChars: Int,
        docType: String
    ): String {
        val skeleton = buildSkeleton(context, docType)
        // INVARIANT (Req 5.4): Skeleton (role, template, mapping instructions, output format)
        // is NEVER truncated. Budget is calculated AFTER reserving space for skeleton,
        // so only the context block is subject to truncation.
        val budget = maxPromptChars - skeleton.headerSize - skeleton.footerSize
        val contextBlock = buildContextBlock(context, budget.coerceAtLeast(0), docType)
        return skeleton.header + contextBlock + skeleton.footer
    }

    /**
     * Build the fixed prompt skeleton (header + footer) using existing builders.
     * The skeleton is the full prompt minus the context data section.
     */
    private fun buildSkeleton(context: EnrichedContext, docType: String): PromptSkeleton {
        val fullPrompt = if (docType.equals("FSD", ignoreCase = true)) {
            FsdPromptBuilder.buildPrompt(context)
        } else {
            BrdPromptBuilder.buildPrompt(context)
        }
        return extractSkeleton(fullPrompt)
    }

    /** Build context block with priority-based truncation. */
    private fun buildContextBlock(
        context: EnrichedContext,
        budget: Int,
        docType: String
    ): String {
        val priorities = PromptPriorityConfig.priorityFor(docType)
        val sections = buildSectionMap(context)
        val graphMeta = PromptSectionBuilder.buildGraphMetadata(context)
        val metaSize = graphMeta.length
        val result = assembleSections(sections, priorities, budget - metaSize)
        return result.text + graphMeta
    }
}

/**
 * Prompt skeleton: header (role + template) and footer (instructions + output).
 * The context section between them is replaced by enriched data.
 */
internal data class PromptSkeleton(
    val header: String,
    val footer: String,
    val headerSize: Int = header.length,
    val footerSize: Int = footer.length
)

/** Extract header and footer from a full prompt by splitting on CONTEXT markers. */
internal fun extractSkeleton(fullPrompt: String): PromptSkeleton {
    val contextStart = fullPrompt.indexOf("=== CONTEXT ===")
    if (contextStart < 0) {
        return PromptSkeleton(header = fullPrompt, footer = "")
    }
    val header = fullPrompt.substring(0, contextStart)
    val afterContext = findNextSection(fullPrompt, contextStart)
    val footer = if (afterContext >= 0) fullPrompt.substring(afterContext) else ""
    return PromptSkeleton(header = header, footer = footer)
}

/** Find the start of the next === SECTION === after the context block. */
private fun findNextSection(text: String, fromIndex: Int): Int {
    val pattern = Regex("^=== [A-Z]", RegexOption.MULTILINE)
    val match = pattern.find(text, fromIndex + 15) // skip "=== CONTEXT ==="
    return match?.range?.first ?: -1
}
