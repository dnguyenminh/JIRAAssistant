package com.assistant.server.analysis

import com.assistant.server.analysis.models.BatchSummary
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Parses [BatchSummary] from raw AI response strings.
 *
 * Handles markdown fence stripping, unknown field tolerance,
 * and default values for missing fields. Validates ticketIds
 * match expected batch tickets (logs warning on mismatch).
 *
 * Requirements: 8.1, 8.2, 8.3, 8.5
 */
internal object BatchSummaryParser {

    private val logger = LoggerFactory.getLogger(BatchSummaryParser::class.java)

    private val batchJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse a raw AI response into a [BatchSummary].
     *
     * @param response Raw AI response (may contain markdown fences)
     * @return Parsed BatchSummary
     * @throws Exception if JSON is fundamentally invalid
     */
    fun parse(response: String): BatchSummary {
        val cleaned = stripMarkdownFences(response)
        return batchJson.decodeFromString<BatchSummary>(cleaned)
    }

    /**
     * Parse and validate ticketIds match expected batch tickets.
     * Logs warning on mismatch but still accepts the response.
     */
    fun parseAndValidate(
        response: String,
        batchIndex: Int,
        expectedTicketIds: List<String>
    ): BatchSummary {
        val summary = parse(response)
        validateTicketIds(summary, batchIndex, expectedTicketIds)
        return summary
    }

    private fun validateTicketIds(
        summary: BatchSummary,
        batchIndex: Int,
        expectedIds: List<String>
    ) {
        val actual = summary.ticketIds.toSet()
        val expected = expectedIds.toSet()
        if (actual != expected) {
            logger.warn(
                "Batch {} ticketId mismatch: expected={}, actual={}",
                batchIndex, expected, actual
            )
        }
    }

    /** Strip markdown code fences (```json ... ``` or ``` ... ```). */
    private fun stripMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        val startIdx = trimmed.indexOf("```")
        if (startIdx < 0) return trimmed
        val afterFence = trimmed.substring(startIdx + 3)
        val contentStart = afterFence.indexOf('\n')
        if (contentStart < 0) return afterFence.trim()
        val content = afterFence.substring(contentStart + 1)
        val endIdx = content.lastIndexOf("```")
        return if (endIdx >= 0) content.substring(0, endIdx).trim()
        else content.trim()
    }
}
