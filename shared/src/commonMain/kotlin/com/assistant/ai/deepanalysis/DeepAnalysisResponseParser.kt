package com.assistant.ai.deepanalysis

import com.assistant.ai.AnalysisResult

/**
 * Parses raw AI response strings into structured AnalysisResult.
 *
 * Replaces AIOrchestratorImpl.parseAIResponse() with deep analysis
 * parsing: strips markdown fences, parses JSON with ignoreUnknownKeys,
 * applies default values for missing optional fields, validates Scrum
 * Points, and computes extraction_confidence.
 *
 * Throws on invalid JSON — the caller (AIOrchestrator) handles retry.
 *
 * Requirements: 25.1-25.6
 */
interface DeepAnalysisResponseParser {

    /**
     * Parse a raw AI response string into an AnalysisResult.
     *
     * @param ticketId The ticket ID being analyzed
     * @param response Raw AI response (may contain markdown fences)
     * @return Parsed AnalysisResult with validated fields
     * @throws DeepAnalysisParseException if JSON is invalid
     */
    fun parse(ticketId: String, response: String): AnalysisResult
}

/**
 * Thrown when the AI response cannot be parsed as valid JSON.
 * The caller should retry with a strict JSON prompt (Req 25.3).
 */
class DeepAnalysisParseException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
