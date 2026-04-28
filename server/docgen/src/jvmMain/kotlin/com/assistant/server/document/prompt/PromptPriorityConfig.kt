package com.assistant.server.document.prompt

/**
 * Priority-based section ordering for prompt assembly.
 *
 * Each [PromptSectionType] represents a category of content that can be
 * included in the prompt. The ordering differs between BRD (business-first)
 * and FSD (technical-first) document types.
 *
 * Requirements: 6.1, 6.9
 */
enum class PromptSectionType {
    ROOT_RAW,
    ROOT_KB,
    ROOT_ATTACHMENTS,
    DEPTH1_RAW,
    DEPTH1_ATTACHMENTS,
    DEEPER_TICKETS,
    DEEPER_ATTACHMENTS
}

/**
 * Provides priority-ordered section lists for BRD and FSD document types.
 *
 * BRD priority (business-first): raw → KB → depth-1 → root attach → d1 attach → deeper
 * FSD priority (technical-first): raw → root attach → KB → depth-1 → d1 attach → deeper
 *
 * Requirements: 6.1, 6.9
 */
object PromptPriorityConfig {

    /** BRD: business requirements, comments, stakeholder discussions first. */
    val brdPriority: List<PromptSectionType> = listOf(
        PromptSectionType.ROOT_RAW,
        PromptSectionType.ROOT_KB,
        PromptSectionType.DEPTH1_RAW,
        PromptSectionType.ROOT_ATTACHMENTS,
        PromptSectionType.DEPTH1_ATTACHMENTS,
        PromptSectionType.DEEPER_TICKETS,
        PromptSectionType.DEEPER_ATTACHMENTS
    )

    /** FSD: technical details, API specs, design docs, wireframes first. */
    val fsdPriority: List<PromptSectionType> = listOf(
        PromptSectionType.ROOT_RAW,
        PromptSectionType.ROOT_ATTACHMENTS,
        PromptSectionType.ROOT_KB,
        PromptSectionType.DEPTH1_RAW,
        PromptSectionType.DEPTH1_ATTACHMENTS,
        PromptSectionType.DEEPER_TICKETS,
        PromptSectionType.DEEPER_ATTACHMENTS
    )

    /** Returns the priority list for the given document type. */
    fun priorityFor(docType: String): List<PromptSectionType> =
        if (docType.equals("FSD", ignoreCase = true)) fsdPriority else brdPriority
}
