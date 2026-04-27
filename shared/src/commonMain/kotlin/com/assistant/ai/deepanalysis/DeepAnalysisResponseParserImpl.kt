package com.assistant.ai.deepanalysis

import com.assistant.ai.AnalysisResult
import kotlinx.serialization.json.Json

/**
 * Implementation of DeepAnalysisResponseParser.
 *
 * Pipeline:
 * 1. Strip markdown code fences (```json ... ```)
 * 2. Parse JSON with ignoreUnknownKeys into intermediate AIResponseRoot
 * 3. Map AIResponseRoot → AnalysisResult with defaults for missing fields
 * 4. Validate Scrum Points (round to nearest valid value)
 * 5. Compute extraction_confidence from section count
 *
 * Throws DeepAnalysisParseException on invalid JSON — caller retries.
 *
 * Requirements: 25.1-25.6
 */
class DeepAnalysisResponseParserImpl : DeepAnalysisResponseParser {

    companion object {
        internal val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    override fun parse(ticketId: String, response: String): AnalysisResult {
        val cleaned = ResponseParserHelpers.stripMarkdownFences(response)
        val root = deserializeResponse(cleaned)
        return ResponseToResultMapper.map(ticketId, root)
    }

    private fun deserializeResponse(jsonStr: String): AIResponseRoot {
        return try {
            json.decodeFromString<AIResponseRoot>(jsonStr)
        } catch (e: Exception) {
            throw DeepAnalysisParseException(
                "Failed to parse AI response as JSON: ${e.message}", e
            )
        }
    }
}
