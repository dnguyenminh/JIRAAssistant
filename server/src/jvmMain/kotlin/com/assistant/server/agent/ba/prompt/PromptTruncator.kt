package com.assistant.server.agent.ba.prompt

import org.slf4j.LoggerFactory

/**
 * Progressive truncation logic for Master Prompts.
 *
 * Truncation order:
 * 1. Reduce linked ticket details to summaries (first 100 chars)
 * 2. Truncate attachment previews to first 500 chars each
 * 3. Reduce comment summaries to first 200 chars each
 * NEVER truncate: root ticket data, role instruction, template structure
 *
 * Requirements: 5.5
 */
object PromptTruncator {

    private val logger = LoggerFactory.getLogger(
        PromptTruncator::class.java
    )

    fun truncate(
        prompt: String,
        maxChars: Int,
        protectedSections: Set<String>
    ): String {
        var result = prompt
        result = truncateLinkedTickets(result, protectedSections)
        if (result.length <= maxChars) return result

        result = truncateAttachments(result, protectedSections)
        if (result.length <= maxChars) return result

        result = truncateComments(result, protectedSections)
        if (result.length <= maxChars) return result

        return forceLimit(result, maxChars, protectedSections)
    }

    internal fun truncateLinkedTickets(
        prompt: String, protected: Set<String>
    ): String = truncateSlotLines(
        prompt, protected, "/linkedTickets]", 100
    )

    internal fun truncateAttachments(
        prompt: String, protected: Set<String>
    ): String = truncateSlotLines(
        prompt, protected, "/attachmentsData]", 500
    )

    internal fun truncateComments(
        prompt: String, protected: Set<String>
    ): String = truncateSlotLines(
        prompt, protected, "/comments]", 200
    )

    private fun truncateSlotLines(
        prompt: String,
        protected: Set<String>,
        marker: String,
        maxLineChars: Int
    ): String {
        val lines = prompt.lines()
        val result = lines.map { line ->
            truncateLine(line, protected, marker, maxLineChars)
        }
        return result.joinToString("\n")
    }

    private fun truncateLine(
        line: String,
        protected: Set<String>,
        marker: String,
        maxLineChars: Int
    ): String {
        if (!line.contains(marker)) return line
        if (isProtected(line, protected)) return line
        if (line.length <= maxLineChars) return line
        val markerIdx = line.indexOf("[Source:")
        if (markerIdx < 0) return line.take(maxLineChars)
        val attribution = line.substring(markerIdx)
        val dataLimit = maxLineChars - attribution.length - 4
        return if (dataLimit > 0) {
            line.take(dataLimit) + "... " + attribution
        } else {
            line.take(maxLineChars)
        }
    }

    private fun isProtected(
        line: String, protected: Set<String>
    ): Boolean = protected.any { line.contains(it) }

    /**
     * Last resort: hard-cut non-protected content to fit limit.
     */
    private fun forceLimit(
        prompt: String,
        maxChars: Int,
        protected: Set<String>
    ): String {
        logger.warn(
            "Force-limiting prompt from {} to {} chars",
            prompt.length, maxChars
        )
        val lines = prompt.lines()
        val sb = StringBuilder()
        for (line in lines) {
            if (sb.length + line.length + 1 > maxChars) {
                if (isProtected(line, protected)) {
                    sb.appendLine(line)
                }
                continue
            }
            sb.appendLine(line)
        }
        return sb.toString().take(maxChars)
    }
}
