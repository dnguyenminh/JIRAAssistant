package com.assistant.server.analysis

import com.assistant.ai.AIAgent
import com.assistant.ai.MapReduceAnalyzer
import com.assistant.ai.ProviderConfig
import com.assistant.ai.AnalysisResult
import com.assistant.ai.deepanalysis.models.StructuredTicketContent
import com.assistant.server.analysis.models.MapReduceConfig
import com.assistant.server.document.DeepJiraContentExtractor
import com.assistant.server.document.models.TicketGraph
import com.assistant.server.document.traversal.TicketFetcher
import com.assistant.server.document.traversal.TraversalEngine
import com.assistant.ai.deepanalysis.SectionClassifier
import com.assistant.jira.JiraClient
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory

/**
 * Adapter bridging [MapReduceAnalyzer] (shared module) to
 * [MapReduceOrchestrator] (server module).
 *
 * Re-traverses the ticket graph with analysis config to obtain
 * a [TicketGraph], then delegates to [MapReduceOrchestrator].
 *
 * Requirements: 6.1-6.6, 10.1-10.4
 */
class MapReduceAnalyzerAdapter(
    private val orchestrator: MapReduceOrchestrator,
    private val configProvider: () -> MapReduceConfig,
    private val jiraClientProvider: () -> JiraClient,
    private val sectionClassifier: SectionClassifier,
    private val jiraApiSemaphore: Semaphore
) : MapReduceAnalyzer {

    private val logger = LoggerFactory.getLogger(
        MapReduceAnalyzerAdapter::class.java
    )

    override val isEnabled: Boolean
        get() = configProvider().validated().mapReduceEnabled

    override val threshold: Int
        get() = configProvider().validated().mapReduceThreshold

    override suspend fun analyzeWithMapReduce(
        ticketId: String,
        content: StructuredTicketContent,
        agentProvider: () -> Map<String, AIAgent>,
        providerProvider: () -> List<ProviderConfig>
    ): AnalysisResult {
        val graph = traverseForMapReduce(ticketId)
        val result = orchestrator.analyze(
            ticketId, graph, agentProvider, providerProvider
        )
        return result.analysisResult
    }

    /** Re-traverse with analysis config to get full TicketGraph. */
    private suspend fun traverseForMapReduce(
        ticketId: String
    ): TicketGraph {
        val config = DeepJiraContentExtractor.analysisConfig()
        val client = jiraClientProvider()
        logger.info(
            "Map-reduce traversal for {}: maxDepth={}, maxTickets={}",
            ticketId, config.maxDepth, config.maxTickets
        )
        val fetcher = TicketFetcher(client, sectionClassifier)
        val engine = TraversalEngine(
            fetcher, config, jiraApiSemaphore
        )
        return engine.traverse(ticketId)
    }
}
