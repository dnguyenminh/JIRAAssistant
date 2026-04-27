package com.assistant.server.document.prompt

import com.assistant.server.document.models.EnrichedContext

/**
 * Core assembly logic: builds section map and applies truncation.
 *
 * Separated from [PromptAssembler] to keep each file under 200 lines.
 *
 * Requirements: 6.2, 6.3, 6.8
 */

/** Result of section assembly with truncation tracking. */
internal data class AssemblyResult(
    val text: String,
    val removedTickets: Int = 0,
    val removedChunks: Int = 0
)

/** Build all section texts keyed by [PromptSectionType]. */
internal fun buildSectionMap(
    context: EnrichedContext
): Map<PromptSectionType, String> = mapOf(
    PromptSectionType.ROOT_RAW to PromptSectionBuilder.buildRootRaw(context),
    PromptSectionType.ROOT_KB to PromptSectionBuilder.buildRootKb(context),
    PromptSectionType.ROOT_ATTACHMENTS to PromptSectionBuilder.buildRootAttachments(context),
    PromptSectionType.DEPTH1_RAW to PromptSectionBuilder.buildTicketsRaw(context, 1),
    PromptSectionType.DEPTH1_ATTACHMENTS to PromptSectionBuilder.buildDepth1Attachments(context),
    PromptSectionType.DEEPER_TICKETS to PromptSectionBuilder.buildDeeperTickets(context, 2),
    PromptSectionType.DEEPER_ATTACHMENTS to PromptSectionBuilder.buildDeeperAttachments(context)
)

/**
 * Assemble sections in priority order, truncating from lowest priority
 * when budget is exceeded. Adds truncation annotation when content is cut.
 *
 * Req 6.2: respect maxPromptChars budget.
 * Req 6.3: cut from lowest priority first.
 * Req 6.8: no truncation when content fits.
 */
internal fun assembleSections(
    sections: Map<PromptSectionType, String>,
    priorities: List<PromptSectionType>,
    budget: Int
): AssemblyResult {
    val included = mutableListOf<String>()
    var currentSize = 0
    var removedTickets = 0
    var removedChunks = 0
    var keptFullTickets = 0
    var keptSummaryTickets = 0
    var originalSize = 0
    val contextHeader = "=== CONTEXT ===\n"
    currentSize += contextHeader.length
    var truncationStarted = false

    for (type in priorities) {
        val sectionText = sections[type] ?: continue
        if (sectionText.isBlank()) continue
        originalSize += sectionText.length

        if (truncationStarted) {
            removedTickets += countTickets(sectionText)
            removedChunks += countChunks(sectionText)
            continue
        }

        if (currentSize + sectionText.length <= budget) {
            included.add(sectionText)
            currentSize += sectionText.length
            keptFullTickets += countTickets(sectionText)
        } else {
            truncationStarted = true
            removedTickets += countTickets(sectionText)
            removedChunks += countChunks(sectionText)
            val partial = addPartialContent(included, sectionText, budget - currentSize)
            if (partial) keptSummaryTickets += countTickets(sectionText)
        }
    }

    val annotation = buildAnnotation(
        removedTickets, removedChunks,
        keptFullTickets, keptSummaryTickets,
        originalSize, budget
    )
    val text = buildString {
        append(contextHeader)
        included.forEach { append(it) }
        append(annotation)
    }
    return AssemblyResult(text, removedTickets, removedChunks)
}

/** Try to fit partial content from a section into remaining space. Returns true if partial added. */
private fun addPartialContent(
    included: MutableList<String>,
    sectionText: String,
    remaining: Int
): Boolean {
    if (remaining > 100) {
        val partial = sectionText.take(remaining - 4) + "...\n"
        included.add(partial)
        return true
    }
    return false
}

/** Build truncation annotation with detailed info if any content was removed (Req 5.3). */
private fun buildAnnotation(
    removedTickets: Int,
    removedChunks: Int,
    keptFullTickets: Int,
    keptSummaryTickets: Int,
    originalSize: Int,
    budget: Int
): String {
    if (removedTickets == 0 && removedChunks == 0) return ""
    return "\n${PromptSectionBuilder.truncationAnnotation(
        removedTickets, removedChunks,
        keptFullTickets, keptSummaryTickets,
        originalSize, budget
    )}\n"
}

/** Count ticket references in a section text. */
private fun countTickets(text: String): Int =
    Regex("Ticket: [A-Z][A-Z0-9]+-\\d+").findAll(text).count()
        .coerceAtLeast(if (text.contains("--- ") && text.contains("Tickets")) 1 else 0)

/** Count attachment chunk references in a section text. */
private fun countChunks(text: String): Int =
    Regex("\\[Attachment:").findAll(text).count()
