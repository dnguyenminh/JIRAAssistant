package com.assistant.ai

import kotlinx.serialization.json.*

/**
 * Parses batch AI response (JSON array) into individual AnalysisResults.
 * Handles markdown code fences, partial results, and malformed JSON.
 * Req: AC 40, AC 42
 */
object BatchResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse AI JSON array response into map of ticketId → AnalysisResult.
     * Returns null if response cannot be parsed as a valid JSON array.
     */
    fun parseBatchResponse(
        response: String,
        expectedTicketIds: List<String>
    ): Map<String, AnalysisResult>? {
        val cleaned = stripCodeFences(response)
        val array = tryParseJsonArray(cleaned) ?: return null
        return mapArrayToResults(array, expectedTicketIds)
    }

    /** Identify ticket IDs missing from a partial batch response. */
    fun findMissingTicketIds(
        results: Map<String, AnalysisResult>,
        expectedTicketIds: List<String>
    ): List<String> = expectedTicketIds.filter { it !in results }

    private fun stripCodeFences(raw: String): String {
        val trimmed = raw.trim()
        val startIdx = trimmed.indexOf("```")
        if (startIdx < 0) return trimmed
        val afterFence = trimmed.substring(startIdx + 3)
        val contentStart = afterFence.indexOf('\n')
        if (contentStart < 0) return afterFence.trim()
        val content = afterFence.substring(contentStart + 1)
        val endIdx = content.lastIndexOf("```")
        return if (endIdx >= 0) content.substring(0, endIdx).trim() else content.trim()
    }

    private fun tryParseJsonArray(text: String): JsonArray? {
        return try { json.parseToJsonElement(text).jsonArray } catch (_: Exception) { null }
    }

    private fun mapArrayToResults(
        array: JsonArray,
        expectedIds: List<String>
    ): Map<String, AnalysisResult> {
        val results = mutableMapOf<String, AnalysisResult>()
        for (element in array) {
            val obj = element.jsonObject
            val ticketId = obj["ticketId"]?.jsonPrimitive?.content ?: continue
            val parsed = parseOneResult(ticketId, obj) ?: continue
            results[ticketId] = parsed
        }
        return results
    }

    private fun parseOneResult(ticketId: String, obj: JsonObject): AnalysisResult? {
        return try {
            val reqObj = obj["requirementSummary"]?.jsonObject
            val unified = reqObj?.get("unified")?.jsonPrimitive?.content ?: ""
            val modules = parseModules(reqObj)
            val evolution = parseEvolution(obj)
            val complexity = parseComplexity(obj)
            AnalysisResult(
                ticketId = ticketId,
                context = RequirementSummary(unified, modules),
                evolution = evolution,
                complexity = complexity,
                source = AnalysisSource.FRESH_AI
            )
        } catch (_: Exception) { null }
    }

    private fun parseModules(reqObj: JsonObject?): List<AffectedModule> {
        return reqObj?.get("affectedModules")?.jsonArray?.mapNotNull { mod ->
            val m = mod.jsonObject
            val name = m["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            AffectedModule(name, m["colorCategory"]?.jsonPrimitive?.content ?: "PRIMARY")
        } ?: emptyList()
    }

    private fun parseEvolution(obj: JsonObject): List<EvolutionEntry> {
        return obj["evolution"]?.jsonArray?.mapNotNull { e ->
            val o = e.jsonObject
            EvolutionEntry(
                version = o["version"]?.jsonPrimitive?.content ?: "",
                date = o["date"]?.jsonPrimitive?.content ?: "",
                description = o["description"]?.jsonPrimitive?.content ?: "",
                changeType = o["changeType"]?.jsonPrimitive?.content ?: "UPDATE"
            )
        } ?: emptyList()
    }

    private fun parseComplexity(obj: JsonObject): ComplexityAssessment {
        val c = obj["complexity"]?.jsonObject
        return ComplexityAssessment(
            scrumPoints = c?.get("scrumPoints")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            description = c?.get("description")?.jsonPrimitive?.content ?: "",
            kbReferences = c?.get("kbReferences")?.jsonArray?.mapNotNull { r ->
                val ro = r.jsonObject
                KBReference(
                    ticketId = ro["ticketId"]?.jsonPrimitive?.content ?: "",
                    similarityPercent = ro["similarityPercent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
            } ?: emptyList()
        )
    }
}
