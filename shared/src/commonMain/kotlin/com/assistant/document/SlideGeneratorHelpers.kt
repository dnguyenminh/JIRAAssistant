package com.assistant.document

import com.assistant.document.models.DocumentSection

/**
 * Helper functions for SlideGenerator.
 * Builds individual slides from BRD sections and extracts bullet points.
 *
 * Requirements: 11.1, 11.2, 11.3
 */

private const val SLIDE_SEPARATOR = "\n\n---\n\n"
private const val MAX_BULLETS = 7
private const val WARNING_MARKER = "⚠️"

/**
 * Build all 7 slides from parsed BRD section map.
 */
internal fun buildSlides(sectionMap: Map<String, DocumentSection>): String {
    val slides = listOf(
        buildVisionSlide(sectionMap),
        buildRequirementsSlide(sectionMap),
        buildDataFlowSlide(sectionMap),
        buildScopeSlide(sectionMap),
        buildStakeholdersSlide(sectionMap),
        buildRiskSlide(sectionMap),
        buildTimelineSlide(sectionMap)
    )
    return slides.joinToString(SLIDE_SEPARATOR)
}

/**
 * Get section content with fallback to alternative sections.
 * If primary section is empty/warning, tries fallback sections.
 */
private fun getContentWithFallback(
    sections: Map<String, DocumentSection>,
    primary: String,
    vararg fallbacks: String
): String {
    val primaryContent = sections[primary]?.content.orEmpty()
    if (primaryContent.isNotBlank() && !primaryContent.contains(WARNING_MARKER)) {
        return primaryContent
    }
    for (fb in fallbacks) {
        val fbContent = sections[fb]?.content.orEmpty()
        if (fbContent.isNotBlank() && !fbContent.contains(WARNING_MARKER)) {
            return fbContent
        }
    }
    return primaryContent
}

/**
 * Slide 1: Vision/Overview — business problem, solution, value.
 * Combines Project Overview + businessSummary context.
 */
internal fun buildVisionSlide(sections: Map<String, DocumentSection>): String {
    val content = getContentWithFallback(
        sections, "Project Overview", "Existing Processes", "Project Requirements"
    )
    val bullets = extractVisionBullets(content)
    return formatSlide("Vision/Overview", bullets)
}

/**
 * Slide 2: Requirements Overview — top 5-7 requirements with priority.
 * Extracts PREQ-NNN items from Project Requirements section.
 */
internal fun buildRequirementsSlide(sections: Map<String, DocumentSection>): String {
    val content = getContentWithFallback(
        sections, "Project Requirements", "Project Overview", "Existing Processes"
    )
    val bullets = extractRequirementBullets(content)
    return formatSlide("Requirements Overview", bullets)
}

/**
 * Slide 3: Data Flow from Existing Processes.
 */
internal fun buildDataFlowSlide(sections: Map<String, DocumentSection>): String {
    val content = getContentWithFallback(
        sections, "Existing Processes", "Project Requirements", "Project Overview"
    )
    val bullets = extractDataFlowBullets(content)
    return formatSlide("Data Flow", bullets)
}

/**
 * Slide 4: Scope — In-Scope vs Out-of-Scope as table-style bullets.
 * Extracts from Project Overview sub-sections.
 */
internal fun buildScopeSlide(sections: Map<String, DocumentSection>): String {
    val content = getContentWithFallback(
        sections, "Existing Processes", "Project Overview", "Project Requirements"
    )
    val bullets = extractScopeBullets(content)
    return formatSlide("Scope", bullets)
}

/**
 * Slide 5: Key Stakeholders from Sign Off section.
 */
internal fun buildStakeholdersSlide(sections: Map<String, DocumentSection>): String {
    val content = getContentWithFallback(
        sections, "Sign Off", "Project Overview", "Project Requirements"
    )
    val bullets = extractBullets(content)
    return formatSlide("Key Stakeholders", bullets)
}

/**
 * Slide 6: Risk Summary — from dependencies and assumptions.
 * Extracts blocking issues and risk indicators.
 */
internal fun buildRiskSlide(sections: Map<String, DocumentSection>): String {
    val content = getContentWithFallback(
        sections, "Project Requirements", "Existing Processes", "Project Overview"
    )
    val bullets = extractRiskBullets(content)
    return formatSlide("Risk Summary", bullets)
}

/**
 * Slide 7: Timeline & Milestones from Appendix or sprint metadata.
 */
internal fun buildTimelineSlide(sections: Map<String, DocumentSection>): String {
    val content = getContentWithFallback(
        sections, "Appendix", "Project Requirements", "Project Overview"
    )
    val bullets = extractBullets(content)
    return formatSlide("Timeline & Milestones", bullets)
}
