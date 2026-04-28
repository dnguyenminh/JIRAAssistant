package com.assistant.server.agent.ba.subprocess

import com.assistant.document.BrdPromptBuilder

/**
 * Basic structural quality checks for generated documents.
 * Returns feedback messages for sections that need improvement.
 *
 * Requirements: 1.1 (multi-turn reasoning)
 */
object DocumentQualityChecker {

    data class QualityResult(
        val passed: Boolean,
        val feedback: String,
        val missingSections: List<String>,
        val weakSections: List<String>
    )

    private const val MIN_SECTION_CHARS = 100
    private const val INSUFFICIENT_MARKER = "Insufficient data"

    fun check(document: String, docType: String): QualityResult {
        val expected = getExpectedSections(docType)
        val missing = findMissingSections(document, expected)
        val weak = findWeakSections(document, expected)
        val hasInsufficient = document.contains(INSUFFICIENT_MARKER)
        val passed = missing.isEmpty() && weak.isEmpty() && !hasInsufficient
        return QualityResult(passed, buildFeedback(missing, weak, hasInsufficient), missing, weak)
    }

    private fun getExpectedSections(docType: String): List<String> = when (docType) {
        "BRD" -> BrdPromptBuilder.BRD_SECTIONS
        "FSD" -> FSD_SECTIONS_COPY
        else -> emptyList()
    }

    /** Copy of FsdPromptBuilder.FSD_SECTIONS (internal in shared module). */
    private val FSD_SECTIONS_COPY = listOf(
        "Introduction", "System/Solution Overview", "Functional Specifications",
        "System Configurations", "Non-Functional Requirements",
        "Reporting Requirements", "Integration Requirements",
        "Data Migration/Conversion Requirements", "References",
        "Open Issues", "Appendix"
    )

    private fun findMissingSections(doc: String, sections: List<String>): List<String> {
        val lower = doc.lowercase()
        return sections.filter { !lower.contains(it.lowercase()) }
    }

    private fun findWeakSections(doc: String, sections: List<String>): List<String> {
        val weak = mutableListOf<String>()
        for (section in sections) {
            val idx = doc.lowercase().indexOf(section.lowercase())
            if (idx < 0) continue
            val after = doc.substring(idx + section.length)
            val nextHeading = after.indexOf("\n## ")
            val content = if (nextHeading > 0) after.substring(0, nextHeading) else after
            if (content.trim().length < MIN_SECTION_CHARS) weak.add(section)
        }
        return weak
    }

    private fun buildFeedback(
        missing: List<String>, weak: List<String>, hasInsufficient: Boolean
    ): String = buildString {
        appendLine("Please review and improve your document:")
        if (missing.isNotEmpty()) {
            appendLine("MISSING SECTIONS (must be added): ${missing.joinToString(", ")}")
        }
        if (weak.isNotEmpty()) {
            appendLine("WEAK SECTIONS (need more detail): ${weak.joinToString(", ")}")
            appendLine("Each section should have substantial content with specific details.")
        }
        if (hasInsufficient) {
            appendLine("REMOVE all 'Insufficient data' markers. Use available data or fetch more via tool calls.")
        }
        appendLine("Fetch additional data if needed, then produce the complete revised document.")
    }.trimEnd()
}
