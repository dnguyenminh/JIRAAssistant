package com.assistant.server.document.curation

/**
 * Configuration constants for the Prompt Curation Pipeline.
 *
 * All budget thresholds, timeouts, and limits are centralized here
 * for easy tuning and testing.
 *
 * Requirements: 6.1, 6.2, 7.1, 7.2, 7.5
 */
object CurationConfig {
    /** Maximum assembled prompt size in characters. */
    const val MAX_PROMPT_CHARS = 120_000

    /** Target minimum prompt size for optimal quality. */
    const val TARGET_MIN_CHARS = 80_000

    /** Max chars for summarized comments per ticket. */
    const val MAX_COMMENT_CHARS_PER_TICKET = 3_000

    /** Max chars for a single attachment preview. */
    const val MAX_ATTACHMENT_PREVIEW_CHARS = 5_000

    /** Max chars for requirement doc attachment preview. */
    const val MAX_REQUIREMENT_DOC_PREVIEW_CHARS = 8_000

    /** Total budget for all attachment previews combined. */
    const val MAX_TOTAL_ATTACHMENT_CHARS = 30_000

    /** Max MCP KB lookup calls per generation. */
    const val MAX_MCP_LOOKUPS = 20

    /** Summarize comments only when count exceeds this. */
    const val COMMENT_THRESHOLD = 10

    /** Gemini CLI timeout per request (ms). */
    const val GEMINI_TIMEOUT_MS = 240_000L

    /** Max retries for AI calls (2 total attempts). */
    const val MAX_RETRIES = 1

    /** Fail-fast threshold for total job time (ms). */
    const val JOB_FAIL_FAST_MS = 280_000L
}
