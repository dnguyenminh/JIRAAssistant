package com.assistant.domain

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import com.assistant.ai.JiraTicketSummary
import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import kotlin.math.abs

/**
 * Estimation engine that uses AI to provide Scrum point suggestions.
 * Follows PM Skill guidance and solid business logic.
 */
class ScrumEstimator(
    private val aiAgent: AIAgent
) {
    private val allowedPoints = listOf(0.0, 0.5, 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 21.0, 40.0)

    /**
     * Estimate a new requirement by comparing its DNA against historical tickets.
     */
    suspend fun estimate(
        newRequirement: NewRequirement,
        history: List<SimilarTicket>
    ): ScrumEstimation {
        val context = AIContext(
            tickets = history.map { 
                JiraTicketSummary(it.ticketKey, it.summary, "", "Done", it.actualPoints.toString()) 
            }
        )

        val prompt = """
            You are an expert Agile Software Estimator.
            Task: Estimate the story points for the following new requirement based on historical data.
            Allowed Points: ${allowedPoints.joinToString(", ")}
            
            New Requirement:
            Summary: ${newRequirement.summary}
            Description: ${newRequirement.description}
            
            Historical Data (Summary and Actual Points):
            ${history.joinToString("\n") { "[${it.ticketKey}] ${it.summary}: ${it.actualPoints} pts" }}
            
            Return the result in JSON format:
            {
                "points": 5.0,
                "confidence": 0.85,
                "rationale": "Why this points were chosen",
                "similar_keys": ["KEY-123", "KEY-456"]
            }
        """.trimIndent()

        val aiResult = aiAgent.analyze(prompt, context)
        
        return if (aiResult is AIResult.Success) {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val parsed = json.parseToJsonElement(aiResult.response).jsonObject
                
                val points = parsed["points"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val confidence = parsed["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.5
                val rationale = parsed["rationale"]?.jsonPrimitive?.content ?: "AI estimation completed."
                val similarKeys = parsed["similar_keys"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                ScrumEstimation(
                    suggestedPoints = findClosestAllowedPoint(points),
                    confidenceScore = confidence,
                    rationale = rationale,
                    similarHistoricalTickets = history.filter { it.ticketKey in similarKeys }
                )
            } catch (e: Exception) {
                ScrumEstimation(0.0, 0.0, "Estimation parse error: ${e.message}")
            }
        } else {
            ScrumEstimation(0.0, 0.0, "AI Agent failure.")
        }
    }

    internal fun findClosestAllowedPoint(points: Double): Double {
        // Handle special double values
        if (points.isNaN() || points == Double.NEGATIVE_INFINITY || points < allowedPoints.first()) {
            return allowedPoints.first() // 0.0
        }
        if (points == Double.POSITIVE_INFINITY || points > allowedPoints.last()) {
            return allowedPoints.last() // 40.0
        }
        return allowedPoints.minBy { abs(it - points) }
    }
}
