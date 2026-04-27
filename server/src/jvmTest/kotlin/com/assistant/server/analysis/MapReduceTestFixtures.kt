package com.assistant.server.analysis

import com.assistant.ai.AIAgent
import com.assistant.ai.AIContext
import com.assistant.ai.AIResult
import com.assistant.ai.AnalysisResult
import com.assistant.ai.AnalysisSource
import com.assistant.ai.ComplexityAssessment
import com.assistant.ai.ConnectionStatus
import com.assistant.ai.ProviderConfig
import com.assistant.ai.ProviderType
import com.assistant.ai.RequirementSummary
import com.assistant.ai.deepanalysis.DeepAnalysisResponseParser

/**
 * Test fixtures for MapReduce integration tests.
 * Contains mock AI agents, providers, and JSON response templates.
 */

/** Mock AI agent that returns a configurable response. */
class MockAIAgent(private val response: String) : AIAgent {
    var callCount = 0
    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        callCount++
        return AIResult.Success(response)
    }
    override fun getAgentName(): String = "MockAgent"
}

/** Mock AI agent that always fails. */
class FailingAIAgent : AIAgent {
    var callCount = 0
    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        callCount++
        return AIResult.Failure("Mock failure")
    }
    override fun getAgentName(): String = "FailingAgent"
}

/** Mock response parser that returns a fixed AnalysisResult. */
class MockResponseParser : DeepAnalysisResponseParser {
    override fun parse(ticketId: String, response: String): AnalysisResult {
        return AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(unified = "Synthesized: $ticketId"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 5.0, description = "mock"),
            source = AnalysisSource.FRESH_AI
        )
    }
}

/** Build a mock ProviderConfig for testing. */
fun testProvider(id: String = "mock-1") = ProviderConfig(
    providerId = id, name = "MockProvider",
    type = ProviderType.OLLAMA, endpoint = "http://localhost",
    priority = 0, status = ConnectionStatus.ACTIVE
)

/** Valid BatchSummary JSON template for map phase mock responses. */
fun batchSummaryJson(batchIndex: Int, ticketIds: List<String>) = """
{
  "batchIndex": $batchIndex,
  "ticketIds": [${ticketIds.joinToString(",") { "\"$it\"" }}],
  "requirementsSummary": "Requirements for batch $batchIndex",
  "technicalInsights": "Tech insights batch $batchIndex",
  "dependencySummary": "Dependencies batch $batchIndex",
  "keyFindings": ["Finding $batchIndex"],
  "openQuestions": []
}
""".trimIndent()

/** Valid AnalysisResult JSON for reduce phase mock responses. */
val VALID_REDUCE_JSON = """
{
  "requirementSummary": {"unified": "Full synthesis"},
  "evolution": [],
  "complexity": {"scrumPoints": 8.0, "description": "complex"}
}
""".trimIndent()
