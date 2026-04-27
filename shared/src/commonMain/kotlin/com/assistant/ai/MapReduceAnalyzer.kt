package com.assistant.ai

import com.assistant.ai.deepanalysis.models.StructuredTicketContent

/**
 * Abstraction for Map-Reduce analysis delegation.
 *
 * Lives in the shared module so [AIOrchestratorImpl] can depend on it.
 * The server module provides the concrete implementation that wraps
 * [MapReduceOrchestrator] and [TicketGraph] construction.
 *
 * Requirements: 6.1-6.6, 10.1-10.4
 */
interface MapReduceAnalyzer {

    /** Whether map-reduce pipeline is enabled via config. */
    val isEnabled: Boolean

    /** Ticket count threshold to activate map-reduce. */
    val threshold: Int

    /**
     * Run Map-Reduce analysis on a ticket with structured content.
     *
     * @param ticketId Root ticket ID
     * @param content Extracted structured content with linked tickets
     * @param agentProvider Provides AI agents from AIOrchestratorImpl
     * @param providerProvider Provides active providers by priority
     * @return AnalysisResult with mapReduceInfo in metadata
     */
    suspend fun analyzeWithMapReduce(
        ticketId: String,
        content: StructuredTicketContent,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): AnalysisResult
}
