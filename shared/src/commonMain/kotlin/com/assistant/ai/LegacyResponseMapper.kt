package com.assistant.ai

import kotlinx.serialization.json.*

/**
 * Legacy response parsing helpers — used when deep analysis
 * components are not injected. Extracted from old parseAIResponse().
 */
internal object LegacyResponseCleaner {
    /** Strip markdown code fences (```json ... ```) from AI response. */
    fun stripMarkdownFences(response: String): String {
        val raw = response.trim()
        val startIdx = raw.indexOf("```")
        if (startIdx < 0) return raw
        val afterFence = raw.substring(startIdx + 3)
        val contentStart = afterFence.indexOf('\n')
        if (contentStart < 0) return afterFence.trim()
        val content = afterFence.substring(contentStart + 1)
        val endIdx = content.lastIndexOf("```")
        return if (endIdx >= 0) content.substring(0, endIdx).trim() else content.trim()
    }
}

/** Legacy prompt builder — used when deep analysis components not available. */
internal object LegacyPromptBuilder {
    fun build(ticketId: String, ticketContent: String): String {
        val contentSection = if (ticketContent.isNotBlank()) {
            "\n--- TICKET CONTENT ---\n$ticketContent\n--- END CONTENT ---\n"
        } else ""
        return """
            Analyze Jira ticket $ticketId.$contentSection
            Return a JSON object with:
            {
              "requirementSummary": { "unified": "...", "affectedModules": [{"name": "...", "colorCategory": "PRIMARY|ACCENT|SECONDARY"}] },
              "evolution": [{"version": "...", "date": "...", "description": "...", "changeType": "ORIGIN|UPDATE|CURRENT"}],
              "complexity": { "scrumPoints": 5.0, "description": "...", "kbReferences": [{"ticketId": "...", "similarityPercent": 85.0}] }
            }
        """.trimIndent()
    }
}

internal object LegacyResponseMapper {
    private val json = Json { ignoreUnknownKeys = true }

    /** Parse raw AI response into AnalysisResult (legacy format). */
    fun parseResponse(ticketId: String, response: String): AnalysisResult? {
        return try {
            val cleaned = LegacyResponseCleaner.stripMarkdownFences(response)
            val root = json.parseToJsonElement(cleaned).jsonObject
            mapToResult(ticketId, root)
        } catch (_: Exception) {
            null
        }
    }

    /** Map parsed JSON to AnalysisResult (legacy format). */
    fun mapToResult(ticketId: String, root: JsonObject): AnalysisResult {
        return AnalysisResult(
            ticketId = ticketId,
            context = mapRequirementSummary(root),
            evolution = mapEvolution(root),
            complexity = mapComplexity(root),
            source = AnalysisSource.FRESH_AI
        )
    }

    private fun mapRequirementSummary(root: JsonObject): RequirementSummary {
        val obj = root["requirementSummary"]?.jsonObject
        val unified = obj?.get("unified")?.jsonPrimitive?.content ?: ""
        val modules = obj?.get("affectedModules")?.jsonArray?.map { mod ->
            val m = mod.jsonObject
            AffectedModule(
                name = m["name"]?.jsonPrimitive?.content ?: "",
                colorCategory = m["colorCategory"]?.jsonPrimitive?.content ?: "PRIMARY"
            )
        } ?: emptyList()
        return RequirementSummary(unified = unified, affectedModules = modules)
    }

    private fun mapEvolution(root: JsonObject): List<EvolutionEntry> {
        return root["evolution"]?.jsonArray?.map { entry ->
            val obj = entry.jsonObject
            EvolutionEntry(
                version = obj["version"]?.jsonPrimitive?.content ?: "",
                date = obj["date"]?.jsonPrimitive?.content ?: "",
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                changeType = obj["changeType"]?.jsonPrimitive?.content ?: "UPDATE"
            )
        } ?: emptyList()
    }

    private fun mapComplexity(root: JsonObject): ComplexityAssessment {
        val obj = root["complexity"]?.jsonObject
        val points = obj?.get("scrumPoints")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val desc = obj?.get("description")?.jsonPrimitive?.content ?: ""
        val refs = obj?.get("kbReferences")?.jsonArray?.map { ref ->
            val r = ref.jsonObject
            KBReference(
                ticketId = r["ticketId"]?.jsonPrimitive?.content ?: "",
                similarityPercent = r["similarityPercent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            )
        } ?: emptyList()
        return ComplexityAssessment(scrumPoints = points, description = desc, kbReferences = refs)
    }
}
