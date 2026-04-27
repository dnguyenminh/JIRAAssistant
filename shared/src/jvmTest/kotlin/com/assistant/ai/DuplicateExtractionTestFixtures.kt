package com.assistant.ai

import com.assistant.ai.deepanalysis.JiraContentExtractor
import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Test fixtures for duplicate deep extraction bugfix tests.
 * Provides spy/fake implementations for verifying extraction call counts.
 */

/** Spy JiraContentExtractor — counts extract() calls. */
class SpyJiraContentExtractor(
    private val linkedCount: Int = 0,
    private val shouldThrow: Boolean = false
) : JiraContentExtractor {
    var extractCallCount = 0
    var lastTicketId: String? = null

    override suspend fun extract(ticketId: String): StructuredTicketContent {
        extractCallCount++
        lastTicketId = ticketId
        if (shouldThrow) throw RuntimeException("Simulated extraction failure")
        val links = (1..linkedCount).map { i ->
            com.assistant.ai.deepanalysis.models.LinkedTicketContent(
                ticketId = "$ticketId-LINK-$i"
            )
        }
        return StructuredTicketContent(
            summary = "Test ticket $ticketId",
            linkedTicketContents = links
        )
    }
}

/** Fake MapReduceAnalyzer — configurable enabled/threshold. */
class FakeMapReduceAnalyzer(
    override val isEnabled: Boolean = true,
    override val threshold: Int = 100
) : MapReduceAnalyzer {
    var analyzeWithMapReduceCalled = false
    var shouldThrow = false

    override suspend fun analyzeWithMapReduce(
        ticketId: String,
        content: StructuredTicketContent,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): AnalysisResult {
        analyzeWithMapReduceCalled = true
        if (shouldThrow) throw RuntimeException("Map-reduce failed")
        return AnalysisResult(
            ticketId = ticketId,
            context = RequirementSummary(unified = "MapReduce: $ticketId"),
            evolution = emptyList(),
            complexity = ComplexityAssessment(scrumPoints = 8.0, description = "map-reduce"),
            source = AnalysisSource.FRESH_AI
        )
    }
}
