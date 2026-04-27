package com.assistant.ai

import com.assistant.ai.deepanalysis.DeepAnalysisParseException
import com.assistant.ai.deepanalysis.DeepAnalysisPromptBuilder
import com.assistant.ai.deepanalysis.DeepAnalysisResponseParser
import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.domain.NetworkGraph
import com.assistant.kb.KBRecord
import com.assistant.kb.KBRepository

/**
 * Test fakes for AIOrchestratorImpl unit tests.
 * Validates: Requirements 21.1, 21.2, 21.6
 */

/** Fake KBRepository — stores records in memory. */
class FakeKBRepository : KBRepository {
    val records = mutableMapOf<String, KBRecord>()
    var saveCalled = false
    var overwriteCalled = false

    override suspend fun findByTicketId(ticketId: String) = records[ticketId]
    override suspend fun save(record: KBRecord): Boolean {
        saveCalled = true; records[record.ticketId] = record; return true
    }
    override suspend fun overwrite(record: KBRecord): Boolean {
        overwriteCalled = true; records[record.ticketId] = record; return true
    }
    override suspend fun saveGraphData(projectKey: String, graph: NetworkGraph) = true
    override suspend fun getGraphData(projectKey: String): NetworkGraph? = null
}

/** Fake AIAgent — returns configurable responses. */
class FakeAIAgent(
    private val name: String = "fake-agent",
    var response: AIResult = AIResult.Success(VALID_AI_JSON)
) : AIAgent {
    var callCount = 0
    var lastPrompt: String? = null

    override suspend fun analyze(prompt: String, context: AIContext?): AIResult {
        callCount++; lastPrompt = prompt; return response
    }
    override fun getAgentName() = name
}

/** Fake JiraContentExtractor — returns a fixed StructuredTicketContent. */
class FakeJiraContentExtractor : JiraContentExtractor {
    var extractCalled = false
    var lastTicketId: String? = null

    override suspend fun extract(ticketId: String): StructuredTicketContent {
        extractCalled = true; lastTicketId = ticketId
        return StructuredTicketContent(summary = "Test ticket $ticketId")
    }
}

/** Fake DeepAnalysisPromptBuilder — records calls, returns fixed prompt. */
class FakeDeepPromptBuilder : DeepAnalysisPromptBuilder {
    var buildCalled = false
    override fun buildPrompt(content: StructuredTicketContent): String {
        buildCalled = true
        return "deep-prompt: ${content.summary}"
    }
}

/** Fake DeepAnalysisResponseParser — parses or throws based on config. */
class FakeDeepResponseParser : DeepAnalysisResponseParser {
    var parseCalled = false
    var failCount = 0 // number of times to throw before succeeding

    override fun parse(ticketId: String, response: String): AnalysisResult {
        parseCalled = true
        if (failCount > 0) {
            failCount--
            throw DeepAnalysisParseException("Simulated parse failure")
        }
        return AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(unified = "Deep: $response"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 5.0, description = "deep"),
            source = AnalysisSource.FRESH_AI
        )
    }
}

/** Minimal valid AI JSON for legacy parser. */
internal val VALID_AI_JSON = """
{
  "requirementSummary": {"unified": "Legacy summary"},
  "evolution": [],
  "complexity": {"scrumPoints": 3.0, "description": "legacy"}
}
""".trimIndent()

/** Helper to build a cached KBRecord. */
internal fun cachedKBRecord(ticketId: String) = KBRecord(
    ticketId = ticketId,
    requirementSummary = "Cached summary",
    evolutionHistory = emptyList(),
    scrumPoints = 8.0,
    confidenceScore = 0.9,
    rationale = "cached",
    similarTicketRefs = emptyList(),
    timestamp = "1000"
)

/** Helper to build an active ProviderConfig. */
internal fun activeProvider(id: String, priority: Int = 0) = ProviderConfig(
    providerId = id, name = "Provider-$id",
    type = ProviderType.OLLAMA, endpoint = "http://localhost",
    priority = priority, status = ConnectionStatus.ACTIVE
)
